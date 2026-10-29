package com.star4droid.star2d.Helpers;

import com.star4droid.star2d.CodeEditor.smart.SourceTrace;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UINode;
import com.star4droid.template.ui.UIWidgetRegistry;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates the {@code <holder>_def.uiEvents = new UIEventsAdapter(){...}} block of a UIHolder.
 *
 * <p>The event code of an inner UI element lives in
 * {@code Events/<scene>/private/<holder>/ui/<nodeId>/<event>.java} (+ {@code .visual}, same as every other event),
 * so the event editor and the visual scripting dialog work on it unchanged. Only non-empty events are generated.</p>
 *
 * <p>Variables visible in the code: {@code ui} (UIView), {@code id} (node id) and, per event,
 * onChange: {@code value} (Object), onSubmit: {@code text}, onScroll: {@code x, y}.</p>
 */
public final class UiEventsGen {
    private UiEventsGen() {}

    /** Signature of each UI event method. */
    public static final String[][] EVENTS = {
            {"onClick", "onClick(com.star4droid.template.ui.UIView ui, String id)"},
            {"onTouchStart", "onTouchStart(com.star4droid.template.ui.UIView ui, String id)"},
            {"onTouchEnd", "onTouchEnd(com.star4droid.template.ui.UIView ui, String id)"},
            {"onChange", "onChange(com.star4droid.template.ui.UIView ui, String id, Object value)"},
            {"onSubmit", "onSubmit(com.star4droid.template.ui.UIView ui, String id, String text)"},
            {"onScroll", "onScroll(com.star4droid.template.ui.UIView ui, String id, float x, float y)"},
    };

    /** The "body" argument of getEventPath for an inner node. */
    public static String body(String holder, String nodeId) {
        return holder + "/ui/" + nodeId;
    }

    /** Code of one inner event ("" when none). */
    static String read(SourceTrace trace, com.star4droid.star2d.Helpers.editor.Project project, String scene,
                       String holder, String nodeId, String event, String origin) {
        return CodeGenerator.readTraced(trace, project, scene, body(holder, nodeId), event, origin);
    }

    /**
     * @return Java statements (indented for the generated build method) or "" when the holder has no inner event code.
     */
    public static String generate(SourceTrace trace, com.star4droid.star2d.Helpers.editor.Project project, String scene,
                                  String holder, String uiName) {
        if (uiName == null || uiName.isEmpty()) return "";
        UIDocument doc;
        try {
            File f = com.star4droid.template.ui.UIView.uiFile(project.getPath(), uiName); // "folder/name" and a trailing ".ui" are fine
            if (!f.exists()) return "";
            doc = UIDocument.read(f);
        } catch (Throwable t) {
            return "";
        }
        if (doc == null) return "";
        // event -> code (all nodes), in node order
        LinkedHashMap<String, StringBuilder> perEvent = new LinkedHashMap<>();
        for (UINode n : doc.allNodes()) {
            for (String ev : UIWidgetRegistry.eventsOf(n)) {
                String code = read(trace, project, scene, holder, n.id, ev, scene + " › " + holder + " › " + n.id + " › " + ev);
                if (code == null || code.trim().isEmpty()) continue;
                StringBuilder sb = perEvent.get(ev);
                if (sb == null) perEvent.put(ev, sb = new StringBuilder());
                sb.append("\t\t\t\tif (id.equals(").append(new com.google.gson.Gson().toJson(n.id)).append(")) {\n")
                        .append(code).append("\n\t\t\t\t}\n");
            }
        }
        if (perEvent.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        out.append("\t\t\t").append(holder).append("_def.uiEvents = new com.star4droid.template.ui.UIEventsAdapter(){\n");
        for (String[] e : EVENTS) {
            StringBuilder body = perEvent.get(e[0]);
            if (body == null) continue;
            out.append("\t\t\t\t@Override public void ").append(e[1]).append(" {\n").append(body).append("\t\t\t\t}\n");
        }
        out.append("\t\t\t};");
        return out.toString();
    }

    /** All (nodeId, events) pairs of a ui file that can have event code - used by the events panel. */
    public static Map<String, String[]> nodesWithEvents(UIDocument doc) {
        LinkedHashMap<String, String[]> m = new LinkedHashMap<>();
        for (UINode n : doc.allNodes()) {
            String[] ev = UIWidgetRegistry.eventsOf(n);
            if (ev.length > 0) m.put(n.id, ev);
        }
        return m;
    }
}
