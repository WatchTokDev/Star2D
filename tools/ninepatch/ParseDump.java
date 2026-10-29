import com.star4droid.template.Utils.NinePatchParser;
import java.io.*;
import java.util.*;

/** Reads "W H" then H lines of W RGBA8888 ints (decimal, unsigned) from a file and prints NinePatchParser.parse(...). Used by tools/test_ninepatch.py. */
public class ParseDump {
    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(a[0]));
        StringTokenizer t = new StringTokenizer(r.readLine());
        final int w = Integer.parseInt(t.nextToken()), h = Integer.parseInt(t.nextToken());
        final int[][] px = new int[h][w];
        for (int y = 0; y < h; y++) {
            t = new StringTokenizer(r.readLine());
            for (int x = 0; x < w; x++) px[y][x] = (int) Long.parseLong(t.nextToken());
        }
        NinePatchParser.Parsed p = NinePatchParser.parse(w, h, (x, y) -> px[y][x]);
        System.out.println(p.valid + " " + p.contentW + " " + p.contentH + " " + p.left + " " + p.right + " " + p.top + " " + p.bottom + " "
                + p.padLeft + " " + p.padRight + " " + p.padTop + " " + p.padBottom + " " + p.markedX + " " + p.markedY);
    }
}
