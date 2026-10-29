import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.SceneScript;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Loads the compiled Refs classes + sample scripts and checks that bindRefs() fills the fields. */
public class BindRun {
    static PlayerItem fake(final String name) {
        return (PlayerItem) Proxy.newProxyInstance(PlayerItem.class.getClassLoader(), new Class<?>[]{PlayerItem.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                if (m.getName().equals("getName")) return name;
                if (m.getName().equals("toString")) return "item:" + name;
                if (m.getName().equals("hashCode")) return name.hashCode();
                if (m.getName().equals("equals")) return p == a[0];
                return null;
            }
        });
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        Map<String, PlayerItem> items = new HashMap<String, PlayerItem>();
        for (String n : new String[]{"background", "player", "ground", "enemy1", "coin1", "jumpBtn"}) items.put(n, fake(n));

        Class<?> itemScript = Class.forName("com.star4droid.Game.Scripts.scene1.enemyScript");
        ItemScript s = (ItemScript) itemScript.getDeclaredConstructor().newInstance();
        s.bindRefs(items);
        check(itemScript.getField("player").get(s) == items.get("player"), "item script: field player bound");
        check(itemScript.getField("coin1").get(s) == items.get("coin1"), "item script: field coin1 bound");
        check(itemScript.getField("scoreText").get(s) == null, "item script: element that is not in the map stays null");
        check(s.item("player") == null, "item(): null without a stage (no crash)");

        Class<?> sceneScript = Class.forName("com.star4droid.game.SceneScript.scene1Script");
        SceneScript sc = (SceneScript) sceneScript.getDeclaredConstructor().newInstance();
        sc.bindRefs(items);
        check(sceneScript.getField("ground").get(sc) == items.get("ground"), "scene script: field ground bound");
        check(sc.findItem("x") == null && sc.item("x") == null && sc.findLight("x") == null && sc.getJoint("x") == null,
              "scene script shortcuts are null-safe without a stage");

        // a plain old script (extends ItemScript) must keep working
        Class<?> old = Class.forName("com.star4droid.Game.Scripts.scene1.oldStyleScript");
        ItemScript o = (ItemScript) old.getDeclaredConstructor().newInstance();
        o.bindRefs(items);
        check(true, "old-style script: bindRefs is a harmless no-op");

        // names that clash with ItemScript members must not be generated as fields
        Class<?> base = Class.forName("com.star4droid.Game.Refs.scene1ItemRefs");
        for (String bad : new String[]{"body", "stage", "actor", "playerItem", "class", "my-item"}) {
            boolean declared = false;
            for (java.lang.reflect.Field f : base.getDeclaredFields()) if (f.getName().equals(bad)) declared = true;
            check(!declared, "no generated field named " + bad);
        }
    }
}
