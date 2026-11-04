package it.michele.wgswitch;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Dispone i figli in riga e va a capo quando non c'è spazio: i pulsanti non escono mai dal riquadro. */
final class FlowLayout extends ViewGroup {
    private final int hGap;
    private final int vGap;

    FlowLayout(Context c, int hGap, int vGap) {
        super(c);
        this.hGap = hGap;
        this.vGap = vGap;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : MeasureSpec.getSize(widthSpec);
        int inner = maxW - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0, usedW = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(MeasureSpec.makeMeasureSpec(Math.max(0, inner), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (x > 0 && x + w > inner) {
                y += lineH + vGap;
                x = 0;
                lineH = 0;
            }
            x += w + hGap;
            usedW = Math.max(usedW, x - hGap);
            lineH = Math.max(lineH, h);
        }
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? maxW
                : Math.min(maxW, usedW + getPaddingLeft() + getPaddingRight());
        setMeasuredDimension(width, resolveSize(y + lineH + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int inner = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        // prima calcola le righe, poi centra ogni figlio in verticale nella sua riga
        int n = getChildCount();
        int[] lineOf = new int[n];
        int[] lineHeights = new int[n + 1];
        int line = 0;
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth();
            if (x > 0 && x + w > inner) {
                lineHeights[line] = lineH;
                line++;
                x = 0;
                lineH = 0;
            }
            lineOf[i] = line;
            x += w + hGap;
            lineH = Math.max(lineH, v.getMeasuredHeight());
        }
        lineHeights[line] = lineH;
        x = 0;
        y = 0;
        int cur = 0;
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            if (lineOf[i] != cur) {
                y += lineHeights[cur] + vGap;
                cur = lineOf[i];
                x = 0;
            }
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            int top = getPaddingTop() + y + (lineHeights[cur] - h) / 2;
            int left = getPaddingLeft() + x;
            v.layout(left, top, left + w, top + h);
            x += w + hGap;
        }
    }
}
