/*#######################################################
 *
 * SPDX-FileCopyrightText: 2017-2025 Gregor Santner <gsantner AT mailbox DOT org>
 * SPDX-License-Identifier: Unlicense OR CC0-1.0
 *
 * Written 2017-2025 by Gregor Santner <gsantner AT mailbox DOT org>
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#########################################################*/
package net.pixelpen.journal.frontend;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.Editable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import net.pixelpen.journal.ApplicationObject;
import net.pixelpen.journal.R;
import net.pixelpen.journal.format.FormatRegistry;
import net.pixelpen.journal.format.markdown.MarkdownActionButtons;
import net.pixelpen.journal.format.wikitext.WikitextLinkResolver;
import net.pixelpen.journal.frontend.textview.TextViewUtils;
import net.pixelpen.journal.model.AppSettings;
import net.pixelpen.journal.util.AutumnContextUtils;
import net.gsantner.opoc.format.GsTextUtils;
import net.gsantner.opoc.util.GsFileUtils;
import net.gsantner.opoc.wrapper.GsCallback;

import java.io.File;



public class AttachLinkOrFileDialog {
    /**
     * The most a single attachment may be when it is copied into the archive. Autumn is for
     * writing: a receipt, a letter or a voice memo fits easily, and a video does not. Pictures
     * are exempt, because they are scaled down on the way in.
     */
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;

    public final static int IMAGE_ACTION = 2, FILE_OR_LINK_ACTION = 3, AUDIO_ACTION = 4;

    private static String getImageFormat(final int textFormatId) {
        if (textFormatId == FormatRegistry.FORMAT_MARKDOWN) {
            return "![%TITLE%](%LINK%)";
        } else if (textFormatId == FormatRegistry.FORMAT_WIKITEXT) {
            return "{{%LINK%}}";
        } else if (textFormatId == FormatRegistry.FORMAT_ASCIIDOC) {
            return "image::%LINK%[\"%TITLE%\"]";
        } else if (textFormatId == FormatRegistry.FORMAT_ORGMODE) {
            return "#+CAPTION: %TITLE%\n[[file:%LINK%]]";
        } else {
            return "<img style='width:auto;max-height:256px;' alt='%TITLE%' src='%LINK%' />";
        }
    }

    private static String getLinkFormat(final int textFormatId) {
        if (textFormatId == FormatRegistry.FORMAT_MARKDOWN) {
            return "[%TITLE%](%LINK%)";
        } else if (textFormatId == FormatRegistry.FORMAT_WIKITEXT) {
            return "[[%LINK%|%TITLE%]]";
        } else if (textFormatId == FormatRegistry.FORMAT_ASCIIDOC) {
            return "link:%LINK%[%TITLE%]";
        } else if (textFormatId == FormatRegistry.FORMAT_TODOTXT) {
            return "%TITLE% link:%LINK%";
        } else if (textFormatId == FormatRegistry.FORMAT_ORGMODE) {
            return "[[file:%LINK%][%TITLE%]]";
        } else {
            return "<a href=\"%LINK%\">%TITLE%</a>";
        }
    }

    private static String getAudioFormat(final int textFormatId) {
        if (textFormatId == FormatRegistry.FORMAT_WIKITEXT) {
            return "[[%LINK%|%TITLE%]]";
        } else {
            return "<audio src='%LINK%' controls><a href='%LINK%'>%TITLE%</a></audio>";
        }
    }

    public static void showInsertImageOrLinkDialog(
            final int action,
            final int textFormatId,
            final Activity activity,
            final Editable edit,
            final File currentFile
    ) {
        final int[] sel = TextViewUtils.getSelection(edit);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.Theme_AppCompat_DayNight_Dialog_Rounded);
        final View view = activity.getLayoutInflater().inflate(R.layout.select_path_dialog, null);
        final EditText inputPathName = view.findViewById(R.id.ui__select_path_dialog__name);
        final EditText inputPathUrl = view.findViewById(R.id.ui__select_path_dialog__url);
        final Button buttonBrowseFilesystem = view.findViewById(R.id.ui__select_path_dialog__browse_filesystem);
        final Button buttonSearch = view.findViewById(R.id.ui__select_path_dialog__search);
        final Button buttonPictureGallery = view.findViewById(R.id.ui__select_path_dialog__gallery_picture);
        final Button buttonPictureCamera = view.findViewById(R.id.ui__select_path_dialog__camera_picture);
        final Button buttonPictureEdit = view.findViewById(R.id.ui__select_path_dialog__edit_picture);
        final Button buttonAudioRecord = view.findViewById(R.id.ui__select_path_dialog__record_audio);

