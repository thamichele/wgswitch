package it.michele.wgswitch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Design "Arancio UI" (artifact "UI per le mie app"): grigi caldi, colore principale a scelta,
 * JetBrains Mono, pulsanti con raggio 4, card con raggio 12, spaziatura 4/8/12/16/24/32, icone a tratto 1.75.
 */
final class Ui {

    /** Un colore principale della sezione "Modalità colori". */
    static final class Accent {
        final String id, name;
        final int main, hover, text, soft, border, deep, light;

        Accent(String id, String name, int main, int hover, int text, int soft, int border, int deep, int light) {
            this.id = id;
            this.name = name;
            this.main = main;
            this.hover = hover;
            this.text = text;
            this.soft = soft;
            this.border = border;
            this.deep = deep;
            this.light = light;
        }
    }

    static final String DEFAULT_ACCENT = "arancio";

    static final Accent[] ACCENTS = {
            new Accent("arancio", "Arancio", 0xFFD96D00, 0xFFE87B12, 0xFFF08A2A, 0xFFF5A04F, 0xFF6B4214, 0xFF36230F, 0xFFA85400),
            new Accent("viola", "Viola", 0xFF9D6BFF, 0xFFAB80FF, 0xFFB48CFF, 0xFFC4A3FF, 0xFF4A3577, 0xFF261E3A, 0xFF6D3FD9),
            new Accent("rosso", "Rosso", 0xFFEC5A5A, 0xFFF06B6B, 0xFFF27C7C, 0xFFF59A9A, 0xFF6E2A2A, 0xFF3A1F1F, 0xFFC42B2B),
            new Accent("giallo", "Giallo", 0xFFE5B000, 0xFFF0BE1A, 0xFFF2C230, 0xFFF5D060, 0xFF6B5410, 0xFF332A0F, 0xFF8A6A00),
            new Accent("blu", "Blu", 0xFF4A90FF, 0xFF5C9BFF, 0xFF6BA6FF, 0xFF8AB8FF, 0xFF24426E, 0xFF17253A, 0xFF1F5FCC),
            new Accent("verde", "Verde", 0xFF2FB36B, 0xFF3CC07A, 0xFF4FCB87, 0xFF6FD69E, 0xFF1F5A3A, 0xFF15291F, 0xFF1A7F48),
    };

    static Accent accent(String id) {
        for (Accent a : ACCENTS) if (a.id.equals(id)) return a;
        return ACCENTS[0];
    }

    static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Colore principale per le notifiche. */
    static int notificationColor(Context c) {
        Accent a = accent(new Prefs(c).accent());
        return isDark(c) ? a.main : a.light;
    }

