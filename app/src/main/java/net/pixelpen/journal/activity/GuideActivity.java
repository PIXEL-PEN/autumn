/*#######################################################
 *
 *   Autumn
 *   Copyright (C) 2026 PIXEL-PEN
 *   Licensed under the GNU General Public License v3.0
 *
#########################################################*/
package net.pixelpen.journal.activity;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import net.pixelpen.journal.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The user guide: one scrolling screen, built from {@code assets/guide.md}.
 *
 * <p>Rendered natively rather than in a WebView. The guide is the one screen whose whole job
 * is to look like the rest of the app, and the markdown stylesheet does not -- it puts
 * headings in condensed type with a rule beneath, which is what made the quicknote page shout.
 * Native views also mean no renderer assets, no page load and no zoom controls on a document
 * nobody wants to zoom.
 *
 * <p>The markdown it understands is small on purpose: {@code #} to {@code ###} headings,
 * {@code >} callouts, {@code -} bullets, paragraphs, and inline {@code **bold**},
 * {@code *italic*} and {@code `code`}. Two additions of its own:
 *
 * <ul>
 * <li>{@code @icon_name text} -- an icon row: the app's own drawable on a dark chip, the way the
 *     welcome cards show the controls, with the text beside it. The icon is tinted white;
 *     {@code @=icon_name} keeps its own colours, for the bottom-bar icons that carry them.
 *     An unknown name is a typo in the guide, so the row is drawn without a chip rather than
 *     failing.</li>
 * <li>{@code +++ Title} ... {@code +++} -- a fold: a heading that opens and closes what is between
 *     the markers, closed to begin with. For asides most readers will skip.</li>
 * </ul>
 *
 * <p>Writing the guide stays a matter of editing a text file; if you need more than this
 * subset, the guide is probably getting too elaborate.
 */
public class GuideActivity extends AutumnBaseActivity {

    private static final String ASSET = "guide.md";

    @Override
    public void onCreate(final Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.guide__activity);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.guide);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        render(findViewById(R.id.guide__container), readAsset());
    }

    private List<String> readAsset() {
        final List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(getAssets().open(ASSET), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        } catch (final IOException e) {
            lines.add("The guide could not be opened.");
        }
        return lines;
    }

    /**
     * Group the lines into blocks and give each one a view. A block ends at a blank line, or
     * when the next line starts a different kind of block -- which is what lets consecutive
     * bullets each become their own view while a wrapped paragraph stays one.
     *
     * <p>A fold collects the lines up to its closing marker and renders them into its own body,
     * through this same method, so everything that works on the page works inside a fold.
     */
    private void render(final LinearLayout container, final List<String> lines) {
        final StringBuilder para = new StringBuilder();
        final StringBuilder quote = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i).trim();

            if (line.startsWith("+++ ")) {
                flush(container, quote, true);
                flush(container, para, false);
                final List<String> inner = new ArrayList<>();
                int j = i + 1;
                while (j < lines.size() && !lines.get(j).trim().equals("+++")) {
                    inner.add(lines.get(j));
                    j++;
                }
                fold(container, line.substring(4).trim(), inner);
                i = j; // the closing marker, or the end if it was left off
                continue;
            }

            final boolean isQuote = line.startsWith("> ");
            final boolean isIcon = line.matches("@=?[A-Za-z_][A-Za-z0-9_]*(\\s.*)?");

            // A wrapped paragraph is one view, and so is a wrapped callout. Both only end
            // when something else starts, which is why they are flushed here rather than
            // emitted line by line -- a two-line callout was becoming two stacked boxes.
            if (!isQuote) {
                flush(container, quote, true);
            }
            if (line.isEmpty() || line.startsWith("#") || isQuote || isIcon || line.startsWith("- ")
                    || line.startsWith("~ ")) {
                flush(container, para, false);
            }
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("#") || line.startsWith("~ ")) {
                // Consecutive lines of the same kind make one view, a line apiece: a two-line
                // heading, or a quotation with its attribution under it.
                final String prefix = line.startsWith("### ") ? "### " : line.startsWith("## ") ? "## "
                        : line.startsWith("# ") ? "# " : line.startsWith("~ ") ? "~ " : null;
                if (prefix != null) {
                    final StringBuilder joined = new StringBuilder(line.substring(prefix.length()));
                    while (i + 1 < lines.size() && lines.get(i + 1).trim().startsWith(prefix)) {
                        i++;
                        joined.append('\n').append(lines.get(i).trim().substring(prefix.length()));
                    }
                    // A heading straight under the tagline sits closer to it than a section heading
                    // sits to the text above: the two belong together, under the title.
                    final boolean afterTagline = container.getChildCount() > 0
                            && TAGLINE.equals(container.getChildAt(container.getChildCount() - 1).getTag());
                    if ("~ ".equals(prefix)) {
                        container.addView(tagline(joined.toString()));
                    } else if (afterTagline) {
                        container.addView(anchored(heading(joined.toString(), prefix.length() - 1, true)));
                    } else {
                        container.addView(heading(joined.toString(), prefix.length() - 1, false));
                    }
                    continue;
                }
            }

            if (isQuote) {
                append(quote, line.substring(2));
            } else if (line.startsWith("- ")) {
                container.addView(bullet(line.substring(2)));
            } else if (isIcon) {
                container.addView(iconRow(line.substring(1)));
            } else {
                append(para, line);
            }
        }
        flush(container, quote, true);
        flush(container, para, false);
    }

    private static void append(final StringBuilder sb, final String line) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(line);
    }

    private void flush(final LinearLayout container, final StringBuilder sb, final boolean isCallout) {
        if (sb.length() == 0) {
            return;
        }
        container.addView(isCallout ? callout(sb.toString()) : body(sb.toString()));
        sb.setLength(0);
    }

    // -- views -----------------------------------------------------------------------------

    private TextView base(final String text, final float sizeSp, final int colorRes) {
        final TextView t = new TextView(this);
        t.setText(Html.fromHtml(inline(text)));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(ContextCompat.getColor(this, colorRes));
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    /**
     * Levels 1 to 3. Three exists because the Year and Timeline sections each carry a part
     * that needs its own title without being promoted to a section of the archive in its own
     * right. Without it a "### " line fell through to the paragraph branch and was drawn as
     * body text with its hashes still attached.
     */
    private TextView heading(final String text, final int level, final boolean afterTagline) {
        final float size = level == 1 ? 24 : level == 2 ? 18 : 16.5f;
        final TextView t = base(text, size,
                level == 1 ? R.color.guide_title : R.color.guide_heading);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        // Tighter than body text: a heading that runs to two lines should read as one heading.
        t.setLineSpacing(dp(1), 1f);
        final int top = afterTagline ? 6 : level == 1 ? 8 : level == 2 ? 30 : 20;
        margins(t, 0, top, 0, level == 3 ? 6 : 10);
        return t;
    }

    /**
     * A line set apart in the leaf's colour: the epigraph under the title. Italics, if wanted,
     * are written into the line, so an attribution under a quotation can stay upright.
     */
    private TextView tagline(final String text) {
        final TextView t = base("", 16.5f, R.color.guide_tagline);
        // Every line after the first is the attribution, and set smaller than the quotation.
        final int nl = text.indexOf('\n');
        t.setText(Html.fromHtml(nl < 0 ? inline(text)
                : inline(text.substring(0, nl)) + "<br><small>" + inline(text.substring(nl + 1)) + "</small>"));
        t.setTag(TAGLINE);
        margins(t, 0, 2, 0, 4);
        return t;
    }

    /**
     * The subtitle under the tagline, set in from the left behind a short bar in the tagline's
     * colour. Without it the two lines floated: bold like a section heading, but with nothing
     * under them to head.
     */
    private View anchored(final TextView heading) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins((int) dp(4), (int) dp(10), 0, (int) dp(14));
        row.setLayoutParams(rp);

        final View bar = new View(this);
        final GradientDrawable shape = new GradientDrawable();
        shape.setColor(ContextCompat.getColor(this, R.color.guide_tagline));
        shape.setCornerRadius(dp(1));
        bar.setBackground(shape);
        final LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                (int) dp(2), LinearLayout.LayoutParams.MATCH_PARENT);
        bp.setMarginEnd((int) dp(12));
        row.addView(bar, bp);

        final LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(heading, hp);
        return row;
    }

    /** Marks a tagline view, so the heading after it knows to sit close. */
    private static final String TAGLINE = "tagline";

    private TextView body(final String text) {
        final TextView t = base(text, 16, R.color.guide_body);
        margins(t, 0, 0, 0, 12);
        return t;
    }

    private TextView bullet(final String text) {
        final TextView t = base("• " + text, 16, R.color.guide_body);
        margins(t, 10, 0, 0, 8);
        return t;
    }

    /**
     * A control as the app draws it, on a dark chip, with what it does beside it.
     *
     * <p>{@code spec} is the line without its {@code @}: a drawable name, then the text.
     */
    private View iconRow(final String spec) {
        final boolean keepColours = spec.startsWith("=");
        final String rest = keepColours ? spec.substring(1) : spec;
        final int space = rest.indexOf(' ');
        final String name = space < 0 ? rest : rest.substring(0, space);
        final String text = space < 0 ? "" : rest.substring(space + 1).trim();

        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, (int) dp(2), 0, (int) dp(10));
        row.setLayoutParams(rp);

        @SuppressWarnings("DiscouragedApi")
        final int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id != 0) {
            final ImageView icon = new ImageView(this);
            icon.setImageResource(id);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (!keepColours) {
                icon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.welcome_text)));
            }
            final int pad = (int) dp(9);
            icon.setPadding(pad, pad, pad, pad);
            final GradientDrawable chip = new GradientDrawable();
            chip.setColor(ContextCompat.getColor(this, R.color.welcome_bg));
            chip.setCornerRadius(dp(10));
            icon.setBackground(chip);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            final LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int) dp(40), (int) dp(40));
            ip.setMarginEnd((int) dp(14));
            row.addView(icon, ip);
        }

        final TextView t = base(text, 16, R.color.guide_body);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /**
     * A heading that opens and closes the lines under it. Closed to begin with: a fold is for an
     * aside, and the page should read straight through without it.
     */
    private void fold(final LinearLayout container, final String title, final List<String> inner) {
        final TextView head = base("", 18, R.color.guide_heading);
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        margins(head, 0, 30, 0, 10);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        render(body, inner);

        final Runnable label = () -> head.setText(Html.fromHtml(
                (body.getVisibility() == View.VISIBLE ? "\u25BE  " : "\u25B8  ") + inline(title)));
        label.run();
        head.setOnClickListener(v -> {
            body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            label.run();
        });

        container.addView(head);
        container.addView(body);
    }

    /**
     * A tinted panel with a lightbulb at its start, set in from both edges so it reads as an
     * aside. It was a full-width panel with a teal spine, which on the pale page was easy to
     * take for part of the text.
     */
    private View callout(final String text) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackgroundResource(R.drawable.bg_guide_callout);
        box.setPadding((int) dp(14), (int) dp(14), (int) dp(18), (int) dp(14));
        final LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins((int) dp(12), (int) dp(6), (int) dp(12), (int) dp(18));
        box.setLayoutParams(bp);

        final ImageView bulb = new ImageView(this);
        bulb.setImageResource(R.drawable.ic_lightbulb_black_24dp);
        bulb.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.guide_heading)));
        bulb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int) dp(22), (int) dp(22));
        ip.setMarginEnd((int) dp(12));
        ip.topMargin = (int) dp(2);
        box.addView(bulb, ip);

        final TextView t = base(text, 16, R.color.guide_callout_text);
        box.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    // -- helpers ---------------------------------------------------------------------------

    /** The inline subset: bold, italic and code. Escaped first, so the guide cannot inject markup. */
    private static String inline(final String s) {
        String out = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        out = out.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        // Single asterisks after the doubles are gone. Not touching a space on the inside, so a
        // lone asterisk in a sentence -- 5 * 3 -- stays an asterisk.
        out = out.replaceAll("\\*(?=\\S)(.+?)(?<=\\S)\\*", "<i>$1</i>");
        // Only joined headings and taglines carry a newline; a paragraph's lines are joined
        // with spaces before they get here.
        out = out.replace("\n", "<br>");
        out = out.replaceAll("`(.+?)`", "<tt>$1</tt>");
        return out;
    }

    private void margins(final TextView t, final int l, final int top, final int r, final int b) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins((int) dp(l), (int) dp(top), (int) dp(r), (int) dp(b));
        t.setLayoutParams(p);
    }

    private float dp(final float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
