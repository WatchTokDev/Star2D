package com.star4droid.template.ui;

/**
 * What a {@link UIView} tells the game. Every callback receives the view and the id of the node that triggered it, so ONE object
 * handles a whole .ui ({@code switch(id)}). Use {@link UIEventsAdapter} to override only the callbacks you need.
 */
public interface UIEvents {
    /** Called once after the view was built (and again after {@link UIView#rebuild()}): set start values here. */
    void onCreate(UIView ui);

    /** A button / clickable node was clicked (finger down and up inside it). */
    void onClick(UIView ui, String id);

    void onTouchStart(UIView ui, String id);

    void onTouchEnd(UIView ui, String id);

    /** Slider: Float. Checkbox / Radio / Switch: Boolean. TextField: String. */
    void onChange(UIView ui, String id, Object value);

    /** TextField: the user pressed enter / done. */
    void onSubmit(UIView ui, String id, String text);

    /** ScrollView moved: x, y in px from the top-left of the content. */
    void onScroll(UIView ui, String id, float x, float y);
}
