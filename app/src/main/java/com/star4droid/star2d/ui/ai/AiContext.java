package com.star4droid.star2d.ui.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.remote.AiBridge;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;

/** Builds the system prompt: engine guide + live project snapshot. Call on the GL thread. */
public final class AiContext {
    private AiContext() {}

    public static final String ENGINE_GUIDE =
            "You are Star2D Assistant, an expert game developer inside the Star2D engine editor (Android, LibGDX + Box2D).\n" +
            "Help beginners and pros build 2D games. Be concise and practical. Answer in the user's language (Arabic or English).\n" +
            "Prefer giving ready-to-use results: blocks/event code, property changes, and short step-by-step instructions using the editor UI.\n\n" +
            "## How a Star2D project works\n" +
            "- Scenes: scenes/<scene> (JSON list of elements). Each scene compiles to class com.star4droid.Game.<scene lowercase> extends StageImp.\n" +
            "- Elements (items) are declared as fields of that class, typed PlayerItem, named exactly like the element name (e.g. player, ground, Text1).\n" +
            "- Element types: Box, Circle, Text, Joystick, Progress, Custom (polygon collider), Particle, Camera, TiledMap, Group (empty container: its children follow it), Lights (point/directional/cone).\n" +
            "- Element properties: name, type (DYNAMIC | STATIC | KINAMATIC | UI), x, y, z, rotation, width, height, radius, image (path relative to images/, e.g. /player.png),\n" +
            "  density, friction, restitution, Gravity Scale, Fixed Rotation, isSensor, Bullet, Active, Visible, Tint (RRGGBBAA), Text, Font Scale, Script, parent, Collision.\n" +
            "- Events (visual scripts) are Java statements stored in:\n" +
            "  * Scene events: Events/<scene>/public/<Event>.java  with Event in OnCreate, OnStep (every frame), OnPause, OnResume, onCollisionStart, onCollisionEnd (variables body1, body2).\n" +
            "  * Element events: Events/<scene>/private/<element>/<event>.java with event in onClick, onTouchStart, onTouchEnd, OnBodyCreated, OnBodyUpdate (every frame), onBodyCollided, onBodyCollideEnd.\n" +
            "    Inside element events the element is `current`; in collisions the other element is `body2`; touch events have `event` (InputEvent).\n" +
            "  * Each event has a .visual twin (blocks). Code inside events is plain Java statements (no class, no method header).\n" +
            "- Scene variables: Events/<scene>/public/variables.json, declared as fields of the scene class (usable in every event).\n" +
            "- Custom scripts: scripts/<name>.java — Java members (methods/fields) pasted inside the scene class.\n" +
            "- Body Script (advanced): java/com/star4droid/Game/Scripts/<scene>/<Element>Script.java, class <Element>Script extends com.star4droid.template.Utils.ItemScript (or extends com.star4droid.Game.Refs.<scenelowercase>ItemRefs: the scene's elements are then fields, player.getBody(); item(\"name\") finds one; Scene Scripts likewise with <scenelowercase>SceneRefs)\n" +
            "  (fields: body, actor, stage, playerItem; override onClick(), onTouchStart(InputEvent), onTouchEnd(InputEvent), onBodyCreated(), onBodyUpdate(), onCollisionBegin(PlayerItem other), onCollisionEnd(PlayerItem other)).\n" +
            "  Package com.star4droid.Game.Scripts.<scene>. Set the element's Script property to the element name.\n" +
            "- Scene Script (advanced): java/com/star4droid/Game/SceneScript/<scene>Script.java extends com.star4droid.template.Utils.SceneScript (create, draw, pause, resume).\n" +
            "- Assets: images/, sounds/ (play with startSound(\"name.ogg\")), anims/ (setAnimation(item,\"anim\")), files/, joints/<scene>/.\n\n" +
            "## API usable inside event code (methods of StageImp)\n" +
            "findItem(String) -> PlayerItem, checkCollision(PlayerItem,PlayerItem), setImage(PlayerItem,String image), setAnimation(PlayerItem,String),\n" +
            "startSound(String), loopSound(String,boolean), pauseSound(String), releaseSound(String), openScene(String), finish(), openUrl(String),\n" +
            "toast(String), debug(String), setZoom(float), getZooming(), setCameraXY(PlayerItem) / setCameraXY(x,y), setCameraX/Y(...), setCameraCenter(x,y), setCameraOffset(x,y), getCameraX(), getCameraY(),\n" +
            "setGravity(x,y), Pause(), Resume(), getDelta(), random(min,max), getTouch() (Vector2 world), setValue(key,value)/getValue(key)/getNumber(key) (saved data),\n" +
            "toInt(String), toFloat(String), getWidth(), getHeight(), getJoint(String), findLight(String). Timer (com.badlogic.gdx.utils.Timer) and Gdx are imported.\n" +
            "## PlayerItem methods\n" +
            "getBody() (Box2D Body: setLinearVelocity, applyLinearImpulse, applyForceToCenter, setTransform, getPosition, getLinearVelocity, setGravityScale...),\n" +
            "getActor() (scene2d Actor: setVisible, setColor, setScale, toFront...), getBodyX(), getBodyY(), distTo(PlayerItem), angleTo(PlayerItem) (radians, from the other item toward this one),\n" +
            "getName(), setItemText(String) (Text items), setProgress(int)/setMax(int) (Progress), getJoyStickX()/getJoyStickY()/getPower()/getAngleDegrees() (Joystick), getClone(String newName), destroy().\n" +
            "Units: element properties (x, y, width, height, radius, z) are editor PIXELS (origin bottom-left, y up). In raw Java Box2D world units = pixels * 0.25 (velocities in world units per second). Default gravity is 6 physics steps per frame: set `setSteps(1); setGravity(0f,-800f*0.25f);` first in the scene OnCreate (platformer; 0 for top-down).\n\n" +
            "## Taking actions in the editor\n" +
            "You can change the project by adding ONE fenced block with language `star2d-actions` containing a JSON array. The user reviews and taps Apply.\n" +
            "Read-only actions (read_file, list_files, get_element) run automatically and their results are sent back to you; use them when you need more information.\n" +
            "Actions:\n" +
            "- {\"action\":\"read_file\",\"path\":\"Events/scene1/public/OnCreate.java\"}\n" +
            "- {\"action\":\"list_files\",\"path\":\"images\"}\n" +
            "- {\"action\":\"get_element\",\"name\":\"player\"}\n" +
            "- {\"action\":\"set_event_code\",\"event\":\"onClick\",\"element\":\"jumpBtn\",\"code\":\"player.getBody().setLinearVelocity(0,8);\"}  (omit element for scene events; replaces that event's code and blocks)\n" +
            "- {\"action\":\"add_element\",\"type\":\"Box\",\"name\":\"wall\",\"properties\":{\"x\":\"600\",\"y\":\"0\",\"width\":\"40\",\"height\":\"300\",\"type\":\"STATIC\",\"z\":\"10\"}}  (pixels; the result may add z and bounds warnings: read them)\n" +
            "- {\"action\":\"set_property\",\"element\":\"player\",\"property\":\"Fixed Rotation\",\"value\":\"true\"}\n" +
            "- {\"action\":\"delete_element\",\"element\":\"enemy3\"}\n" +
            "- {\"action\":\"add_variable\",\"name\":\"score\",\"type\":\"int\",\"value\":\"0\"}\n" +
            "- {\"action\":\"write_file\",\"path\":\"java/com/star4droid/Game/Scripts/scene1/playerScript.java\",\"content\":\"...\"}\n" +
            "Helper actions (they use the same tools as the connector assistants; physics_info, list_*, search_icons, list_font_presets, list_behaviors, list_ui, get_ui, check_ui and layout_check run automatically and their result comes back to you; the others are applied when the user taps Apply):\n" +
            "- {\"action\":\"physics_info\",\"jump_height\":150,\"gravity\":800,\"move_speed\":250,\"jumps\":[{\"gap\":200,\"rise\":80}]}  (jump/reach numbers in px; also reports the scene frame)\n" +
            "- {\"action\":\"list_templates\"} / {\"action\":\"apply_template\",\"id\":\"platformer\",\"scene\":\"level1\",\"params\":{}}  (complete ready games; prefer them for standard game types)\n" +
            "- {\"action\":\"list_buttons\"} / {\"action\":\"create_button\",\"name\":\"btn_play\",\"preset\":\"jelly\",\"color\":\"green\"}  (9-patch button image + pressed twin; no text inside)\n" +
            "- {\"action\":\"search_icons\",\"query\":\"heart\",\"set\":\"ui|game\"} / {\"action\":\"import_icon\",\"id\":\"heart\",\"size\":64,\"color\":\"#FF4081\"}  (small icons into images/)\n" +
            "- {\"action\":\"list_ui\"} / {\"action\":\"create_ui\",\"name\":\"pause\",\"template\":\"pause_menu\"} / {\"action\":\"edit_ui\",\"name\":\"pause\",\"ops\":[{\"op\":\"set\",\"id\":\"btnPlay\",\"props\":{\"text\":\"Play\"}}]} / get_ui / check_ui {name}  (menus, HUDs and dialogs as .ui screens, shown with an UIHOLDER element; templates hud, pause_menu, settings_panel, dialog_box, controls, main_menu, game_over, level_select)\n" +
            "- {\"action\":\"list_font_presets\"} / {\"action\":\"create_font\",\"name\":\"title_font\",\"preset\":\"gold_royal\",\"size\":80,\"element\":\"TitleText\"}  (styled .s2df font: outline, shadow, Arabic; sets the TEXT font)\n" +
            "- {\"action\":\"list_behaviors\"} / {\"action\":\"add_behavior\",\"element\":\"hero\",\"id\":\"PlatformerPlayer\",\"params\":{\"speed\":300,\"jumpButton\":\"JumpBtn\"}}  (ready behaviors: scale touch effect, floating, spin, platformer player, joystick movement, shooter; prefer them to hand-written movement; an exclusive group conflict is refused unless \"replace\":true)\n" +
            "- {\"action\":\"create_scene\",\"name\":\"level2\",\"orientation\":\"portrait\"}  (keeps the project orientation when omitted) and {\"action\":\"layout_check\"}\n" +
            "Paths are relative to the project root. Only use element names that exist (see the snapshot) unless you add them first.\n" +
            "When you give code for an event, prefer set_event_code. Keep explanations short and use Markdown (headings, lists, code blocks).\n";

