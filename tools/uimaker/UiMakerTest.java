import com.google.gson.*;
import com.star4droid.star2d.ui.uimaker.*;
import com.star4droid.template.ui.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Pure-Java checks of the UI Maker logic and of the `ui` AI tool (UiAi). No GL, no Android. */
public class UiMakerTest {
    static int fails = 0, oks = 0;
    static void check(String n, boolean ok, String d) {
        if (ok) oks++; else { fails++; System.out.println("FAIL " + n + (d == null ? "" : " - " + d)); }
    }
    static void check(String n, boolean ok) { check(n, ok, null); }
    static JsonObject J(String s) { return JsonParser.parseString(s).getAsJsonObject(); }
    static String run(String proj, String json) { return UiAi.run(proj, J(json), "landscape"); }
    static String fail(String proj, String json) {
        try { UiAi.run(proj, J(json), "landscape"); return null; } catch (UiAi.Error e) { return e.getMessage(); }
    }

    public static void main(String[] a) throws Exception {
        // ---------- templates
        for (String t : UiTemplates.names()) {
            UIDocument d = UiTemplates.document("t_" + t, t, "jelly");
            check("template " + t + " valid", d.validate().isEmpty(), String.valueOf(d.validate()));
            check("template " + t + " has nodes", d.root.countNodes() > 3);
            UIDocument back = UIDocument.parse(d.toJsonString());
            check("template " + t + " round trip", back.toJsonString().equals(d.toJsonString()));
            check("template " + t + " sanitize clean", UiOps.sanitize(d.root).isEmpty());
        }
        // ---------- lint
        UIDocument d = UiOps.newDocument("lint", 720, 1280);
        UINode b = UINode.create("Button", "tiny");
        b.w = "20"; b.h = "20"; b.anchor = "topleft";
        d.root.addChild(b);
        UINode off = UINode.create("Button", "off");
        off.w = "200"; off.h = "60"; off.anchor = "topleft"; off.x = 2000;
        d.root.addChild(off);
        List<String> w = UiLint.check(d, 720, 1280, UILayout.APPROX);
        check("lint small target", w.toString().contains("tiny"), w.toString());
        check("lint off-screen", w.toString().contains("off"), w.toString());
        UIDocument ok = UiTemplates.document("ok", "pause_menu", "dark");
        List<String> w2 = UiLint.checkBoth(ok, 1280, 720, UILayout.APPROX);
        check("pause_menu has no overlap warnings", !w2.toString().contains("overlap"), w2.toString());
        // ---------- history
        UIDocument h0 = UiOps.newDocument("h", 720, 1280);
        UiHistory hist = new UiHistory(h0);
        check("history clean", !hist.dirty());
        UIDocument h1 = UIDocument.parse(h0.toJsonString());
        h1.root.addChild(UINode.create("Label", "a"));
        check("history record", hist.record(h1));
        check("history dirty", hist.dirty() && hist.canUndo() && !hist.canRedo());
        UIDocument u = hist.undo();
        check("undo restores", u.root.children.size() == 0);
        UIDocument r = hist.redo();
        check("redo restores", r.root.children.size() == 1);
        hist.markSaved();
        check("saved not dirty", !hist.dirty());
        // ---------- ops
        UIDocument o = UiOps.newDocument("ops", 720, 1280);
        ArrayList<String> notes = new ArrayList<>();
        String e = UiOps.apply(o, J("{op:'add',parent:'root',type:'Button',id:'play',anchor:'center',w:240,h:72,props:{text:'Play'}}"), notes);
        check("op add ok", e == null, e);
        check("op add text", o.find("play") != null && "Play".equals(UIWidgetRegistry.get(o.find("play"), "text")));
        e = UiOps.apply(o, J("{op:'set',id:'play',props:{text:'Go',fontSize:99999}}"), notes);
        String fs = UIWidgetRegistry.get(o.find("play"), "fontSize");
        check("op set clamps out of range numbers", e == null && Float.parseFloat(fs) < 99999f && Float.parseFloat(fs) >= 8f, e + " fontSize=" + fs);
        e = UiOps.apply(o, J("{op:'set',id:'play',props:{bogus:1}}"), notes);
        check("op set unknown prop lists valid", e != null && e.contains("bogus"), e);
        e = UiOps.apply(o, J("{op:'set',id:'nope',props:{text:'x'}}"), notes);
        check("op set unknown id lists ids", e != null && e.contains("play"), e);
        e = UiOps.apply(o, J("{op:'add',parent:'play',type:'Label',id:'inner'}"), notes);
        check("op add into leaf refused", e != null, e);
        e = UiOps.apply(o, J("{op:'add',parent:'root',type:'Wobble'}"), notes);
        check("op add unknown type", e != null && e.contains("Wobble"), e);
        e = UiOps.apply(o, J("{op:'add',parent:'root',type:'Linear',id:'row',children:[{type:'Button',id:'b1',props:{text:'1'}},{type:'Button',id:'b2'}]}"), notes);
        check("op add nested", e == null && o.find("b2") != null, e);
        e = UiOps.apply(o, J("{op:'duplicate',id:'row'}"), notes);
        check("op duplicate unique ids", e == null && o.validate().isEmpty(), e + " " + o.validate());
        e = UiOps.apply(o, J("{op:'move',id:'play',parent:'row',index:0}"), notes);
        check("op move", e == null && o.find("play").parent == o.find("row"), e);
        e = UiOps.apply(o, J("{op:'move',id:'row',parent:'b1'}"), notes);
        check("op move into own descendant refused", e != null, e);
        e = UiOps.apply(o, J("{op:'wrap',id:'play',type:'Panel'}"), notes);
        check("op wrap", e == null && o.find("play").parent.type.equals("Panel"), e);
        e = UiOps.apply(o, J("{op:'set',id:'play',rename:'start'}"), notes);
        check("op rename", e == null && o.find("start") != null && o.find("play") == null, e);
        e = UiOps.apply(o, J("{op:'remove',id:'start'}"), notes);
        check("op remove", e == null && o.find("start") == null, e);
        e = UiOps.apply(o, J("{op:'remove',id:'root'}"), notes);
        check("op remove root refused", e != null, e);
        check("theme preset", UiOps.applyTheme(o, J("{preset:'dark',overrides:{primary:'#ff8800'}}")) == null && "dark".equals(o.theme.preset));
        check("theme bad preset", UiOps.applyTheme(o, J("{preset:'zzz'}")) != null);
        check("theme bad colour", UiOps.applyTheme(o, J("{overrides:{primary:'notacolour'}}")) != null);

        // ---------- the ui tool
        Path tmp = Files.createTempDirectory("uiproj");
        String p = tmp.toString();
        String s = run(p, "{action:'list'}");
        JsonObject lj = J(s);
        check("list templates", lj.getAsJsonArray("templates").size() == 8, s);
        check("list widgets", lj.getAsJsonArray("widgets").size() == 15, s);
        check("list empty screens", lj.getAsJsonArray("screens").size() == 0);
        s = run(p, "{action:'describe'}");
        check("describe all", J(s).getAsJsonArray("types").size() == 15);
        s = run(p, "{action:'describe',type:'Slider'}");
        check("describe slider props", s.contains("min") && s.contains("[number]".toLowerCase()) || s.contains("props"), s);
        check("describe unknown", fail(p, "{action:'describe',type:'Zork'}") != null);
        check("get missing", fail(p, "{action:'get',name:'nope'}").contains("No UI"));
        check("bad name", fail(p, "{action:'create',name:'a b'}") != null);
        s = run(p, "{action:'create',name:'pause',template:'pause_menu',theme:{preset:'candy'}}");
        JsonObject cj = J(s);
        check("create ok", cj.get("ok").getAsBoolean() && new File(p, "UI/pause.ui").isFile(), s);
        check("create outline", cj.get("outline").getAsString().contains("Button"), s);
        check("create is landscape", cj.get("design").getAsString().equals("1280x720"), s);
        check("create twice refused", fail(p, "{action:'create',name:'pause',template:'hud'}").contains("exists"));
        check("create replace", J(run(p, "{action:'create',name:'pause',template:'hud',replace:true,orientation:'portrait'}")).get("design").getAsString().equals("720x1280"));
        check("create unknown template", fail(p, "{action:'create',name:'x',template:'zork'}").contains("hud"));
        s = run(p, "{action:'create',name:'custom',orientation:'portrait',tree:[{id:'title',type:'Label',anchor:'top',margin:[0,60,0,0],props:{text:'Hello',fontSize:48}},{id:'go',type:'Button',anchor:'bottom',margin:[0,0,0,80],w:300,h:90,props:{text:'Go'}}]}");
        check("create tree", J(s).get("nodes").getAsInt() >= 3 && s.contains("title"), s);
        String bad = fail(p, "{action:'create',name:'bad2',tree:[{id:'q',type:'Zork'}]}");
        check("create tree unknown type refused", bad != null && bad.contains("Zork"), bad);
        check("bad tree not written", !new File(p, "UI/bad2.ui").exists());
        s = run(p, "{action:'set',name:'custom',ops:[{op:'add',parent:'root',type:'Switch',id:'snd',anchor:'center',props:{checked:true}},{op:'set',id:'nope',props:{text:'x'}},{op:'set',id:'go',props:{text:'Start'}}]}");
        JsonObject sj = J(s);
        check("set partial", !sj.get("ok").getAsBoolean() && sj.getAsJsonArray("errors").size() == 1 && sj.get("applied").getAsInt() == 2, s);
        s = run(p, "{action:'get',name:'custom',size:[720,1280]}");
        JsonObject gj = J(s);
        check("get has rects", gj.has("rects") && gj.getAsJsonObject("rects").has("go"), s);
        check("get saved change", gj.get("outline").getAsString().contains("Start") && gj.get("outline").getAsString().contains("snd"), s);
        // reads back identical
        UIDocument disk = UIDocument.read(new File(p, "UI/custom.ui"));
        check("file valid on disk", disk.validate().isEmpty(), String.valueOf(disk.validate()));
        s = run(p, "{action:'set',name:'custom',ops:[{op:'add',parent:'root',type:'Button',id:'tiny',anchor:'topleft',w:20,h:20,props:{text:'x'}}]}");
        check("set returns touch warning", s.contains("tiny") && s.contains("warnings"), s);
        s = run(p, "{action:'check',name:'custom'}");
        check("check warns", !J(s).get("ok").getAsBoolean(), s);
        s = run(p, "{action:'check',name:'pause'}");
        check("check has portrait+landscape note", s.contains("portrait and landscape"), s);
        check("set without ops refused", fail(p, "{action:'set',name:'custom'}") != null);
        s = run(p, "{action:'list'}");
        check("list shows screens", J(s).getAsJsonArray("screens").size() == 2, s);
        // events
        s = run(p, "{action:'event',scene:'Main',holder:'ui1',node:'go',event:'onClick',code:'Gdx.app.log(\"x\",\"y\");',ui:'custom'}");
        File ev = new File(p, "Events/Main/private/ui1/ui/go/onClick.java");
        check("event written", ev.isFile() && J(s).get("ok").getAsBoolean(), s);
        s = run(p, "{action:'event',scene:'Main',holder:'ui1',node:'go',event:'onClick'}");
        check("event read", s.contains("Gdx.app.log"), s);
        check("event bad name", fail(p, "{action:'event',scene:'Main',holder:'ui1',node:'go',event:'onFoo'}") != null);
        check("event type mismatch", fail(p, "{action:'event',scene:'Main',holder:'ui1',node:'title',event:'onChange',code:'x',ui:'custom'}") != null);
        check("event path traversal", fail(p, "{action:'event',scene:'../x',holder:'a',node:'b',event:'onClick',code:'x'}") != null);
        // delete
        check("delete needs confirm", fail(p, "{action:'delete',name:'custom'}") != null && new File(p, "UI/custom.ui").exists());
        run(p, "{action:'delete',name:'custom',confirm:true}");
        check("delete", !new File(p, "UI/custom.ui").exists());
        check("unknown action", fail(p, "{action:'zork'}") != null);

        // ---------- lock: locked nodes are skipped by tapping (hit) but test mode can still reach them
        UIDocument lk = UiOps.newDocument("lk", 720, 1280);
        UINode lb = UINode.create("Button", "lockedbtn");
        lb.anchor = "center"; lb.w = "200"; lb.h = "100";
        lk.root.addChild(lb);
        UiRects lr = UiRects.compute(lk, 720, 1280, UILayout.APPROX);
        UiRects.R lbr = lr.get("lockedbtn");
        float lx = lbr.x + lbr.w / 2, ly = lbr.y + lbr.h / 2;
        check("hit finds an unlocked node", lr.hit(lx, ly) != null && lr.hit(lx, ly).node == lb);
        lb.locked = true;
        check("hit skips a locked node", lr.hit(lx, ly) == null);
        check("hit can include locked nodes (test mode)", lr.hit(lx, ly, true) != null && lr.hit(lx, ly, true).node == lb);
        check("locked survives a round trip", UIDocument.parse(lk.toJsonString()).find("lockedbtn").locked);
        UiHistory lh = new UiHistory(lk);
        UIDocument lk2 = UIDocument.parse(lk.toJsonString());
        lk2.find("lockedbtn").locked = false;
        check("lock change is recorded by the history", lh.record(lk2) && lh.dirty());
        // ---------- parent change rules (the Parent chooser uses UINode.moveTo)
        UIDocument pd = UiOps.newDocument("pd", 720, 1280);
        UINode pa = UINode.create("Panel", "pa"), pb = UINode.create("Panel", "pb"), pl = UINode.create("Label", "pl");
        pd.root.addChild(pa);
        pa.addChild(pb);
        pb.addChild(pl);
        check("move into a descendant is refused", pa.moveTo(pb, 0) != null);
        check("move into itself is refused", pa.moveTo(pa, 0) != null);
        check("move into a non container is refused", pb.moveTo(pl, 0) != null);
        check("move to the root works", pl.moveTo(pd.root, pd.root.children.size()) == null && pl.parent == pd.root);
        // ---------- per node theme
        UINode th = UINode.create("Button", "themed");
        check("theme prop is an enum with the presets", UIWidgetRegistry.find("Button", "theme") != null
                && Arrays.asList(UIWidgetRegistry.find("Button", "theme").options).contains("candy"));
        check("set per node theme", UIWidgetRegistry.set(th, "theme", "candy") == null && "candy".equals(UIWidgetRegistry.get(th, "theme")));
        check("clear per node theme", UIWidgetRegistry.set(th, "theme", "") == null && !th.has("theme"));
        // ---------- lint translator
        UIDocument tl = UiOps.newDocument("tl", 720, 1280);
        UINode tb = UINode.create("Button", "tinyb");
        tb.w = "20"; tb.h = "20";
        tl.root.addChild(tb);
        final ArrayList<String> askedKeys = new ArrayList<>();
        List<String> tw = UiLint.check(tl, 720, 1280, UILayout.APPROX, (k, def) -> { askedKeys.add(k); return "T:" + def; });
        check("lint asks the translator", askedKeys.contains("uim_lint_touch") && tw.toString().contains("T:"), askedKeys + " " + tw);
        check("lint without translator is English as before", UiLint.check(tl, 720, 1280, UILayout.APPROX).toString().contains("touch target 'tinyb' is only 20 px thick"));

        // ---------- UiTr: English defaults come back when nothing is translated (Tr has no strings loaded here)
        try {
            check("UiTr option", "center".equals(UiTr.optionLabel("center")) && "default".equals(UiTr.optionLabel("")));
            check("UiTr preset", "glass neon".equals(UiTr.presetName("glass_neon")) && "primary".equals(UiTr.roleName("primary")));
            check("UiTr widget", "Button".equals(UiTr.widgetName("Button")) && !UiTr.widgetDesc("Button", "x").isEmpty());
            UIWidgetRegistry.Prop alpha = UIWidgetRegistry.find("Label", "alpha");
            UIWidgetRegistry.Prop spacing = UIWidgetRegistry.find("Linear", "spacing");
            check("UiTr prop labels", "Opacity".equals(UiTr.propLabel("Label", alpha)) && "Spacing".equals(UiTr.propLabel("Linear", spacing)) && !UiTr.propDesc("Linear", spacing).isEmpty());
            check("UiTr errors", "Already first".equals(UiTr.err("already first")) && "x".equals(UiTr.err("x")) && UiTr.err("Panel 'a' cannot hold children").contains("'a'"));
        } catch (Throwable t) {
            check("UiTr usable on the JVM", false, t.toString());
        }

        System.out.println(fails == 0 ? "ALL OK (" + oks + " checks)" : fails + " FAILED of " + (oks + fails));
        System.exit(fails == 0 ? 0 : 1);
    }
}
