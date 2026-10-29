package com.star4droid.star2d.ui.uimaker;

import com.star4droid.template.ui.UIDocument;

import java.util.ArrayList;

/** Undo / redo for the UI Maker: the whole document as JSON text after every change (documents are small, so this is simple and safe). */
public final class UiHistory {
    private static final int MAX = 120;
    private final ArrayList<String> states = new ArrayList<>();
    private int pos = -1;
    private int savedPos = -1;

    public UiHistory(UIDocument first) {
        states.add(first.toJsonString());
        pos = 0;
        savedPos = 0;
    }

    /** Records the document after a change. Equal consecutive states are ignored. Drops the redo branch. */
    public boolean record(UIDocument doc) {
        String s = doc.toJsonString();
        if (s.equals(states.get(pos))) return false;
        while (states.size() > pos + 1) states.remove(states.size() - 1);
        states.add(s);
        pos++;
        if (states.size() > MAX) {
            states.remove(0);
            pos--;
            if (savedPos >= 0) savedPos--;
        }
        return true;
    }

    public boolean canUndo() {
        return pos > 0;
    }

    public boolean canRedo() {
        return pos < states.size() - 1;
    }

    public UIDocument undo() {
        if (!canUndo()) return null;
        pos--;
        return UIDocument.parse(states.get(pos));
    }

    public UIDocument redo() {
        if (!canRedo()) return null;
        pos++;
        return UIDocument.parse(states.get(pos));
    }

    public void markSaved() {
        savedPos = pos;
    }

    public boolean dirty() {
        return pos != savedPos;
    }
}
