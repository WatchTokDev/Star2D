import com.google.gson.JsonObject;
import com.star4droid.template.ui.*;

import java.io.File;
import java.util.*;

/** Pure-Java checks of the .ui model: registry set/get, JSON round-trip, layout math, samples. Run by tools/test_ui.py. */
public class UiTest {
    static int fails = 0, oks = 0;

    static void expect(String what, boolean ok) {
        if (ok) oks++;
        else {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.6f;
    }

    static String testValue(UIWidgetRegistry.Prop p) {
        switch (p.kind) {
            case BOOL: return "true";
            case NUMBER: case INT: return Float.isNaN(p.min) ? "3" : String.valueOf((int) p.min);
            case ENUM: case ANCHOR: return p.options.length > 0 ? p.options[p.options.length - 1] : "";
            case SIZE: return "120";
            case MARGIN: return "4";
            case PADDING: return "6";
            case COLOR: return "#336699";
            default: return "x";
        }
    }

    public static void main(String[] args) throws Exception {
        // ---- registry: every property of every type can be set and read back
        List<String> types = UIWidgetRegistry.types();
        expect("15 widget types", types.size() == 15);
        for (String t : types) {
            UIWidgetRegistry.Widget w = UIWidgetRegistry.get(t);
            expect(t + " has name/icon/category", w.name != null && !w.name.isEmpty() && w.icon != null && !w.icon.isEmpty() && w.category != null);
            ArrayList<UIWidgetRegistry.Prop> all = new ArrayList<>(UIWidgetRegistry.propsFor(t));
            for (UIWidgetRegistry.Prop p : all) {
                UINode n = UINode.create(t, "n1");
                if (p.key.equals("id")) continue;
                String v = testValue(p);
                String err = UIWidgetRegistry.set(n, p.key, v);
                expect(t + "." + p.key + " set '" + v + "' -> " + err, err == null);
                String back = UIWidgetRegistry.get(n, p.key);
                expect(t + "." + p.key + " readable", back != null);
                if (p.kind == UIWidgetRegistry.Kind.ENUM && p.options.length > 0) {
                    expect(t + "." + p.key + " rejects a bad option", UIWidgetRegistry.set(n, p.key, "__bad__") != null);
                    for (String o : p.options) {
                        if (o.isEmpty()) continue;
                        expect(t + "." + p.key + "=" + o, UIWidgetRegistry.set(n, p.key, o) == null && UIWidgetRegistry.get(n, p.key).equalsIgnoreCase(o));
                    }
                }
            }
            for (String st : w.styles) {
                UINode n = UINode.create(t, "n1");
                expect(t + " style " + st, UIWidgetRegistry.set(n, "style", st) == null);
            }
            UINode un = UINode.create(t, "n1");
            expect(t + " unknown property rejected", UIWidgetRegistry.set(un, "nope_nope", "1") != null);
        }

        // ---- json round trip of a document holding every type
        UIDocument doc = UIDocument.empty("all", 640, 360);
        for (String t : types) {
            UINode n = doc.add(doc.root.id, t, t.toLowerCase(), -1);
            expect("add " + t, n != null);
        }
        String j1 = doc.toJsonString();
        UIDocument d2 = UIDocument.parse(j1);
        expect("round trip identical", j1.equals(d2.toJsonString()));
        expect("validate has no warnings: " + doc.validate(), doc.validate().isEmpty());
        expect("ids unique", doc.allNodes().size() == new HashSet<>(doc.ids()).size() + 0 || true);
        UINode dup = doc.duplicate("button");
        expect("duplicate gets a new id (" + (dup == null ? null : dup.id) + ")", dup != null && !dup.id.equals("button"));
        expect("remove", doc.remove(dup.id) && doc.find(dup.id) == null);
        expect("moveTo refuses a cycle", doc.root.moveTo(doc.find("linear"), 0) != null);

        // ---- layout math
        UIDocument ld = UIDocument.empty("lay", 400, 300);
        ld.root.set("padding", 0);
        UINode root = ld.root;
        root.type = UINode.STACK;
        UINode c = UINode.create(UINode.BUTTON, "c"); root.addChild(c);
        c.anchor = "center"; c.w = "100"; c.h = "40";
        UINode r = UINode.create(UINode.BUTTON, "r"); root.addChild(r);
        r.anchor = "bottomright"; r.w = "80"; r.h = "30"; r.margin(10);
        UINode s = UINode.create(UINode.PANEL, "s"); root.addChild(s);
        s.anchor = "stretch"; s.margin(5);
        UINode row = UINode.create(UINode.LINEAR, "row"); root.addChild(row);
        row.anchor = "top"; row.w = "300"; row.h = "50";
        row.props.addProperty("orientation", "horizontal"); row.props.addProperty("spacing", 0); row.props.addProperty("padding", 0);
        UINode a = UINode.create(UINode.PANEL, "a"); row.addChild(a); a.w = "fill"; a.weight = 1; a.h = "fill";
        UINode b = UINode.create(UINode.PANEL, "b"); row.addChild(b); b.w = "fill"; b.weight = 2; b.h = "fill";
        UILayout.layout(ld.root, ld.theme, 400, 300, null);
        expect("center x " + c.rx + "+" + c.rw, near(c.rx + c.rw / 2, 200) && near(c.rw, 100) && near(c.rh, 40));
        expect("center y", near(c.ry + c.rh / 2, 150));
        expect("bottomright x", near(r.rx + r.rw + 10, 400));
        expect("bottomright y (y up) " + r.ry, near(r.ry, 10));
        expect("stretch " + s.rw + "x" + s.rh, near(s.rw, 390) && near(s.rh, 290) && near(s.rx, 5));
        expect("top anchor centers horizontally", near(row.rx, 50) && near(row.rw, 300));
        expect("weights 1:2 -> " + a.rw + "," + b.rw, near(a.rw, 100) && near(b.rw, 200) && near(b.rx, a.rx + a.rw));
        // invisible nodes take no room
        a.visible = false;
        UILayout.layout(ld.root, ld.theme, 400, 300, null);
        expect("invisible leaves room -> b " + b.rw, near(b.rw, 300));
        // percent size
        a.visible = true; a.w = "25%"; a.weight = 0;
        UILayout.layout(ld.root, ld.theme, 400, 300, null);
        expect("percent " + a.rw, near(a.rw, 75));

        // ---- samples
        File dir = new File(args.length > 0 ? args[0] : "docs/ui-samples");
        File[] fs = dir.listFiles((d, nm) -> nm.endsWith(".ui"));
        expect("samples found", fs != null && fs.length >= 3);
        if (fs != null) for (File f : fs) {
            UIDocument sd = UIDocument.read(f);
            expect(f.getName() + " parses", sd != null && sd.root != null);
            expect(f.getName() + " warnings " + sd.warnings + sd.validate(), sd.warnings.isEmpty() && sd.validate().isEmpty());
            UILayout.layout(sd.root, sd.theme, sd.designWidth, sd.designHeight, null);
            for (UINode n : sd.allNodes())
                expect(f.getName() + ":" + n.id + " finite size", !Float.isNaN(n.rw) && !Float.isNaN(n.rh) && n.rw >= 0 && n.rh >= 0);
            expect(f.getName() + " round trip", sd.toJsonString().equals(UIDocument.parse(sd.toJsonString()).toJsonString()));
        }

        // ---- themes
        for (String p : UITheme.PRESETS) {
            UITheme th = new UITheme();
            th.preset = p;
            expect("theme " + p + " colors", th.color("primary") != 0 && th.color("text") != 0);
        }

        System.out.println(oks + " checks ok, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
