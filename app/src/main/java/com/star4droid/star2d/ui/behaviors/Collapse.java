package com.star4droid.star2d.ui.behaviors;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/**
 * Smooth open / close of a block of views by animating its height (measured once, then plain layout passes: it works inside a
 * ScrollView and never leaves the view with a fixed height). Calling show / hide in the middle of an animation reverses it from
 * where it is.
 */
public final class Collapse {
	private Collapse() {}

	static final long DURATION = 230;
	static final PathInterpolator INTERPOLATOR = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

	private static void cancel(View v) {
		Object o = v.getTag();
		if (o instanceof Animator) ((Animator) o).cancel();
	}

	/** Opens {@code v} (currently GONE or closing) to its natural height. {@code widthPx} = the width it will have. */
	public static void show(final View v, int widthPx) {
		cancel(v);
		final ViewGroup.LayoutParams lp = v.getLayoutParams();
		int start = v.getVisibility() == View.VISIBLE ? v.getHeight() : 0;
		lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
		int w = widthPx > 0 ? widthPx : v.getResources().getDisplayMetrics().widthPixels;
		v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		final int target = v.getMeasuredHeight();
		if (target <= 0 || target == start) {
			v.setVisibility(View.VISIBLE);
			v.requestLayout();
			return;
		}
		lp.height = start;
		v.setVisibility(View.VISIBLE);
		v.requestLayout();
		run(v, start, target, false);
	}

	/** Closes {@code v}: its height goes to 0, then it is GONE. */
	public static void hide(final View v) {
		cancel(v);
		if (v.getVisibility() != View.VISIBLE) return;
		int start = v.getHeight();
		if (start <= 0) {
			v.setVisibility(View.GONE);
			return;
		}
		run(v, start, 0, true);
	}

	private static void run(final View v, int from, int to, final boolean closing) {
		final ViewGroup.LayoutParams lp = v.getLayoutParams();
		ValueAnimator a = ValueAnimator.ofInt(from, to);
		a.setDuration(Math.max(180, Math.min(320, DURATION + Math.abs(to - from) / 8)));
		a.setInterpolator(INTERPOLATOR);
		a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator va) {
				lp.height = (Integer) va.getAnimatedValue();
				v.requestLayout();
			}
		});
		a.addListener(new AnimatorListenerAdapter() {
			boolean cancelled;

			@Override
			public void onAnimationCancel(Animator animation) {
				cancelled = true;
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				if (v.getTag() == animation) v.setTag(null);
				if (cancelled) return;
				lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
				if (closing) v.setVisibility(View.GONE);
				v.requestLayout();
			}
		});
		v.setTag(a);
		a.start();
	}
}
