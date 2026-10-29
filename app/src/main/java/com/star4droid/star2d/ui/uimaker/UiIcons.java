package com.star4droid.star2d.ui.uimaker;

import com.star4droid.star2d.evo.R;
import com.star4droid.template.ui.UINode;

/** Editor icons (drawables of the app) for the widget types, used by the palette, the tree and the properties header. */
final class UiIcons {
    private UiIcons() {}

    static int forType(String type) {
        switch (type) {
            case UINode.PANEL: return R.drawable.ic_s2_square;
            case UINode.STACK: return R.drawable.ic_s2_layers;
            case UINode.LINEAR: return R.drawable.ic_s2_view_list;
            case UINode.GRID: return R.drawable.ic_s2_view_grid;
            case UINode.SCROLL: return R.drawable.ic_s2_sort;
            case UINode.LABEL: return R.drawable.ic_s2_text;
            case UINode.IMAGE: return R.drawable.ic_s2_image;
            case UINode.ICON: return R.drawable.ic_s2_widgets;
            case UINode.BUTTON: return R.drawable.ic_s2_touch;
            case UINode.PROGRESS: return R.drawable.ic_s2_progress;
            case UINode.SLIDER: return R.drawable.ic_s2_scale;
            case UINode.CHECKBOX: return R.drawable.ic_s2_check;
            case UINode.RADIO: return R.drawable.ic_s2_circle;
            case UINode.SWITCH: return R.drawable.ic_s2_settings;
            case UINode.TEXTFIELD: return R.drawable.ic_s2_keyboard;
            default: return R.drawable.ic_s2_square;
        }
    }
}
