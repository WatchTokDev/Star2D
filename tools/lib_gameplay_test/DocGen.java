import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.ReflectScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.template.behaviors.Block;

import java.lang.reflect.Method;
import java.util.List;

/** Prints the per-behavior tables of docs/behaviors/gameplay.md (run by tools/test_lib_gameplay.py --docs). Reads the same annotations the editor shows. */
public class DocGen {
	static String esc(String s) {
		return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
	}

	static String num(double d) {
		if (d == Math.rint(d) && Math.abs(d) < 1e9) return Long.toString((long) d);
		return Double.toString(d);
	}

	static String def(VarSpec v) {
		Object d = v.def;
		if (v.list) return "empty list";
		if (v.kind == VarSpec.Kind.REF) return "none";
		if (d == null) return "";
		if (d instanceof Number) return num(((Number) d).doubleValue());
		String s = String.valueOf(d);
		return s.length() == 0 ? "empty" : "`" + s + "`";
	}

	static String range(VarSpec v) {
		if (!v.isNumber()) return v.kind == VarSpec.Kind.ENUM ? v.options.toString().replace("[", "").replace("]", "") : "";
		return num(v.min) + " .. " + num(v.max) + (v.step > 0 ? " (step " + num(v.step) + ")" : "");
	}

	static String type(VarSpec v) {
		String t;
		switch (v.kind) {
			case REF: t = v.elemType; break;
			case ENUM: t = "choice"; break;
			case BOOL: t = "yes/no"; break;
			case IMAGE: t = "image"; break;
			case SOUND: t = "sound"; break;
			case COLOR: t = "color"; break;
			case STRING: t = "text"; break;
			default: t = v.kind.name().toLowerCase();
		}
		return v.list ? "list of " + t : t;
	}

	public static void main(String[] a) throws Exception {
		String[] ids = a;
		StringBuilder sum = new StringBuilder();
		sum.append("| Behavior | Icon | Target | What it does | Events | Blocks |\n|---|---|---|---|---|---|\n");
		StringBuilder det = new StringBuilder();
		for (String id : ids) {
			Class<?> c = Class.forName("com.star4droid.template.behaviors.lib." + id);
			BehaviorMeta m = ReflectScanner.behavior(c);
			int blocks = 0;
			StringBuilder bl = new StringBuilder();
			for (Method me : c.getDeclaredMethods()) {
				Block b = me.getAnnotation(Block.class);
				if (b == null) continue;
				blocks++;
			}
			StringBuilder ev = new StringBuilder();
			for (BehaviorMeta.EventSpec e : m.events) ev.append(ev.length() > 0 ? ", " : "").append('`').append(e.name).append('`');
			sum.append("| [").append(m.id).append("](#").append(m.id.toLowerCase()).append(") | `").append(m.icon).append("` | ").append(m.targets.toLowerCase()).append(" | ")
					.append(esc(m.desc)).append(" | ").append(ev.length() == 0 ? "-" : ev).append(" | ").append(blocks).append(" |\n");

			det.append("\n### ").append(m.id).append("\n\n").append(m.desc).append("\n\n");
			det.append("Id `").append(m.id).append("` · icon `").append(m.icon).append("` · category ").append(m.category).append(" · works on ").append(m.targets.toLowerCase()).append(" elements");
			if (m.reqBody) det.append(" · needs a physical element");
			det.append("\n\n**Parameters**\n\n| Parameter | Type | Default | Range / choices | Unit | Meaning |\n|---|---|---|---|---|---|\n");
			String header = "";
			for (VarSpec v : m.vars) {
				if (!v.header.equals(header)) {
					header = v.header;
					if (header.length() > 0) det.append("| **").append(header).append("** | | | | | |\n");
				}
				det.append("| `").append(v.name).append("` | ").append(type(v)).append(" | ").append(def(v)).append(" | ").append(range(v)).append(" | ").append(esc(v.unit)).append(" | ").append(esc(v.desc)).append(" |\n");
			}
			if (!m.events.isEmpty()) {
				det.append("\n**Events** (Events panel of the element)\n\n");
				for (BehaviorMeta.EventSpec e : m.events) det.append("- `").append(e.signature()).append("`\n");
			}
			det.append("\n**Blocks** (shown while the scene has an element with this behavior)\n\n| Block | Arguments | What it does |\n|---|---|---|\n");
			Method[] ms = c.getDeclaredMethods();
			java.util.Arrays.sort(ms, new java.util.Comparator<Method>() {
				public int compare(Method x, Method y) {
					return x.getName().compareTo(y.getName());
				}
			});
			for (Method me : ms) {
				Block b = me.getAnnotation(Block.class);
				if (b == null) continue;
				StringBuilder args = new StringBuilder();
				Class<?>[] pt = me.getParameterTypes();
				for (int i = 0; i < pt.length; i++) args.append(i > 0 ? ", " : "").append(b.args().length > i ? b.args()[i] : pt[i].getSimpleName()).append(" (").append(pt[i].getSimpleName()).append(")");
				String ret = me.getReturnType() == void.class ? "" : " → " + me.getReturnType().getSimpleName();
				det.append("| ").append(esc(b.label())).append(ret.length() > 0 ? " *(value)*" : "").append(" | ").append(args.length() == 0 ? "-" : args).append(" | ").append(esc(b.desc())).append(" |\n");
			}
			det.append("\n@@NOTES:").append(id).append("@@\n");
		}
		System.out.println("@@SUMMARY@@\n" + sum + "\n@@DETAILS@@" + det);
	}
}
