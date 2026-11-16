/*#######################################################
 *
 *   Autumn
 *   Copyright (C) 2026 PIXEL-PEN
 *   Licensed under the GNU General Public License v3.0
 *
#########################################################*/
package net.pixelpen.journal.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import net.gsantner.opoc.util.GsContextUtils;
import net.pixelpen.journal.ApplicationObject;
import net.pixelpen.journal.R;
import net.pixelpen.journal.activity.openeditor.OpenFromShortcutOrWidgetActivity;
import net.pixelpen.journal.model.CreationIndex;
import net.pixelpen.journal.model.Document;
import net.pixelpen.journal.util.CategoryManager;
import net.pixelpen.journal.util.NoteTimestamp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;

/**
 * Brings a text file, or shared text, into the archive as a note.
 *
 * <p>Reached three ways: Share to Autumn from another app, Open with Autumn from a file manager,
 * and Import file in the overflow menu, which hands a picked document here. All three end in the
 * same small form -- title, category, date -- and the same result: a new note in
 * Year/Month/Category, stamped like one written from the Calendar, opened in view mode.
 *
 * <p>The content is copied. The source file is never touched, so importing cannot damage
 * anything outside the archive, and the note is an ordinary file of the archive afterwards.
 *
 * <p>An Autumn note brought back in already starts with a stamp. Its date is taken from that
 * stamp and it is not stamped a second time.
 *
 * <p>An {@link AppCompatActivity} for the same reason as QuicknoteEntryActivity: the base activity
 * would replace the dialog theme.
 */
public class ImportActivity extends AppCompatActivity {

    /** Larger than any journal note; a guard against a mis-shared binary or a huge log. */
    private static final int MAX_BYTES = 1024 * 1024;

    /** Matches the Calendar's title field. */
    private static final int MAX_TITLE = 250;

    private EditText _title;
    private Spinner _category;
    private TextView _date;
    private final Calendar _when = Calendar.getInstance();
    private String _body;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_note);
        setTitle(R.string.import_note);

        if (!GsContextUtils.instance.checkExternalStoragePermission(this)) {
            Toast.makeText(this, R.string.qn_widget_no_storage, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        _title = findViewById(R.id.import_title);
        _category = findViewById(R.id.import_category);
        _date = findViewById(R.id.import_date);

        if (!readIncoming(getIntent())) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindCategories();
        showDate();
        _date.setOnClickListener(v -> pickDate());
        findViewById(R.id.import_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.import_ok).setOnClickListener(v -> doImport());
    }

    /**
     * Read the text and a title suggestion from whichever way this was opened.
     *
     * @return false if there is nothing usable: no text, unreadable, too large, or binary.
     */
    private boolean readIncoming(final Intent intent) {
        if (intent == null) {
            return false;
        }
        Uri uri = null;
        String text = null;
        String name = null;

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                final CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                text = shared == null ? null : shared.toString();
                name = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            }
        } else {
            // VIEW from a file manager, or a document picked from Import file.
            uri = intent.getData();
        }

        if (uri != null) {
            name = displayName(uri);
            text = readUri(uri);
        }
        if (text == null) {
            return false;
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        if (text.trim().isEmpty()) {
            return false;
        }

        // An Autumn note coming back in: keep its date, drop its stamp, stamp it once on write.
        final int nl = text.indexOf('\n');
        final String first = (nl < 0 ? text : text.substring(0, nl)).trim();
        if (NoteTimestamp.isStamp(first)) {
            final Long millis = NoteTimestamp.parseStamp(first);
            if (millis != null) {
                _when.setTimeInMillis(millis);
            }
            text = nl < 0 ? "" : text.substring(nl + 1).replaceFirst("^\\s*\\n", "");
        }
        _body = text;

        String title = stripExtension(name);
        if (title == null || title.trim().isEmpty()) {
            title = firstLineAsTitle(text);
        }
        title = title == null ? "" : title.trim();
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE).trim();
        }
        _title.setText(title);
        _title.setSelection(_title.length());

        final TextView source = findViewById(R.id.import_source);
        source.setText(name != null && !name.trim().isEmpty() ? name : getString(R.string.import_shared_text));
        return true;
    }

    /** The file's name as the providing app reports it, falling back to the path's last part. */
    private String displayName(final Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    return c.getString(0);
                }
            } catch (Exception ignored) {
            }
        }
        return uri.getLastPathSegment();
    }

    /**
     * The whole of the document as UTF-8, or null. Null for anything over MAX_BYTES, and for
     * anything holding a NUL byte, which no text file does: a picked file typed as
     * application/octet-stream may be a .md or may be a photo.
     */
    private String readUri(final Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) {
                    return null;
                }
            }
            final byte[] bytes = out.toByteArray();
            for (final byte b : bytes) {
                if (b == 0) {
                    return null;
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripExtension(final String name) {
        if (name == null) {
            return null;
        }
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** First non-blank line, without a Markdown heading's leading hashes. */
    private static String firstLineAsTitle(final String text) {
        for (final String line : text.split("\n")) {
            final String t = line.replaceFirst("^#+\\s*", "").trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return "";
    }

    private void bindCategories() {
        final List<String> cats = CategoryManager.listCategories(this).changedPaths;
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, cats);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        _category.setAdapter(adapter);
        final String general = CategoryManager.protectedCategory(this);
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i).equalsIgnoreCase(general)) {
                _category.setSelection(i, false);
                break;
            }
        }
    }

    /** Shown as the stamp the note will carry, so what you see is what is written. */
    private void showDate() {
        _date.setText(NoteTimestamp.format(_when));
    }

    /** Changes the day only. The time of day stays, as on the Calendar. */
    private void pickDate() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            _when.set(Calendar.YEAR, y);
            _when.set(Calendar.MONTH, m);
            _when.set(Calendar.DAY_OF_MONTH, d);
            showDate();
        }, _when.get(Calendar.YEAR), _when.get(Calendar.MONTH), _when.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Write the note where the Calendar would have, then open it. The form stays on failure. */
    private void doImport() {
        final String title = _title.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a note title.", Toast.LENGTH_SHORT).show();
            return;
        }
        final Object sel = _category.getSelectedItem();
        final String category = sel == null ? CategoryManager.protectedCategory(this) : sel.toString();

        final File home = ApplicationObject.settings().getNotebookDirectory();
        final File dir = new File(new File(new File(home, NoteTimestamp.yearFolderName(_when)),
                NoteTimestamp.monthFolderName(_when)), category);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
            return;
        }

        // The same character rule as a note made on the Calendar.
        final String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        final File note = new File(dir, safeTitle + ".md");
        if (note.exists()) {
            Toast.makeText(this, R.string.import_exists, Toast.LENGTH_LONG).show();
            return;
        }

        String content = NoteTimestamp.format(_when) + "\n\n" + _body;
        if (!content.endsWith("\n")) {
            content += "\n";
        }
        try (FileOutputStream out = new FileOutputStream(note)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            note.delete();
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
            return;
        }

        final CreationIndex cidx = new CreationIndex(this);
        cidx.put(note, _when.getTimeInMillis());
        cidx.save();

        Toast.makeText(this, getString(R.string.import_done, note.getName()), Toast.LENGTH_SHORT).show();

        // Through the shortcut trampoline, into the app's own task. This activity may be sitting
        // in the sharing app's task, or in a hidden one of its own.
        startActivity(new Intent(this, OpenFromShortcutOrWidgetActivity.class)
                .putExtra(Document.EXTRA_FILE, note)
                .putExtra(Document.EXTRA_DO_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