    static int alpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF);
    }

    // ---- Token ----

    final Context c;
    final boolean dark;
    final Accent a;
    final int bg, panel, raised, border, fieldBorder, text, text2, disabled;
    /** fill = pulsante pieno, onFill = testo sopra, fg = testo e link nel colore principale. */
    final int fill, onFill, fg, soft, deep, deepBorder;
    final int okFg, okBg, errFg, errBg, errBorder, dangerBg, warnFg, warnBg;
    final Typeface regular, medium, semibold;

    Ui(Context c, String accentId) {
        this.c = c;
        dark = isDark(c);
        a = accent(accentId);
        if (dark) {
            bg = 0xFF1C1B1A; panel = 0xFF252422; raised = 0xFF2E2C2A; border = 0xFF3A3734;
            fieldBorder = 0xFF45413D; text = 0xFFEDEAE3; text2 = 0xFFA39E95; disabled = 0xFF77726A;
            fill = a.main; onFill = 0xFF1C1B1A; fg = a.text; soft = a.soft; deep = a.deep; deepBorder = a.border;
            okFg = 0xFF6CC4B6; okBg = alpha(0xFF5DB3A6, 0.16f);
            errFg = 0xFFF0948A; errBg = alpha(0xFFE07368, 0.16f); errBorder = 0xFF6B3631; dangerBg = 0xFF3A2523;
            warnFg = 0xFFE8C35A; warnBg = alpha(0xFFE0B840, 0.16f);
        } else {
            bg = 0xFFF6F4EF; panel = 0xFFFFFFFF; raised = 0xFFEFECE6; border = 0xFFDDD8CF;
            fieldBorder = 0xFFDDD8CF; text = 0xFF22201D; text2 = 0xFF6B665E; disabled = 0xFFA39E95;
            fill = a.light; onFill = 0xFFFFFFFF; fg = a.light; soft = a.light;
            deep = alpha(a.light, 0.10f); deepBorder = alpha(a.light, 0.30f);
            okFg = 0xFF1F6B60; okBg = 0xFFDDF0EC;
            errFg = 0xFFA8362B; errBg = 0xFFF8E1DE; errBorder = alpha(0xFFA8362B, 0.30f); dangerBg = 0xFFF8E1DE;
            warnFg = 0xFF7A3E00; warnBg = 0xFFF6E3CC;
        }
        regular = font(R.font.jetbrains_mono_regular);
        medium = font(R.font.jetbrains_mono_medium);
        semibold = font(R.font.jetbrains_mono_semibold);
    }

    private Typeface font(int res) {
        try {
            return c.getResources().getFont(res);
        } catch (RuntimeException e) {
            return Typeface.MONOSPACE;
        }
    }

    int dp(float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    GradientDrawable rect(int fillColor, int strokeColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        if (strokeColor != 0) d.setStroke(Math.max(1, dp(1)), strokeColor);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private Drawable ripple(Drawable content, int fgColor) {
        // la maschera esplicita rende visibile l'effetto anche sui pulsanti senza fondo
        return new RippleDrawable(ColorStateList.valueOf(alpha(fgColor, 0.18f)), content, rect(0xFFFFFFFF, 0, 4));
    }

    Drawable icon(int res, int color, int sizeDp) {
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return d;
    }

    /** Spaziatura uniforme tra i figli di un LinearLayout. */
    private void gap(LinearLayout l, int gapDp, boolean vertical) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0);
        if (vertical) d.setSize(0, dp(gapDp)); else d.setSize(dp(gapDp), 0);
        l.setDividerDrawable(d);
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
    }

    // ---- Contenitori ----

    LinearLayout column(int gapDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        gap(l, gapDp, true);
        return l;
    }

    LinearLayout row(int gapDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        gap(l, gapDp, false);
        return l;
    }

    FlowLayout flow() {
        return new FlowLayout(c, dp(8), dp(8));
    }

    /** Card: pannello, bordo, raggio 12, con il titolo di sezione in maiuscoletto. */
    LinearLayout card(String title) {
        LinearLayout l = column(12);
        l.setBackground(rect(panel, border, 12));
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (title != null) l.addView(heading(title));
        return l;
    }

    // ---- Testo ----

    TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.35f);
        return t;
    }

    TextView title(String s) {
        TextView t = text(s, 26, text, semibold);
        t.setLetterSpacing(-0.02f);
        return t;
    }

    TextView heading(String s) {
        TextView t = text(s.toUpperCase(java.util.Locale.ITALIAN), 13, text2, semibold);
        t.setLetterSpacing(0.08f);
        t.setAccessibilityHeading(true);
        return t;
    }

    TextView body(String s) {
        return text(s, 15, text, regular);
    }

    TextView hint(String s) {
        TextView t = text(s, 13, text2, regular);
        t.setLineSpacing(0, 1.5f);
        return t;
    }

    TextView label(String s) {
        return text(s, 13, text, medium);
    }

    /** Etichetta a pillola (stati: ok teal, errore rosa, avviso ambra; categoria: colore principale). */
    TextView chip(String s, int fgColor, int bgColor) {
        TextView t = text(s, 12, fgColor, medium);
        t.setLineSpacing(0, 1f);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(rect(bgColor, 0, 999));
        return t;
    }

    void styleChip(TextView t, String s, int fgColor, int bgColor) {
        t.setText(s);
        t.setTextColor(fgColor);
        t.setBackground(rect(bgColor, 0, 999));
    }

    ImageView image(int res, int color, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(icon(res, color, sizeDp));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    // ---- Pulsanti ----

    static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2, DANGER = 3, TONAL = 4;

    TextView button(int kind, String label, int iconRes, View.OnClickListener onClick) {
        int fgColor, bgColor, strokeColor;
        switch (kind) {
            case PRIMARY: fgColor = onFill; bgColor = fill; strokeColor = 0; break;
            case GHOST: fgColor = text; bgColor = 0; strokeColor = 0; break;
            case DANGER: fgColor = errFg; bgColor = dangerBg; strokeColor = errBorder; break;
            case TONAL: fgColor = fg; bgColor = deep; strokeColor = deepBorder; break;
            default: fgColor = text; bgColor = dark ? raised : panel; strokeColor = dark ? fieldBorder : border; break;
        }
        TextView b = text(label, 14, fgColor, kind == PRIMARY ? semibold : medium);
        b.setLineSpacing(0, 1f);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(iconRes != 0 ? 14 : 16), 0, dp(16), 0);
        b.setBackground(ripple(rect(bgColor, strokeColor, 4), fgColor));
        if (iconRes != 0) {
            b.setCompoundDrawablesRelative(icon(iconRes, fgColor, 18), null, null, null);
            b.setCompoundDrawablePadding(dp(8));
        }
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        b.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return b;
    }

    /** Pulsante di scelta del colore principale: quadratino colore + nome, anello quando selezionato. */
    TextView swatch(final Accent opt, boolean selected, View.OnClickListener onClick) {
        int main = dark ? opt.main : opt.light;
        TextView b = text(opt.name, 14, text, medium);
        b.setLineSpacing(0, 1f);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setMinHeight(dp(44));
        b.setPadding(dp(10), 0, dp(14), 0);
        b.setBackground(ripple(rect(selected ? alpha(main, 0.12f) : panel,
                selected ? main : border, 4), text));
        GradientDrawable sw = rect(main, 0, 4);
        sw.setSize(dp(22), dp(22));
        sw.setBounds(0, 0, dp(22), dp(22));
        b.setCompoundDrawablesRelative(sw, null, null, null);
        b.setCompoundDrawablePadding(dp(10));
        b.setClickable(true);
        b.setFocusable(true);
        b.setSelected(selected);
        b.setContentDescription("Colore " + opt.name + (selected ? ", selezionato" : ""));
        b.setOnClickListener(onClick);
        return b;
    }

    // ---- Controlli ----

    /** Interruttore 38×22 del kit: binario nel colore principale, pallino scuro. */
    Switch toggle(String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setTextColor(text);
        s.setTypeface(regular);
        s.setMinHeight(dp(48));
        s.setSwitchPadding(dp(16));

        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked}, knob(onFill));
        thumb.addState(new int[]{}, knob(dark ? text2 : 0xFFFFFFFF));
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, pill(fill, 0));
        track.addState(new int[]{}, pill(dark ? border : 0xFFC9C3B8, 0));
        s.setThumbDrawable(thumb);
        s.setTrackDrawable(track);
        s.setThumbTintList(null);
        s.setTrackTintList(null);
        s.setSwitchMinWidth(dp(38));
        s.setShowText(false);
        s.setChecked(checked);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    private Drawable knob(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize(dp(16), dp(16));
        return new InsetDrawable(d, dp(3));
    }

    private Drawable pill(int color, int stroke) {
        GradientDrawable d = rect(color, stroke, 11);
        d.setSize(dp(38), dp(22));
        return d;
    }

    CheckBox check(String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = new CheckBox(c);
        cb.setText(label);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cb.setTextColor(text);
        cb.setTypeface(regular);
        cb.setMinHeight(dp(44));
        cb.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{fill, text2}));
        cb.setChecked(checked);
        cb.setOnCheckedChangeListener(l);
        return cb;
    }

    /** Campo: fondo pagina, bordo, raggio 4; a fuoco bordo nel colore principale e alone di 3 dp. */
    EditText field(String value, String hintText) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hintText);
        e.setSingleLine();
        e.setEllipsize(TextUtils.TruncateAt.END);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTextColor(text);
        e.setHintTextColor(text2);
        e.setTypeface(regular);
        e.setHighlightColor(alpha(fill, 0.30f));
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(fill);
        cursor.setSize(dp(2), 0);
        e.setTextCursorDrawable(cursor);
        int ring = dp(3);
        int fieldBg = dark ? bg : 0xFFFFFFFF;
        StateListDrawable d = new StateListDrawable();
        LayerDrawable focused = new LayerDrawable(new Drawable[]{
                rect(alpha(fill, 0.22f), 0, 7), new InsetDrawable(rect(fieldBg, fill, 4), ring)});
        d.addState(new int[]{android.R.attr.state_focused}, focused);
        d.addState(new int[]{}, new InsetDrawable(rect(fieldBg, fieldBorder, 4), ring));
        e.setBackground(d);
        e.setPadding(dp(12) + ring, ring, dp(12) + ring, ring);
        e.setMinHeight(dp(44) + 2 * ring);
        return e;
    }

    /** Campo con etichetta sopra e testo d'aiuto opzionale sotto. */
    LinearLayout labeled(String labelText, View field, String help) {
        LinearLayout l = column(6);
        l.addView(label(labelText));
        l.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (help != null) l.addView(hint(help));
        return l;
    }

    /** Avviso evidenziato (icona di attenzione nel colore principale). */
    LinearLayout callout(String titleText, String bodyText) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setBackground(rect(deep, deepBorder, 10));
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        ImageView ic = image(R.drawable.ic_alert, fg, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(20), dp(20));
        lp.setMarginEnd(dp(12));
        lp.topMargin = dp(1);
        l.addView(ic, lp);
        LinearLayout txt = column(2);
        txt.addView(text(titleText, 14, text, semibold));
        if (bodyText != null) txt.addView(hint(bodyText));
        l.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return l;
    }
}
