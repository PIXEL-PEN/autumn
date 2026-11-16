/*#######################################################
 *
 *   Autumn
 *   Copyright (C) 2026 PIXEL-PEN
 *   Licensed under the GNU General Public License v3.0
 *
#########################################################*/
package net.pixelpen.journal.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import net.gsantner.opoc.util.GsContextUtils;
import net.pixelpen.journal.ApplicationObject;
import net.pixelpen.journal.R;
import net.pixelpen.journal.activity.openeditor.OpenFromShortcutOrWidgetActivity;
import net.pixelpen.journal.model.Document;
import net.pixelpen.journal.model.Quicknote;

import java.io.File;
import java.util.Calendar;

/**
 * The box the Quicknote widget opens: a field and the Autumn logo, over the home screen.
 *
 * <p>It always opens across the top of the screen, wherever the widget sits. Opening it over
 * the widget itself was tried and dropped: launchers report a widget's position unreliably,
 * and moving the widget would move the box.
 *
 * <p>The logo saves to this month's Quicknotes file, the same file the bottom-bar Quicknote
 * writes, stamped now, and closes. Back or a tap outside closes without saving.
 *
 * <p>Long-pressing the logo opens the month, as long-pressing the bar item does. The widget
 * cannot take a long press itself -- the launcher keeps that gesture for moving the widget --
 * so this box is where it lives. It matters most with the bar item switched off in Settings,
 * when this is the only way back to the month.
 *
 * <p>An {@link AppCompatActivity}, not {@code AutumnBaseActivity}: the base activity sets the
 * full-screen app theme, which would replace this dialog theme. The forced night mode is
 * already set in {@code ApplicationObject}, so the box follows the app's theme choice anyway.
 */
public class QuicknoteEntryActivity extends AppCompatActivity {

    private EditText _text;

    @Override
    protected void onCreate(final Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.quicknote_entry);
        placeAtTop();
        _text = findViewById(R.id.qn_entry_text);
        _text.requestFocus();
        findViewById(R.id.qn_entry_save).setOnClickListener(v -> save());
        findViewById(R.id.qn_entry_save).setOnLongClickListener(v -> {
            openMonth();
            return true;
        });
    }

    private void placeAtTop() {
        final Window w = getWindow();
        if (w == null) {
            return;
        }
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        final WindowManager.LayoutParams lp = w.getAttributes();
        lp.dimAmount = 0f;
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        // 20dp clear of each screen edge. Set on the window rather than as padding in the
        // layout, so a tap in that margin is outside the box and closes it like any other.
        final android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        final int margin = Math.round(20 * dm.density);
        lp.width = Math.max(dm.widthPixels - 2 * margin, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        w.setAttributes(lp);
    }

    /**
     * Open this month's Quicknotes in view mode, saving first if anything was typed.
     *
     * <p>The box closes on the way to the month, so unsaved text would simply vanish. The bar's
     * long press can leave text alone because the Calendar's field keeps it; this box cannot.
     *
     * <p>Through the same trampoline a pinned note shortcut uses. It runs in the app's own task;
     * this box has an empty task affinity and is kept out of Recents, so a document started
     * straight from here would land in that hidden task.
     */
    private void openMonth() {
        if (!GsContextUtils.instance.checkExternalStoragePermission(this)) {
            Toast.makeText(this, R.string.qn_widget_no_storage, Toast.LENGTH_LONG).show();
            return;
        }
        final File home = ApplicationObject.settings().getNotebookDirectory();
        final Calendar now = Calendar.getInstance();
        final String text = _text.getText().toString().trim();
        if (!text.isEmpty() && Quicknote.write(home, now, text) == null) {
            Toast.makeText(this, R.string.qn_widget_failed, Toast.LENGTH_LONG).show();
            return;
        }
        final File month = Quicknote.fileFor(home, now);
        if (month == null || !month.isFile()) {
            Toast.makeText(this, R.string.quicknote_none_yet, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, OpenFromShortcutOrWidgetActivity.class)
                .putExtra(Document.EXTRA_FILE, month)
                .putExtra(Document.EXTRA_DO_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void save() {
        final String text = _text.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.qn_widget_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!GsContextUtils.instance.checkExternalStoragePermission(this)) {
            // The text stays in the field; nothing is lost by saying so.
            Toast.makeText(this, R.string.qn_widget_no_storage, Toast.LENGTH_LONG).show();
            return;
        }
        final File home = ApplicationObject.settings().getNotebookDirectory();
        final File written = Quicknote.write(home, Calendar.getInstance(), text);
        if (written == null) {
            Toast.makeText(this, R.string.qn_widget_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.quicknote_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