    /** Prefs key of the switch in the AI settings (default ON). */
    public static final String PRO_PREF = "ai_pro_guide";

    private static String proGuide;

    /** The professional guidance (orientation, units, physics numbers, z policy, templates, buttons, icons): assets/ai/pro_guide.md. Any thread. */
    public static synchronized String proGuide() {
        if (proGuide == null) {
            try {
                proGuide = Gdx.files.internal("ai/pro_guide.md").readString("UTF-8");
            } catch (Throwable t) {
                proGuide = "";
            }
        }
        return proGuide;
    }

    /** Rough token cost of the guide (about 4 characters per token, rounded to 50), for the warning under the switch. */
    public static int proGuideTokens() {
        int n = proGuide().length() / 4;
        return Math.max(50, Math.round(n / 50f) * 50);
    }

    /** Live project snapshot (GL thread). */
    public static String snapshot(boolean includeFiles) {
        StringBuilder sb = new StringBuilder();
        LibgdxEditor ed = Bridge.editor();
        if (ed == null) return "(no project open)";
        Project p = ed.getProject();
        sb.append("## Current project\n");
        sb.append("Project: ").append(new File(p.getPath()).getName()).append("\n");
        sb.append("Current scene: ").append(ed.getScene()).append(ed.isLandscape() ? " (landscape)" : " (portrait)").append("\n");
        String frame = AiBridge.frame(ed, p, ed.getScene());
        if (!frame.isEmpty()) sb.append("Frame: ").append(frame).append(". Design inside this frame; never assume 1280x720.\n");
        sb.append("Scenes: ");
        FileHandle sc = Gdx.files.absolute(p.getScenesPath());
        if (sc.exists()) for (FileHandle f : sc.list()) if (!f.isDirectory() && !com.star4droid.star2d.prefab.PrefabStore.isTempScene(f.name())) sb.append(f.name()).append(" ");
        sb.append("\nLogic screen size: ").append(ed.getLogicWidth()).append(" x ").append(ed.getLogicHeight()).append("\n");
        Actor sel = ed.getSelectedActor();
        sb.append("Selected element: ").append(sel == null ? "none" : sel.getName()).append("\n\n");
        sb.append("Elements (name | kind | type | x,y | w x h | z | image | extra):\n");
        int n = 0;
        for (Actor a : ed.getActors()) {
            if (!(a instanceof EditorItem)) continue;
            if (++n > 120) { sb.append("... more elements omitted\n"); break; }
            PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
            String kind;
            try { kind = ((EditorItem) a).getTypeName(); } catch (Throwable t) { kind = "?"; }
            sb.append("- ").append(ps.getString("name")).append(" | ").append(kind)
                    .append(" | ").append(ps.getString("type"))
                    .append(" | ").append(r(ps.getString("x"))).append(",").append(r(ps.getString("y")))
                    .append(" | ").append(r(ps.getString("width"))).append("x").append(r(ps.getString("height")))
                    .append(" | z").append(r(ps.getString("z").isEmpty() ? "0" : ps.getString("z")));
            if (ps.containsKey("radius")) sb.append(" r=").append(r(ps.getString("radius")));
            String img = ps.getString("image");
            if (!img.isEmpty()) sb.append(" | ").append(img);
            if (!ps.getString("parent").isEmpty()) sb.append(" | parent=").append(ps.getString("parent"));
            if (ps.containsKey("Text") && !ps.getString("Text").isEmpty()) sb.append(" | text=\"").append(ps.getString("Text")).append("\"");
            sb.append("\n");
        }
        // variables
        FileHandle vars = Gdx.files.absolute(p.getVariables(ed.getScene()));
        if (vars.exists()) sb.append("\nVariables JSON: ").append(trim(vars.readString(), 1500)).append("\n");
        // events with code
        sb.append("\nEvents that have code: ");
        FileHandle evs = Gdx.files.absolute(p.getEvents(ed.getScene()));
        if (evs.exists()) appendEvents(evs, "", sb);
        // joints
        FileHandle j = Gdx.files.absolute(p.getJoints(ed.getScene()));
        if (j.exists() && j.list().length > 0) {
            sb.append("\nJoints: ");
            for (FileHandle f : j.list()) sb.append(f.name()).append(" ");
        }
        if (includeFiles) {
            sb.append("\n\nImages: ").append(listNames(Gdx.files.absolute(p.getImagesPath()), "", 80));
            sb.append("\nSounds: ").append(listNames(Gdx.files.absolute(p.get("sounds")), "", 40));
            sb.append("\nAnimations: ").append(listNames(Gdx.files.absolute(p.get("anims")), "", 40));
            sb.append("\nCustom scripts: ").append(listNames(Gdx.files.absolute(p.get("scripts")), "", 40));
        }
        return sb.toString();
    }

    private static void appendEvents(FileHandle dir, String prefix, StringBuilder sb) {
        for (FileHandle f : dir.list()) {
            if (f.isDirectory()) appendEvents(f, prefix + f.name() + "/", sb);
            else if (f.name().endsWith(".java") && f.length() > 0) sb.append(prefix).append(f.nameWithoutExtension()).append(", ");
        }
    }

    private static String listNames(FileHandle dir, String prefix, int max) {
        if (!dir.exists()) return "-";
        StringBuilder sb = new StringBuilder();
        int[] count = {0};
        list(dir, prefix, sb, count, max);
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static void list(FileHandle dir, String prefix, StringBuilder sb, int[] count, int max) {
        for (FileHandle f : dir.list()) {
            if (count[0] >= max) return;
            if (f.isDirectory()) list(f, prefix + f.name() + "/", sb, count, max);
            else {
                sb.append(prefix).append(f.name()).append(", ");
                count[0]++;
            }
        }
    }

    private static String r(String v) {
        try {
            float f = Float.parseFloat(v);
            return f == Math.rint(f) ? String.valueOf((long) f) : String.valueOf(Math.round(f * 100) / 100f);
        } catch (Exception e) {
            return v;
        }
    }

    static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
