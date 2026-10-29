package com.star4droid.star2d.ui.behaviors;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Children placed left to right (right to left in RTL) and wrapped to new lines; a gap of {@code gap} px between them. */
final class FlowLayout extends ViewGroup {
	private final int gap;

	FlowLayout(Context c, int gapPx) {
		super(c);
		gap = gapPx;
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		int maxW = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
		int x = 0, y = 0, lineH = 0, usedW = 0;
		for (int i = 0; i < getChildCount(); i++) {
			View v = getChildAt(i);
			if (v.getVisibility() == GONE) continue;
			v.measure(MeasureSpec.makeMeasureSpec(Math.max(0, maxW), maxW == Integer.MAX_VALUE ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
			if (x > 0 && x + w > maxW) {
				x = 0;
				y += lineH + gap;
				lineH = 0;
			}
			x += w + gap;
			lineH = Math.max(lineH, h);
			usedW = Math.max(usedW, x - gap);
		}
		int h = y + lineH + getPaddingTop() + getPaddingBottom();
		int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : usedW + getPaddingLeft() + getPaddingRight();
		setMeasuredDimension(w, resolveSize(h, heightSpec));
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
		int width = r - l;
		int maxW = width - getPaddingLeft() - getPaddingRight();
		int x = 0, y = getPaddingTop(), lineH = 0;
		for (int i = 0; i < getChildCount(); i++) {
			View v = getChildAt(i);
			if (v.getVisibility() == GONE) continue;
			int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
			if (x > 0 && x + w > maxW) {
				x = 0;
				y += lineH + gap;
				lineH = 0;
			}
			int left = rtl ? width - getPaddingRight() - x - w : getPaddingLeft() + x;
			v.layout(left, y, left + w, y + h);
			x += w + gap;
			lineH = Math.max(lineH, h);
		}
	}
}
