package com.star4droid.star2d.behaviors;

/** Source of a new project behavior ("New behavior" in the Files panel). */
public final class BehaviorTemplate {
	private BehaviorTemplate() {}

	public static boolean validName(String n) {
		if (n == null || n.length() == 0 || !Character.isUpperCase(n.charAt(0))) return false;
		for (int i = 0; i < n.length(); i++) if (!(Character.isLetterOrDigit(n.charAt(i)) && n.charAt(i) < 128)) return false;
		return true;
	}

	/**
	 * @param targets "UI", "PHYSICS" or "BOTH"
	 */
	public static String source(String name, String targets) {
		String t = targets == null ? "BOTH" : targets;
		boolean body = t.equals("PHYSICS");
		StringBuilder s = new StringBuilder();
		s.append("package ").append(BehaviorCatalog.PROJECT_PACKAGE).append(";\n\n");
		s.append("import com.star4droid.template.behaviors.*;\n");
		s.append("import com.star4droid.template.Utils.PlayerItem;\n");
		s.append("import com.star4droid.template.Items.UIHolder;\n\n");
		s.append("/**\n * A behavior: attach it to any element from \"Additional behaviors\" in the Properties panel.\n");
		s.append(" * Every public non-static field below becomes a field in the inspector (numbers, booleans, text, Color, enums,\n");
		s.append(" * images with @Image, sounds with @Sound, lists, and elements: PlayerItem / ProgressItem / TextItem / Joystick ...).\n");
		s.append(" * Positions and speeds are in editor pixels (y up). Don't use lambdas (the phone compiles Java 1.7).\n */\n");
		s.append("@BehaviorInfo(\n");
		s.append("    name = \"").append(VarSpec.humanize(name)).append("\",\n");
		s.append("    icon = \"puzzle\",            // any id of the icon library\n");
		s.append("    desc = \"Describe what it does in one line.\",\n");
		s.append("    targets = BehaviorInfo.Target.").append(t).append(",\n");
		s.append("    category = \"My behaviors\"");
		s.append(",\n    events = {\"onDone()\"}       // optional: events shown in the Events panel; raise them with fire(\"onDone\")\n");
		s.append(")\n");
		if (body) s.append("@Requires(body = true)\n");
		s.append("public class ").append(name).append(" extends Behavior {\n\n");
		s.append("    @Param(desc = \"How fast it goes.\", unit = \"px/s\", min = 0, max = 450)\n");
		s.append("    public float speed = 100;\n");
		s.append("    public boolean active = true;\n");
		s.append("    public PlayerItem target;        // pick an element in the inspector\n\n");
		s.append("    // @Block turns a public method into a block (shown in the block editor while the scene uses this behavior).\n");
		s.append("    // Parameters: number, boolean, String, PlayerItem. A method that returns a value becomes a value block.\n");
		s.append("    @Block(label = \"set speed\", desc = \"Changes how fast it goes.\", args = {\"speed\"})\n");
		s.append("    public void setSpeed(float value) {\n        speed = value;\n    }\n\n");
		s.append("    @Override\n    public void onStart() {\n        // runs once when the game starts; item = the element, stage = the scene\n    }\n\n");
		s.append("    @Override\n    public void onUpdate(float dt) {\n        // every frame while the game plays\n");
		s.append("        if (!active) return;\n");
		if (body) s.append("        setVelocityX(speed);\n");
		else s.append("        setCenterX(centerX() + speed * dt);\n");
		s.append("    }\n\n");
		s.append("    @Override\n    public void onTouchStart(com.badlogic.gdx.scenes.scene2d.InputEvent event) {\n    }\n\n");
		s.append("    @Override\n    public void onCollisionBegin(PlayerItem other) {\n    }\n\n");
		s.append("    @Override\n    public void onDisable() {\n        // undo what the behavior did to the element: the saved properties are never rewritten\n    }\n}\n");
		return s.toString();
	}
}
