package com.star4droid.template.ui;

/** {@link UIEvents} with empty methods: {@code new UIEventsAdapter(){ public void onClick(UIView ui, String id){...} }}. */
public abstract class UIEventsAdapter implements UIEvents {
    @Override
    public void onCreate(UIView ui) {}

    @Override
    public void onClick(UIView ui, String id) {}

    @Override
    public void onTouchStart(UIView ui, String id) {}

    @Override
    public void onTouchEnd(UIView ui, String id) {}

    @Override
    public void onChange(UIView ui, String id, Object value) {}

    @Override
    public void onSubmit(UIView ui, String id, String text) {}

    @Override
    public void onScroll(UIView ui, String id, float x, float y) {}
}