        builder.setCancelable(true);
        builder.setNegativeButton(android.R.string.cancel, (di, b) -> di.dismiss());

        // Extract filepath if using Markdown
        if (textFormatId == FormatRegistry.FORMAT_MARKDOWN) {
            if (sel[0] > 0 && sel[1] > 0 && sel[0] != sel[1]) {
                inputPathName.setText(edit.subSequence(sel[0], sel[1]));
            } else if (edit.length() == 0) {
                inputPathName.setText("");
            } else {
                final MarkdownActionButtons.Link link = MarkdownActionButtons.Link.extract(edit, sel[0]);
                final boolean isImage = action == IMAGE_ACTION && link.isImage;
                final boolean isLink = action == FILE_OR_LINK_ACTION && !link.isImage;
                if (link.isValid() && (isImage || isLink)) {
                    inputPathName.setText(link.title);
                    inputPathUrl.setText(link.link);
                    sel[0] = link.start;
                    sel[1] = link.end;
                }
            }
        }

        // Create dialog first as we need a ref to it
        final AlertDialog dialog = builder.setView(view).create();

        // Helper func
        final GsCallback.a1<InsertType> _insertItem = (type) -> fetchAndInsertItem(type, textFormatId, activity, edit, currentFile, sel, dialog);

        // Setup all the various choices
        final InsertType browseType, okType;
        if (action == IMAGE_ACTION) {
            // Pictures come from the camera or the gallery. The file browser here was Markor's,
            // showing its own view of the storage, and anything that is not a picture belongs
            // to Attach, not here.
            buttonBrowseFilesystem.setVisibility(View.GONE);
            buttonPictureCamera.setVisibility(View.VISIBLE);
            buttonPictureGallery.setVisibility(View.VISIBLE);
            buttonPictureEdit.setVisibility(View.VISIBLE);
            dialog.setTitle(R.string.insert_image);
            browseType = InsertType.IMAGE_BROWSE;
            okType = InsertType.IMAGE_DIALOG;
        } else if (action == AUDIO_ACTION) {
            dialog.setTitle(R.string.audio);
            buttonAudioRecord.setVisibility(View.VISIBLE);
            browseType = InsertType.AUDIO_BROWSE;
            okType = InsertType.AUDIO_DIALOG;
        } else {
            dialog.setTitle(R.string.insert_link);
            buttonSearch.setVisibility(View.VISIBLE);
            browseType = InsertType.LINK_BROWSE;
            okType = InsertType.LINK_DIALOG;
        }

