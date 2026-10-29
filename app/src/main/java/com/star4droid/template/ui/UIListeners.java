package com.star4droid.template.ui;

import com.badlogic.gdx.Gdx;

import java.util.ArrayList;

/**
 * Listeners registered from code on the inner elements of a UI, by node id (in addition to the {@link UIEvents} object and to the
 * event code written in the editor; all of them run). A {@link com.star4droid.template.Items.UIHolder} owns one, so they survive
 * {@code setUi(...)} / {@code rebuild()}.
 * <pre>
 * hud.onClick("btnPlay", new Runnable(){ public void run(){ ... } });      // Java 8+: hud.onClick("btnPlay", () -&gt; ...)
 * hud.onChange("sldVolume", new UIListeners.OnValue(){ public void on(Object value){ ... } });
 * </pre>
 * Callbacks run on the GL thread; an exception in one is logged and does not stop the others.
 */
public final class UIListeners {
	/** onChange: Slider -&gt; Float, Checkbox / Radio / Switch -&gt; Boolean, TextField -&gt; String. */
	public interface OnValue {
		void on(Object value);
	}

	/** onSubmit: the text of the field. */
	public interface OnText {
		void on(String text);
	}

	/** onScroll: px from the top-left of the content. */
	public interface OnScroll {
		void on(float x, float y);
	}

	private static final class Entry {
		final String id, event;
		final Object fn;

		Entry(String id, String event, Object fn) {
			this.id = id;
			this.event = event;
			this.fn = fn;
		}
	}

	private final ArrayList<Entry> list = new ArrayList<Entry>();

	public UIListeners onClick(String id, Runnable r) {
		return add(id, "onClick", r);
	}

	public UIListeners onTouchStart(String id, Runnable r) {
		return add(id, "onTouchStart", r);
	}

	public UIListeners onTouchEnd(String id, Runnable r) {
		return add(id, "onTouchEnd", r);
	}

	public UIListeners onChange(String id, OnValue l) {
		return add(id, "onChange", l);
	}

	public UIListeners onSubmit(String id, OnText l) {
		return add(id, "onSubmit", l);
	}

	public UIListeners onScroll(String id, OnScroll l) {
		return add(id, "onScroll", l);
	}

	private UIListeners add(String id, String event, Object fn) {
		if (id != null && fn != null) list.add(new Entry(id, event, fn));
		return this;
	}

	/** Removes the listeners of a node ({@code event} null = all its events, else e.g. "onClick"). */
	public UIListeners remove(String id, String event) {
		for (int i = list.size() - 1; i >= 0; i--) {
			Entry e = list.get(i);
			if (e.id.equals(id) && (event == null || e.event.equals(event))) list.remove(i);
		}
		return this;
	}

	public UIListeners clear() {
		list.clear();
		return this;
	}

	/** Called by the view: {@code a} / {@code b} are the event arguments (value, text or x / y). */
	void fire(String event, String id, Object a, Object b) {
		if (list.isEmpty()) return;
		Entry[] all = list.toArray(new Entry[0]);
		for (int i = 0; i < all.length; i++) {
			Entry e = all[i];
			if (!e.id.equals(id) || !e.event.equals(event)) continue;
			try {
				if (e.fn instanceof Runnable) ((Runnable) e.fn).run();
				else if (e.fn instanceof OnValue) ((OnValue) e.fn).on(a);
				else if (e.fn instanceof OnText) ((OnText) e.fn).on(String.valueOf(a));
				else if (e.fn instanceof OnScroll) ((OnScroll) e.fn).on(((Float) a).floatValue(), ((Float) b).floatValue());
			} catch (Throwable t) {
				try {
					Gdx.app.error("star2d_ui", "error in " + event + " listener of " + id + ": " + t, t);
					com.star4droid.template.Utils.Utils.Log("ui listener error", com.star4droid.template.Utils.Utils.getStackTraceString(t));
				} catch (Throwable ignored) {
				}
			}
		}
	}
}
