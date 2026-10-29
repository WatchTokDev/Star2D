import com.star4droid.template.ui.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Offline reference renderer for .ui files (docs/UI.md): lays the tree out with the real UILayout and paints the widgets with the
 * real UIArt / UIShapes pictures plus AWT text. Used by tools/test_ui.py to check layout math and to look at the result.
 *   java UiPreview in.ui out.png [width height]
 */
public class UiPreview {
    static UITheme theme;
    static Graphics2D g;
    static final Font BASE = new Font(Font.SANS_SERIF, Font.BOLD, 20);

    static Font font(float size) {
        return BASE.deriveFont(size);
    }

    static final UILayout.Measurer M = new UILayout.Measurer() {
        @Override
        public float[] text(String text, float fs, float wrap, UINode node) {
            BufferedImage bi = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = bi.createGraphics();
            gg.setFont(font(fs));
            FontMetrics fm = gg.getFontMetrics();
            float w = 0;
            int lines = 0;
            for (String line : text.split("\n", -1)) {
                if (wrap > 0) {
                    StringBuilder cur = new StringBuilder();
                    for (String word : line.split(" ")) {
                        String t = cur.length() == 0 ? word : cur + " " + word;
                        if (fm.stringWidth(t) > wrap && cur.length() > 0) {
                            w = Math.max(w, fm.stringWidth(cur.toString()));
                            lines++;
                            cur = new StringBuilder(word);
                        } else cur = new StringBuilder(t);
                    }
                    w = Math.max(w, fm.stringWidth(cur.toString()));
                    lines++;
                } else {
                    w = Math.max(w, fm.stringWidth(line));
                    lines++;
                }
            }
            gg.dispose();
            return new float[]{w, lines * fm.getHeight()};
        }

        @Override
        public float[] image(String path) {
            return null;
        }
    };

    static Color col(int rgba) {
        return new Color((rgba >>> 24) & 255, (rgba >>> 16) & 255, (rgba >>> 8) & 255, rgba & 255);
    }

    static void art(UIArt.Art a, float x, float y) {
        int[] px = a.render();
        BufferedImage bi = new BufferedImage(a.w, a.h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            bi.setRGB(i % a.w, i / a.w, ((c & 255) << 24) | (((c >>> 24) & 255) << 16) | (((c >>> 16) & 255) << 8) | ((c >>> 8) & 255));
        }
        g.drawImage(bi, Math.round(x - a.margin), Math.round(y - a.margin), null);
    }

    static void text(String t, float x, float y, float w, float h, float fs, int color, String align) {
        g.setFont(font(fs));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(col(color));
        String[] lines = t.split("\n", -1);
        float total = lines.length * fm.getHeight();
        float ty = y + (h - total) / 2f + fm.getAscent();
        for (String l : lines) {
            float tw = fm.stringWidth(l);
            float tx = align.equals("center") ? x + (w - tw) / 2 : align.equals("right") ? x + w - tw : x;
            g.drawString(l, tx, ty);
            ty += fm.getHeight();
        }
    }