        final String ok = activity.getString(android.R.string.ok);
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, ok, (di, b) -> _insertItem.callback(okType));
        buttonBrowseFilesystem.setOnClickListener(v -> _insertItem.callback(browseType));
        buttonSearch.setOnClickListener(v -> _insertItem.callback(InsertType.LINK_SEARCH));
        buttonPictureCamera.setOnClickListener(b -> _insertItem.callback(InsertType.IMAGE_CAMERA));
        buttonPictureGallery.setOnClickListener(v -> _insertItem.callback(InsertType.IMAGE_GALLERY));
        buttonAudioRecord.setOnClickListener(v -> _insertItem.callback(InsertType.AUDIO_RECORDING));
        buttonPictureEdit.setOnClickListener(v -> _insertItem.callback(InsertType.IMAGE_EDIT));

        dialog.show();
        final Window win = dialog.getWindow();
        if (win != null) {
            win.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            inputPathName.requestFocus();
        }
    }

    private enum InsertType {
        IMAGE_CAMERA,
        IMAGE_GALLERY,
        IMAGE_BROWSE,
        IMAGE_DIALOG,
        IMAGE_EDIT,
        AUDIO_RECORDING,
        AUDIO_BROWSE,
        AUDIO_DIALOG,
        LINK_BROWSE,
        LINK_DIALOG,
        LINK_SPECIAL,
        LINK_SEARCH
    }

    private static String getTemplateForAction(final InsertType action, final int textFormatId) {
        switch (action) {
            case IMAGE_CAMERA:
            case IMAGE_GALLERY:
            case IMAGE_EDIT:
            case IMAGE_DIALOG:
            case IMAGE_BROWSE: {
                return getImageFormat(textFormatId);
            }
            case AUDIO_RECORDING:
            case AUDIO_DIALOG:
            case AUDIO_BROWSE: {
                return getAudioFormat(textFormatId);
            }
            case LINK_DIALOG:
            case LINK_BROWSE:
            case LINK_SPECIAL:
            case LINK_SEARCH:
            default: {
                return getLinkFormat(textFormatId);
            }
        }
    }

    public static String formatLink(String title, String path, final int textFormatId) {
        return formatLink(title, path, textFormatId, InsertType.LINK_DIALOG);
    }

    private static String formatLink(String title, String path, final int textFormatId, final InsertType action) {
        title = title.trim().replace("|", "/");

        path = path.trim()
                // Workaround for parser - cannot deal with spaces and have other entities problems
                .replace(" ", "%20")
                // Disable space encoding for Jekyll
                .replace("{{%20site.baseurl%20}}", "{{ site.baseurl }}");

        String newText = getTemplateForAction(action, textFormatId)
                .replace("%TITLE%", title)
                .replace("%LINK%", path);

        if (textFormatId == FormatRegistry.FORMAT_WIKITEXT && newText.endsWith("|]]")) {
            newText = newText.replaceFirst("\\|]]$", "]]");
        }

        if (textFormatId == FormatRegistry.FORMAT_TODOTXT) {
            newText = newText.replaceAll("\\n", " ");
        }

        return newText;
    }

    private static File copyFileToAttachmentDir(final File attachment, final File attachmentDir) {
        attachmentDir.mkdirs();

        if (attachment.getName().matches("(?i).+\\.(jpg|jpeg|png|webp)")) {
            // Stored as JPEG, so named as one: a .png holding JPEG bytes is a file whose name lies.
            final File local = GsFileUtils.findNonConflictingDest(attachmentDir,
                    GsFileUtils.getFilenameWithoutExtension(attachment) + ".jpg");
            compressAndCopyImage(attachment, local);
            return local;
        }
        final File local = GsFileUtils.findNonConflictingDest(attachmentDir, attachment.getName());
        GsFileUtils.copyFile(attachment, local);

        return local;
    }

    private static void compressAndCopyImage(File source, File dest) {
        // The same scaling as every other way a picture enters the archive: 1024 on the long
        // side, turned upright, decoded with sampling so a 50-megapixel photo cannot run the
        // phone out of memory on the way.
        try {
            net.pixelpen.journal.model.ArchiveImages.writeScaled(source, dest);
        } catch (final Exception e) {
            GsFileUtils.copyFile(source, dest);  // fallback: keep the picture, full size
        }
    }


    private static String setupFileAttachment(
            final int textFormatId,
            final File attachment,
            final File document
    ) {
        final AppSettings as = ApplicationObject.settings();
        final File notebookDir = as.getNotebookDirectory();

        String path = "";
        if (textFormatId == FormatRegistry.FORMAT_WIKITEXT) {
            final boolean shouldDynamicallyDetermineRoot = as.isWikitextDynamicNotebookRootEnabled();
            path = WikitextLinkResolver.resolveSystemFilePath(attachment, notebookDir, document, shouldDynamicallyDetermineRoot);
            if (path.startsWith("/")) {
                final File attachmentDir = WikitextLinkResolver.findAttachmentDir(document);
                final File local = copyFileToAttachmentDir(attachment, attachmentDir);
                path = WikitextLinkResolver.resolveSystemFilePath(local, notebookDir, document, shouldDynamicallyDetermineRoot);
            }
        } else {
            if (!GsFileUtils.isChild(notebookDir, attachment)) {
                // One tree per kind under the home folder, instead of a _res beside every note.
                // Per-note folders multiply without limit and each one shows up in every gallery
                // app until it is hidden; this way one .nomedia covers all of them, and the
                // pictures can actually be browsed and pruned in one place.
                //
                // Only where new attachments land. Links already written into existing notes
                // point at their _res folders, those files are untouched, and they keep working.
                File attachmentDir;
                try {
                    attachmentDir = net.pixelpen.journal.model.ArchiveImages.attachmentFolder(
                            notebookDir, document,
                            net.pixelpen.journal.model.ArchiveImages.isImageName(attachment.getName()));
                } catch (final Exception e) {
                    // If the shared folder cannot be made, fall back to the old behaviour rather
                    // than losing the attachment.
                    // Last resort only, and no longer configurable: a setting naming this
                    // folder implied it decided where attachments go, which it has not since
                    // they moved to Images/<year> and Files/<year>.
                    final File parent = document.getParentFile();
                    attachmentDir = (parent == null) ? notebookDir : new File(parent, "_res");
                }
                final File local = copyFileToAttachmentDir(attachment, attachmentDir);
                // ArchiveImages.relativeLink, not GsFileUtils.relativePath.
                    //
                    // The note is reached one way and the attachment folder another -- one
                    // through /sdcard, the other through /storage/emulated/0. They are the same
                    // folder, but they compare as different strings, so relativePath finds no
                    // common prefix and falls back to an absolute path. The link was written,
                    // looked right, and pointed nowhere. relativeLink canonicalises all three
                    // first, which is why the Year entry screen has always produced live links
                    // while the editor's bottom bar did not.
                    path = net.pixelpen.journal.model.ArchiveImages.relativeLink(
                            document, local, notebookDir);
            } else {
                path = net.pixelpen.journal.model.ArchiveImages.relativeLink(
                        document, attachment, notebookDir);
            }
        }

        return path;
    }

    public static String makeAttachmentLink(
            final int textFormatId,
            final String title,
            final File attachment,
            final File document
    ) {
        final String path = setupFileAttachment(textFormatId, attachment, document);
        return formatLink(title, path, textFormatId);
    }

    private static void fetchAndInsertItem(
            final InsertType action,
            final int textFormatId,
            final Activity activity,
            final Editable edit,
            final File currentFile,
            @Nullable final int[] region,
            @Nullable AlertDialog dialog
    ) {
        final int[] sel;
        if (region != null && region.length > 1 && region[0] >= 0 && region[1] >= 0) {
            sel = region;
        } else {
            sel = TextViewUtils.getSelection(edit);
        }

        final AppSettings _appSettings = ApplicationObject.settings();

        // Title, path to be written when the user hits accept
        final GsCallback.a2<String, String> insertLink = (title, path) -> {
            if (GsTextUtils.isNullOrEmpty(path)) {
                return;
            }

            final String newText = formatLink(title, path, textFormatId, action);

            if (!newText.equals(edit.subSequence(sel[0], sel[1]).toString())) {
                edit.replace(sel[0], sel[1], newText);
            }

            if (dialog != null) {
                dialog.dismiss();
            }
        };

        // Pull dialog elements
        final EditText nameEdit, pathEdit;
        if (dialog != null) {
            nameEdit = dialog.findViewById(R.id.ui__select_path_dialog__name);
            pathEdit = dialog.findViewById(R.id.ui__select_path_dialog__url);
        } else {
            nameEdit = pathEdit = null;
        }

        // Defensive checks to make sure file has not changed
        // Can happen if the callback is triggered after a long delay
        final long hash = GsFileUtils.crc32(edit);

        final GsCallback.a1<String> insertFileLink = (path) -> {
            if (GsFileUtils.crc32(edit) != hash) {
                return;
            }

            // If path is not under notebook, copy it to the res folder
            final File attachment = new File(path);

            final File home = ApplicationObject.settings().getNotebookDirectory();
            if (!GsFileUtils.isChild(home, attachment)
                    && !net.pixelpen.journal.model.ArchiveImages.isImageName(attachment.getName())
                    && attachment.length() > MAX_ATTACHMENT_BYTES) {
                Toast.makeText(activity, activity.getString(R.string.attachment_too_large,
                                GsFileUtils.getReadableFileSize(attachment.length(), true),
                                GsFileUtils.getReadableFileSize(MAX_ATTACHMENT_BYTES, true)),
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Pull the appropriate title
            String title = "";
            if (nameEdit != null) {
                title = nameEdit.getText().toString();
            }

            if (GsTextUtils.isNullOrEmpty(title)) {
                title = GsFileUtils.getFilenameWithoutExtension(attachment);
            }

            final String localPath = setupFileAttachment(textFormatId, attachment, currentFile);

            // A camera photo arrives full size in the app's own Pictures folder, and a picked
            // cloud file as a copy in the cache. The archive has its own copy now, so these are
            // only taking up space.
            final File cameraDir = activity.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            final File pickedDir = new File(activity.getCacheDir(), "picked");
            final File from = attachment.getParentFile();
            if (from != null && !GsTextUtils.isNullOrEmpty(localPath)
                    && (from.equals(cameraDir) || from.equals(pickedDir))) {
                //noinspection ResultOfMethodCallIgnored
                attachment.delete();
            }

            insertLink.callback(title, localPath);
        };

        final AutumnContextUtils cu = new AutumnContextUtils(activity);

        final GsCallback.a1<File> setFields = file -> {
            if (textFormatId == FormatRegistry.FORMAT_WIKITEXT) {
                // About the Zim's window 'Insert Link', where it is possible to browse for a file to select,
                // Zim defaults, for the first time, to the file link's path to set the description when it's
                // considered empty.  Then, Zim will automatically replace a description with the path of the
                // next selection only if the description had been already automatically set, or manually set
                // before switching the file, to the path of the current selection.  Zim will not replace the
                // description that had been manually set to the path of a future selection, after exchanging
                // that file.  Nor Zim will replace an empty description if this happens after the first time
                // a link is inserted.  Here, for clarity, always replace an empty description, or one set to
                // the path of the current selection, with the path of the next selection.
                if (nameEdit.getText().toString().equals(pathEdit.getText().toString())) {
                    nameEdit.setText("");
                }

                final File notebookDir = _appSettings.getNotebookDirectory();
                final boolean shouldDynamicallyDetermineRoot = _appSettings.isWikitextDynamicNotebookRootEnabled();
                pathEdit.setText(WikitextLinkResolver.resolveSystemFilePath(file, notebookDir, currentFile, shouldDynamicallyDetermineRoot));

                if (GsTextUtils.isNullOrEmpty(nameEdit.getText())) {
                    nameEdit.setText(pathEdit.getText());
                }
            } else {
                if (pathEdit != null) {
                    pathEdit.setText(GsFileUtils.relativePath(currentFile, file));
                }
                if (nameEdit != null && GsTextUtils.isNullOrEmpty(nameEdit.getText())) {
                    nameEdit.setText(GsFileUtils.getNameWithoutExtension(file.getName()));
                }
            }
        };

        // Do each thing as necessary
        switch (action) {
            case IMAGE_CAMERA: {
                cu.requestCameraPicture(activity, insertFileLink);
                break;
            }
            case IMAGE_GALLERY: {
                cu.requestGalleryPicture(activity, insertFileLink);
                break;
            }
            case IMAGE_EDIT: {
                if (pathEdit != null) {
                    final String path = pathEdit.getText().toString().replace("%20", " ");

                    final File abs = new File(path).getAbsoluteFile();
                    if (abs.isFile()) {
                        cu.requestFileEdit(activity, abs);
                        break;
                    }

                    final File currentDir = (textFormatId == FormatRegistry.FORMAT_WIKITEXT) ? WikitextLinkResolver.findAttachmentDir(currentFile) : currentFile.getParentFile();
                    final File rel = new File(currentDir, path).getAbsoluteFile();
                    if (rel.isFile()) {
                        cu.requestFileEdit(activity, rel);
                    }
                }
                break;
            }
            case AUDIO_RECORDING: {
                if (!cu.requestAudioRecording(activity, insertFileLink)) {
                    Toast.makeText(activity, R.string.no_voice_recorder, Toast.LENGTH_LONG).show();
                }
                break;
            }
            case LINK_BROWSE:
            case IMAGE_BROWSE:
            case AUDIO_BROWSE: {
                // Android's own file picker, not Markor's file browser: the picker is the one
                // people know from every other app, with Recent, Downloads and Drive at hand.
                // The file is copied into the archive (pictures scaled into Images, the rest into
                // Files) and linked relatively; one already inside the archive is linked in place.
                final String[] mimes = (action == InsertType.AUDIO_BROWSE) ? new String[]{"audio/*"}
                        : (action == InsertType.IMAGE_BROWSE) ? new String[]{"image/*"}
                        : new String[]{"*/*"};
                cu.requestOpenDocument(activity, mimes, MAX_ATTACHMENT_BYTES, insertFileLink,
                        size -> Toast.makeText(activity, activity.getString(R.string.attachment_too_large,
                                        GsFileUtils.getReadableFileSize(size, true),
                                        GsFileUtils.getReadableFileSize(MAX_ATTACHMENT_BYTES, true)),
                                Toast.LENGTH_LONG).show());
                break;
            }
            case LINK_SPECIAL: {
                AutumnDialogFactory.showSelectSpecialFileDialog(activity, setFields);
                break;
            }
            case LINK_SEARCH: {
                AutumnDialogFactory.showNotebookFilterDialog(activity, null, null, (file, l) -> {
                    setFields.callback(file);
                });
            }
            case LINK_DIALOG:
            case AUDIO_DIALOG:
            case IMAGE_DIALOG: {
                if (nameEdit != null && pathEdit != null) {
                    insertLink.callback(nameEdit.getText().toString(), pathEdit.getText().toString());
                }
            }
        }
    }

    /** Picture: camera or gallery, and nothing else to fill in. */
    public static void showPictureChoice(
            final Activity activity,
            final int textFormatId,
            final Editable edit,
            final File currentFile
    ) {
        // With the cursor on a picture's line, the menu also has a separate section for that
        // picture: remove the link only, or delete it from Autumn altogether. Deleting the link
        // text by hand leaves the file behind in Images, where nothing points at it any more.
        final PictureAtCursor here = findPictureAtCursor(edit, currentFile);

        final LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 8), 0, 0);
        final AlertDialog[] shown = new AlertDialog[1];

        list.addView(menuRow(activity, R.string.camera_picture, false, () -> {
            shown[0].dismiss();
            insertCameraPhoto(activity, textFormatId, edit, currentFile);
        }));
        list.addView(menuRow(activity, R.string.gallery_picture, false, () -> {
            shown[0].dismiss();
            insertGalleryPhoto(activity, textFormatId, edit, currentFile);
        }));

        if (here != null) {
            // Apart from the insert choices: a rule, a little space, and its own heading.
            final View rule = new View(activity);
            final LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 1)));
            rlp.setMargins(dp(activity, 24), dp(activity, 12), dp(activity, 24), dp(activity, 4));
            rule.setLayoutParams(rlp);
            rule.setBackgroundColor(0x33888888);
            list.addView(rule);

            final android.widget.TextView heading = new android.widget.TextView(activity);
            heading.setText(R.string.picture_this_one);
            heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            heading.setAlpha(0.7f);
            heading.setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), dp(activity, 2));
            list.addView(heading);

            list.addView(menuRow(activity, R.string.picture_remove_link, false, () -> {
                shown[0].dismiss();
                removeLink(edit, here);
            }));
            list.addView(menuRow(activity, R.string.picture_delete, true, () -> {
                shown[0].dismiss();
                confirmDeletePicture(activity, edit, currentFile, here);
            }));
        }

        shown[0] = new AlertDialog.Builder(activity, R.style.Theme_AppCompat_DayNight_Dialog_Rounded)
                .setTitle(R.string.insert_image)
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int dp(final Activity a, final int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** One tappable row in the picture menu, styled like a list item. */
    private static View menuRow(final Activity a, final int text, final boolean destructive,
                                final Runnable onTap) {
        final android.widget.TextView row = new android.widget.TextView(a);
        row.setText(text);
        row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        row.setPadding(dp(a, 24), dp(a, 14), dp(a, 24), dp(a, 14));
        row.setMinHeight(dp(a, 48));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (destructive) {
            row.setTextColor(0xFFC62828);
        }
        final android.util.TypedValue tv = new android.util.TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(v -> onTap.run());
        return row;
    }

    /** Take the link out of the note; if it was the whole line, the line goes with it. */
    private static void removeLink(final Editable edit, final PictureAtCursor p) {
        int start = p.start, end = p.end;
        if (end < edit.length() && edit.charAt(end) == '\n'
                && (start == 0 || edit.charAt(start - 1) == '\n')) {
            end++;
        }
        edit.delete(start, end);
    }

    /** A picture link on the cursor's line, and the archive file it points at. */
    private static final class PictureAtCursor {
        final int start, end;   // the link's extent in the text
        final File file;

        PictureAtCursor(final int start, final int end, final File file) {
            this.start = start;
            this.end = end;
            this.file = file;
        }
    }

    private static final java.util.regex.Pattern IMAGE_LINK =
            java.util.regex.Pattern.compile("!\\[[^\\]]*\\]\\(\\s*<?([^)>\\s]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");

    /**
     * The picture link the cursor is on, or the only one on its line, if it points at a file
     * inside the archive. Null otherwise: a web picture, or a link to a file outside the archive,
     * is not Autumn's to delete.
     */
    @Nullable
    private static PictureAtCursor findPictureAtCursor(final Editable edit, final File currentFile) {
        if (edit == null || currentFile == null || currentFile.getParentFile() == null) {
            return null;
        }
        final int cur = Math.max(0, Math.min(TextViewUtils.getSelection(edit)[0], edit.length()));
        final String text = edit.toString();
        final int lineStart = text.lastIndexOf('\n', cur - 1) + 1;
        int lineEnd = text.indexOf('\n', cur);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        final java.util.regex.Matcher m = IMAGE_LINK.matcher(text.substring(lineStart, lineEnd));
        int found = 0;
        java.util.regex.MatchResult only = null, atCursor = null;
        while (m.find()) {
            found++;
            only = m.toMatchResult();
            if (lineStart + m.start() <= cur && cur <= lineStart + m.end()) {
                atCursor = m.toMatchResult();
            }
        }
        final java.util.regex.MatchResult pick = (atCursor != null) ? atCursor : (found == 1 ? only : null);
        if (pick == null) {
            return null;
        }
        final String target = android.net.Uri.decode(pick.group(1));
        if (target.contains("://")) {
            return null;
        }
        try {
            final File f = new File(currentFile.getParentFile(), target).getCanonicalFile();
            final File home = ApplicationObject.settings().getNotebookDirectory().getCanonicalFile();
            if (!f.isFile() || !GsFileUtils.isChild(home, f)) {
                return null;
            }
            return new PictureAtCursor(lineStart + pick.start(), lineStart + pick.end(), f);
        } catch (final Exception e) {
            return null;
        }
    }

    private static void confirmDeletePicture(final Activity activity, final Editable edit,
                                             final File currentFile, final PictureAtCursor p) {
        new AlertDialog.Builder(activity, R.style.Theme_AppCompat_DayNight_Dialog_Rounded)
                .setTitle(R.string.picture_delete)
                .setMessage(R.string.picture_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.picture_delete_yes, (d, w) -> {
                    // The link goes from the note in any case. The file goes too, unless another
                    // note still shows it: deleting it then would break that note instead.
                    removeLink(edit, p);
                    if (isUsedElsewhere(p.file, currentFile)) {
                        Toast.makeText(activity, R.string.picture_kept_in_use, Toast.LENGTH_LONG).show();
                    } else if (!p.file.delete()) {
                        Toast.makeText(activity, R.string.error_could_not_open_file, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** True if any note other than {@code self} mentions the file's name. */
    private static boolean isUsedElsewhere(final File file, final File self) {
        final File home = ApplicationObject.settings().getNotebookDirectory();
        final String name = file.getName();
        final java.util.ArrayDeque<File> dirs = new java.util.ArrayDeque<>();
        dirs.add(home);
        while (!dirs.isEmpty()) {
            final File[] list = dirs.poll().listFiles();
            if (list == null) {
                continue;
            }
            for (final File f : list) {
                if (f.isDirectory()) {
                    if (!f.getName().startsWith(".")) {
                        dirs.add(f);
                    }
                } else if (!f.equals(self) && f.getName().matches("(?i).+\\.(md|txt|markdown|json|html?)")
                        && f.length() < 2_000_000L) {
                    final String t = GsFileUtils.readTextFileFast(f).first;
                    if (t != null && t.contains(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Attach: straight to Android's file picker; the file is copied in and linked. */
    public static void insertAttachment(
            final Activity activity,
            final int textFormatId,
            final Editable edit,
            final File currentFile
    ) {
        fetchAndInsertItem(InsertType.LINK_BROWSE, textFormatId, activity, edit, currentFile, null, null);
    }

    public static void insertCameraPhoto(
            final Activity activity,
            final int textFormatId,
            final Editable edit,
            final File currentFile
    ) {
        fetchAndInsertItem(InsertType.IMAGE_CAMERA, textFormatId, activity, edit, currentFile, null, null);
    }

    public static void insertGalleryPhoto(
            final Activity activity,
            final int textFormatId,
            final Editable edit,
            final File currentFile
    ) {
        fetchAndInsertItem(InsertType.IMAGE_GALLERY, textFormatId, activity, edit, currentFile, null, null);
    }

    public static void insertAudioRecording(
            final Activity activity,
            final int textFormatId,
            final Editable edit,
            final File currentFile
    ) {
        fetchAndInsertItem(InsertType.AUDIO_RECORDING, textFormatId, activity, edit, currentFile, null, null);
    }
}
