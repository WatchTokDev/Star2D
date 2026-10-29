package com.star4droid.template.ui;

import com.star4droid.template.Items.UIHolder;

/**
 * One inner element of a {@link UIHolder} (a node of its .ui file), found by id. Holds no widget: it looks the node up in the holder's
 * current view each time, so it stays valid after {@code setUi(...)} / {@code rebuild()}, and a missing id just does nothing.
 * <pre>
 * hud.node("score").setText("10").setVisible(true);       // or the generated field: hud_score.setText("10");
 * hud_btnPlay.onClick(new Runnable(){ public void run(){ ... } });
 * </pre>
 * Reference it from an editor variable with {@code public UINodeRef scoreLabel;} (Properties &gt; pick "hud/score").
 * Setters return this; getters return "" / 0 / false when the node does not exist.
 */
public final class UINodeRef {
	private final UIHolder holder;
	private final String id;

	public UINodeRef(UIHolder holder, String id) {
		this.holder = holder;
		this.id = id == null ? "" : id;
	}

	public String id() {
		return id;
	}

	public UIHolder holder() {
		return holder;
	}

	public UIView view() {
		return holder == null ? null : holder.ui();
	}

	/** The node of the document (type, props...), or null. */
	public UINode node() {
		UIView v = view();
		return v == null ? null : v.node(id);
	}

	public boolean exists() {
		UIView v = view();
		return v != null && v.has(id);
	}

	// ------------------------------------------------------------------ values

	public UINodeRef setText(String text) {
		if (exists()) view().setText(id, text);
		return this;
	}

	public String getText() {
		return exists() ? view().getText(id) : "";
	}

	/** Slider / Progress value (no onChange event). */
	public UINodeRef setValue(float value) {
		if (exists()) view().setValue(id, value);
		return this;
	}

	public float getValue() {
		return exists() ? view().getValue(id) : 0f;
	}

	public UINodeRef setRange(float min, float max) {
		if (exists()) view().setRange(id, min, max);
		return this;
	}

	public UINodeRef setChecked(boolean on) {
		if (exists()) view().setChecked(id, on);
		return this;
	}

	public boolean isChecked() {
		return exists() && view().isChecked(id);
	}

	public UINodeRef setVisible(boolean v) {
		if (exists()) view().setVisible(id, v);
		return this;
	}

	public boolean isVisible() {
		return exists() && view().isVisible(id);
	}

	public UINodeRef setEnabled(boolean on) {
		if (exists()) view().setEnabled(id, on);
		return this;
	}

	public boolean isEnabled() {
		return exists() && view().isEnabled(id);
	}

	public UINodeRef setAlpha(float alpha) {
		if (exists()) view().setAlpha(id, alpha);
		return this;
	}

	public UINodeRef setImage(String image) {
		if (exists()) view().setImage(id, image);
		return this;
	}

	public UINodeRef setColor(String color) {
		if (exists()) view().setColor(id, color);
		return this;
	}

	/** Any property by key (see the UI Maker); returns an error text or null. */
	public String set(String key, String value) {
		return exists() ? view().set(id, key, value) : "no node " + id;
	}

	public String getTag() {
		return exists() ? view().getTag(id) : "";
	}

	public UINodeRef scrollTo(float x, float y) {
		if (exists()) view().scrollTo(id, x, y);
		return this;
	}

	/** Does what a tap does (fires onClick...). */
	public UINodeRef click() {
		if (exists()) view().click(id);
		return this;
	}

	// ------------------------------------------------------------------ events (see UIListeners)

	public UINodeRef onClick(Runnable r) {
		holder.listeners().onClick(id, r);
		return this;
	}

	public UINodeRef onTouchStart(Runnable r) {
		holder.listeners().onTouchStart(id, r);
		return this;
	}

	public UINodeRef onTouchEnd(Runnable r) {
		holder.listeners().onTouchEnd(id, r);
		return this;
	}

	public UINodeRef onChange(UIListeners.OnValue l) {
		holder.listeners().onChange(id, l);
		return this;
	}

	public UINodeRef onSubmit(UIListeners.OnText l) {
		holder.listeners().onSubmit(id, l);
		return this;
	}

	public UINodeRef onScroll(UIListeners.OnScroll l) {
		holder.listeners().onScroll(id, l);
		return this;
	}

	/** Removes the listeners registered on this node. */
	public UINodeRef clearListeners() {
		holder.listeners().remove(id, null);
		return this;
	}

	@Override
	public String toString() {
		return (holder == null ? "?" : holder.getName()) + "/" + id;
	}
}
