import com.star4droid.star2d.sheets.SheetSlicer;
import java.util.*;

public class SheetSlicerTest {
    static int fails = 0;
    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL " + msg); } else System.out.println("ok   " + msg); }
    static void fill(byte[] m, int w, int x, int y, int bw, int bh) { for (int j = y; j < y + bh; j++) for (int i = x; i < x + bw; i++) m[j * w + i] = 1; }

    public static void main(String[] a) {
        int W = 200, H = 100;
        byte[] m = new byte[W * H];
        // grid
        ArrayList<int[]> g = SheetSlicer.grid(new int[]{0, 0, 200, 100}, 4, 2);
        check(g.size() == 8 && g.get(5)[0] == 50 && g.get(5)[1] == 50, "grid 4x2 -> 8 cells, #5 at 50,50");
        ArrayList<int[]> c = SheetSlicer.cells(new int[]{0, 0, 200, 100}, 32, 32, 2, 4);
        check(c.size() == 5 * 2, "cells 32x32 margin 2 spacing 4 -> 10 (got " + c.size() + ")");
        // skip empty
        fill(m, W, 0, 0, 10, 10);
        check(SheetSlicer.skipEmpty(SheetSlicer.grid(new int[]{0, 0, 200, 100}, 4, 2), m, W).size() == 1, "skipEmpty keeps only the cell with pixels");
        // auto: three blobs, one with a hole, two touching diagonally, one noise pixel
        m = new byte[W * H];
        fill(m, W, 10, 10, 20, 20);
        fill(m, W, 15, 15, 5, 5); // fine
        for (int j = 15; j < 20; j++) for (int i = 15; i < 20; i++) m[j * W + i] = 0; // hole
        fill(m, W, 60, 10, 10, 10); fill(m, W, 70, 20, 10, 10); // diagonal touch -> one object
        m[5 * W + 150] = 1; // noise
        fill(m, W, 100, 60, 30, 30);
        ArrayList<int[]> r = SheetSlicer.auto(m, W, H, new int[]{0, 0, W, H}, 4, 0, null);
        check(r.size() == 3, "auto finds 3 objects (got " + r.size() + ")");
        check(Arrays.equals(r.get(0), new int[]{10, 10, 20, 20}), "first = the square with a hole");
        check(Arrays.equals(r.get(1), new int[]{60, 10, 20, 20}), "second = diagonal pair merged");
        check(Arrays.equals(r.get(2), new int[]{100, 60, 30, 30}), "third = lower block (reading order)");
        // gap merge: two parts of a character 3px apart
        m = new byte[W * H];
        fill(m, W, 10, 10, 10, 10); fill(m, W, 23, 10, 10, 10);
        check(SheetSlicer.auto(m, W, H, new int[]{0, 0, W, H}, 1, 0, null).size() == 2, "gap 0 keeps them apart");
        check(SheetSlicer.auto(m, W, H, new int[]{0, 0, W, H}, 1, 4, null).size() == 1, "gap 4 joins them");
        // U shape that is connected only through the bottom row (union of labels)
        m = new byte[W * H];
        fill(m, W, 10, 10, 3, 20); fill(m, W, 30, 10, 3, 20); fill(m, W, 10, 30, 23, 3);
        ArrayList<int[]> u = SheetSlicer.auto(m, W, H, new int[]{0, 0, W, H}, 1, 0, null);
        check(u.size() == 1 && Arrays.equals(u.get(0), new int[]{10, 10, 23, 23}), "U shape = one object");
        // area limits the cut
        m = new byte[W * H];
        fill(m, W, 10, 10, 10, 10); fill(m, W, 150, 50, 10, 10);
        check(SheetSlicer.auto(m, W, H, new int[]{100, 0, 100, 100}, 1, 0, null).size() == 1, "area: only the right half is cut");
        // rows: two rows of 3
        m = new byte[W * H];
        for (int i = 0; i < 3; i++) { fill(m, W, 10 + i * 40, 10, 20, 20); fill(m, W, 10 + i * 40, 60, 20, 20); }
        ArrayList<int[]> rr = SheetSlicer.auto(m, W, H, new int[]{0, 0, W, H}, 1, 0, null);
        check(rr.size() == 6 && rr.get(3)[1] == 60 && rr.get(3)[0] == 10, "reading order: row 1 then row 2");
        // performance: 4096 x 4096 with a 40 x 40 grid of sprites
        int N = 4096;
        byte[] big = new byte[N * N];
        Random rnd = new Random(1);
        for (int gy = 0; gy < 40; gy++) for (int gx = 0; gx < 40; gx++) {
            int x0 = gx * 100 + 10, y0 = gy * 100 + 10;
            for (int k = 0; k < 6; k++) fill(big, N, x0 + rnd.nextInt(30), y0 + rnd.nextInt(30), 10 + rnd.nextInt(30), 10 + rnd.nextInt(30));
        }
        long t0 = System.nanoTime();
        ArrayList<int[]> res = SheetSlicer.auto(big, N, N, new int[]{0, 0, N, N}, 4, 15, null);
        long ms = (System.nanoTime() - t0) / 1000000;
        System.out.println("4096x4096, 1600 sprites: " + res.size() + " objects in " + ms + " ms");
        check(res.size() == 1600, "perf image finds 1600 objects");
        check(ms < 1500, "4096x4096 under 1.5 s (" + ms + " ms)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