    static void draw(UINode n, float px, float py, UINode parent, float offH) {
        if (!n.visible) return;
        float x = px + n.rx, y = py + (parent == null ? 0 : offH - n.ry - n.rh);
        float w = n.rw, h = n.rh;
        Composite old = g.getComposite();
        if (n.alpha < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, n.alpha));
        UIStyles.Spec sp = UIStyles.spec(n, theme);
        float fs = n.num("fontSize", theme.number("fontSize"));
        int tc = theme.resolveColor(n.str("color", ""), theme.color("text"));
        switch (n.type) {
            case UINode.PANEL: art(UIArt.panel(n, theme, w, h), x, y); break;
            case UINode.STACK: case UINode.LINEAR: case UINode.GRID: case UINode.SCROLL:
                if (n.has("bgColor")) art(UIArt.plain(theme.resolveColor(n.str("bgColor", ""), 0), 0, w, h), x, y);
                break;
            case UINode.LABEL: text(n.str("text", ""), x, y, w, h, fs, tc, n.str("textAlign", "left")); break;
            case UINode.BUTTON: {
                art(UIArt.button(n, theme, w, h, false), x, y);
                text(n.str("text", ""), x, y, w, h, fs, UIArt.buttonTextColor(n, theme), "center");
                break;
            }
            case UINode.IMAGE: case UINode.ICON:
                g.setColor(new Color(255, 255, 255, 60));
                g.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
                g.setColor(col(theme.color("textDim")));
                g.drawRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
                g.drawLine(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
                break;
            case UINode.PROGRESS: case UINode.SLIDER: {
                boolean vert = n.bool("vertical", false);
                float val = n.num("value", 0), mn = n.num("min", 0), mx = n.num("max", n.type.equals(UINode.SLIDER) ? 1 : 100);
                float f = mx > mn ? Math.max(0, Math.min(1, (val - mn) / (mx - mn))) : 0;
                float th = UIArt.trackThickness(n, theme, h);
                float len = vert ? h : w;
                if (n.type.equals(UINode.SLIDER)) len -= 0; // knob overhang is inside the box
                float tx = vert ? x + (w - th) / 2 : x, ty = vert ? y : y + (h - th) / 2;
                UIArt.Art tr = UIArt.track(n, theme, len, th, vert);
                if (UIArt.trackHasSegments(n, theme)) tr = tr; // segments are on the fill
                art(tr, tx, ty);
                UIStyles.Spec s2 = UIStyles.spec(n, theme);
                float bw = s2.f("trackBorder", 0);
                UIArt.Art fl = UIArt.fill(n, theme, len, th, vert);
                Shape oldClip = g.getClip();
                float fx = tx + (vert ? bw : bw), fy = ty + bw;
                if (vert) g.clipRect(Math.round(tx), Math.round(ty + len - len * f), Math.round(th), Math.round(len * f + 1));
                else g.clipRect(Math.round(tx), Math.round(ty), Math.round(len * f), Math.round(th));
                art(fl, fx, fy);
                g.setClip(oldClip);
                if (n.type.equals(UINode.SLIDER)) {
                    UIArt.Art kn = UIArt.knob(n, theme);
                    float kw = kn.drawW - 2 * kn.margin, kh = kn.drawH - 2 * kn.margin;
                    float kx = vert ? x + (w - kw) / 2 : x + kw / 2 + (len - kw) * f - kw / 2, ky = vert ? y + len - kh / 2 - (len - kh) * f - kh / 2 : y + (h - kh) / 2;
                    art(kn, kx, ky);
                    if (n.bool("showValueLabel", false)) text(String.format(Locale.ROOT, "%." + n.integer("decimals", 0) + "f", val), kx - 20, ky - fs * 1.2f, kw + 40, fs, fs * 0.7f, tc, "center");
                } else if (n.bool("showText", false)) {
                    String fmt = n.str("textFormat", "percent");
                    String t = fmt.equals("value") ? String.valueOf(Math.round(val)) : fmt.equals("fraction") ? Math.round(val) + "/" + Math.round(mx) : Math.round(f * 100) + "%";
                    text(t, x, y, w, h, Math.min(fs, th * 0.8f), tc, "center");
                }
                break;
            }
            case UINode.CHECKBOX: case UINode.RADIO: {
                float box = n.num("boxSize", Math.max(32, fs * 1.2f));
                art(UIArt.check(n, theme, box, n.bool("checked", false)), x, y + (h - box) / 2);
                text(n.str("text", ""), x + box + 12, y, w - box - 12, h, fs, tc, "left");
                break;
            }
            case UINode.SWITCH: {
                boolean on = n.bool("checked", false);
                art(UIArt.switchTrack(n, theme, w, h, on), x, y);
                UIArt.Art k = UIArt.switchKnob(n, theme, h);
                float d = k.drawW - 2 * k.margin;
                float kx = on ? x + w - d - (h - d) / 2 : x + (h - d) / 2;
                art(k, kx, y + (h - d) / 2);
                break;
            }
            case UINode.TEXTFIELD: {
                art(UIArt.textField(n, theme, w, h, false), x, y);
                String t = n.str("text", "");
                if (t.isEmpty()) text(n.str("hint", ""), x + 16, y, w - 32, h, fs, theme.color("textDim"), "left");
                else text(t, x + 16, y, w - 32, h, fs, tc, "left");
                break;
            }
            default: break;
        }
        // children
        if (n.type.equals(UINode.SCROLL)) {
            Shape oc = g.getClip();
            g.clipRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
            for (UINode c : n.children) draw(c, x, y, n, n.contentH);
            g.setClip(oc);
        } else if (n.isContainer()) {
            boolean clip = n.bool("clip", false);
            Shape oc = g.getClip();
            if (clip) g.clipRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
            for (UINode c : n.children) draw(c, x, y, n, n.rh);
            g.setClip(oc);
        }
        g.setComposite(old);
    }

    public static void main(String[] a) throws Exception {
        UIDocument doc = UIDocument.read(new File(a[0]));
        int w = a.length > 2 ? Integer.parseInt(a[2]) : Math.round(doc.designWidth), h = a.length > 3 ? Integer.parseInt(a[3]) : Math.round(doc.designHeight);
        UIArt.EXACT = true;
        theme = doc.theme;
        for (String s : doc.warnings) System.out.println("warning: " + s);
        UILayout.layout(doc.root, theme, w, h, M);
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // checkerboard-ish dark game background so transparent UI is visible
        g.setColor(new Color(40, 44, 58));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(48, 53, 70));
        for (int y = 0; y < h; y += 40) for (int x = (y / 40 % 2) * 40; x < w; x += 80) g.fillRect(x, y, 40, 40);
        draw(doc.root, 0, 0, null, 0);
        ImageIO.write(bi, "png", new File(a[1]));
        if (a.length > 4) { // dump layout
            doc.root.walk(n -> System.out.println(n.id + " " + n.type + " " + n.rx + "," + n.ry + " " + n.rw + "x" + n.rh));
        }
    }
}
