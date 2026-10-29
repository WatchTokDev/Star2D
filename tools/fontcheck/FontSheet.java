import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import com.star4droid.star2d.fonts.FontPresets;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Offline check of assets/fonts/presets.json: parses every preset with the real FontPresets code, then renders a contact sheet with the
 * real FreeType (desktop natives) using the exact same parameter mapping the editor/game uses. No GL needed (PixmapPacker pages are Pixmaps).
 * Usage: FontSheet <presets.json> <assets/files dir> <out.png> [sample text]
 */
public class FontSheet {
    static void draw(Pixmap dst, FreeTypeFontGenerator.FreeTypeBitmapFontData data, PixmapPacker packer, String text, int x, int baseline) {
        int pen = x;
        for (char c : text.toCharArray()) {
            BitmapFont.Glyph g = data.getGlyph(c);
            if (g == null) continue;
            if (g.width > 0 && g.height > 0) {
                Pixmap page = packer.getPages().get(g.page).getPixmap();
                int top = baseline - g.yoffset - g.height;
                dst.drawPixmap(page, g.srcX, g.srcY, g.width, g.height, pen + g.xoffset, top, g.width, g.height);
            }
            pen += g.xadvance;
        }
    }

    static int width(FreeTypeFontGenerator.FreeTypeBitmapFontData data, String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            BitmapFont.Glyph g = data.getGlyph(c);
            if (g != null) w += g.xadvance;
        }
        return w;
    }

    public static void main(String[] a) throws Exception {
        new SharedLibraryLoader().load("gdx");
        String json = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        File files = new File(a[1]);
        String sample = a.length > 3 ? a[3] : "Aa Bb Cc 0123";
        List<FontPresets.Preset> presets = FontPresets.parse(json);
        HashSet<String> ids = new HashSet<>();
        int cols = 2, cellW = 560, cellH = 150;
        int rows = (presets.size() + cols - 1) / cols;
        Pixmap sheet = new Pixmap(cols * cellW, rows * cellH, Pixmap.Format.RGBA8888);
        for (int y = 0; y < sheet.getHeight(); y++)
            for (int x = 0; x < sheet.getWidth(); x++)
                sheet.drawPixel(x, y, ((x / 20 + y / 20) % 2 == 0) ? 0x4A4F57FF : 0x42464EFF);
        FreeTypeFontGenerator labelGen = new FreeTypeFontGenerator(new FileHandle(new File(files, "DroidSans.ttf")));
        int i = 0;
        for (FontPresets.Preset p : presets) {
            if (!ids.add(p.id)) throw new IllegalStateException("duplicate preset id " + p.id);
            File ttf = new File(files, p.ttf);
            if (!ttf.exists()) throw new IllegalStateException("preset " + p.id + ": missing ttf " + p.ttf);
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(new FileHandle(ttf));
            PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, false);
            FreeTypeFontGenerator.FreeTypeFontParameter fp = FontPresets.parameter(p.params, 1f);
            fp.characters = sample;
            fp.packer = packer;
            FreeTypeFontGenerator.FreeTypeBitmapFontData data = gen.generateData(fp);
            int cx = (i % cols) * cellW, cy = (i / cols) * cellH;
            int w = width(data, sample);
            int base = cy + cellH / 2 + (int) (fp.size * 0.35f);
            draw(sheet, data, packer, sample, cx + Math.max(8, (cellW - w) / 2), base);
            // label
            PixmapPacker lp = new PixmapPacker(256, 256, Pixmap.Format.RGBA8888, 1, false);
            FreeTypeFontGenerator.FreeTypeFontParameter lf = new FreeTypeFontGenerator.FreeTypeFontParameter();
            lf.size = 14;
            lf.characters = p.id + " ";
            lf.packer = lp;
            FreeTypeFontGenerator.FreeTypeBitmapFontData ld = labelGen.generateData(lf);
            draw(sheet, ld, lp, p.id, cx + 6, cy + 16);
            System.out.println("OK " + p.id + " size=" + fp.size + " border=" + fp.borderWidth + " shadow=" + fp.shadowOffsetX + "," + fp.shadowOffsetY + " mono=" + fp.mono);
            gen.dispose();
            i++;
        }
        PixmapIO.writePNG(new FileHandle(new File(a[2])), sheet);
        System.out.println("presets=" + presets.size() + " sheet=" + a[2]);
    }
}
