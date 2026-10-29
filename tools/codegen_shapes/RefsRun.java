import com.star4droid.star2d.Helpers.RefsGenHarness;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

/** Runs the REAL refsFor() text of CodeGenerator (extracted by test_codegen_shapes.py) and writes the generated classes. */
public class RefsRun {
    static PropertySet<String, Object> ps(String name, String type) {
        PropertySet<String, Object> p = new PropertySet<String, Object>();
        p.put("name", name);
        p.put("TYPE", type);
        return p;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        Project project = new Project(out.getPath());
        ArrayList<PropertySet<String, Object>> props = new ArrayList<PropertySet<String, Object>>();
        String[][] items = {
            {"background", "BOX"}, {"player", "BOX"}, {"ground", "BOX"}, {"enemy1", "CIRCLE"}, {"coin1", "BOX"}, {"jumpBtn", "BOX"},
            {"scoreText", "TEXT"}, {"lifeBar", "PROGRESS"}, {"joy", "JOYSTICK"}, {"grp", "GROUP"}, {"sun", "LIGHT"},
            // names that must NOT become fields
            {"body", "BOX"}, {"stage", "BOX"}, {"actor", "BOX"}, {"playerItem", "BOX"}, {"ID", "BOX"},
            {"class", "BOX"}, {"my-item", "BOX"}, {"player", "BOX"}
        };
        for (String[] it : items) props.add(ps(it[0], it[1]));
        for (String scene : new String[]{"scene1", "Level2", "__prefab_x", "1bad"}) {
            ArrayList<RefsGenHarness.SceneCode> r = RefsGenHarness.refsFor(project, scene, props);
            System.out.println(scene + " -> " + r.size() + " files");
            for (RefsGenHarness.SceneCode sc : r) {
                File f = new File(sc.path);
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), sc.code.getBytes(StandardCharsets.UTF_8));
                System.out.println("  " + f.getName());
            }
        }
    }
}
