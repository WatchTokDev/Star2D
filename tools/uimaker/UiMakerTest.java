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

        System.out.println(fails == 0 ? "ALL OK (" + oks + " checks)" : fails + " FAILED of " + (oks + fails));
        System.exit(fails == 0 ? 0 : 1);
    }
}
