package com.star4droid.star2d.sheets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Cuts a sprite sheet into frames. Pure Java (no Android classes), so it is easy to test.
 * The picture is given as a mask: {@code opaque[y * width + x] != 0} where the pixel is not transparent.
 * A frame is {@code int[]{x, y, w, h}} in picture pixels.
 */
public final class SheetSlicer {
    private SheetSlicer() {}

    /** The part of the picture to cut (x, y, w, h); clamped to the picture. */
    public static int[] clampArea(int[] a, int width, int height) {
        int x = Math.max(0, Math.min(a[0], width - 1)), y = Math.max(0, Math.min(a[1], height - 1));
        int w = Math.max(1, Math.min(a[2], width - x)), h = Math.max(1, Math.min(a[3], height - y));
        return new int[]{x, y, w, h};
    }

    /** {@code cols x rows} equal cells inside the area, left to right, top to bottom. */
    public static ArrayList<int[]> grid(int[] area, int cols, int rows) {
        ArrayList<int[]> out = new ArrayList<>();
        cols = Math.max(1, cols);
        rows = Math.max(1, rows);
        int cw = area[2] / cols, ch = area[3] / rows;
        if (cw < 1 || ch < 1) return out;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) out.add(new int[]{area[0] + c * cw, area[1] + r * ch, cw, ch});
        return out;
    }

    /** Cells of a given size, with a margin around the sheet and a spacing between the cells. */
    public static ArrayList<int[]> cells(int[] area, int cw, int ch, int margin, int spacing) {
        ArrayList<int[]> out = new ArrayList<>();
        if (cw < 1 || ch < 1) return out;
        margin = Math.max(0, margin);
        spacing = Math.max(0, spacing);
        for (int y = area[1] + margin; y + ch <= area[1] + area[3] - margin + 0; y += ch + spacing)
            for (int x = area[0] + margin; x + cw <= area[0] + area[2] - margin; x += cw + spacing)
                out.add(new int[]{x, y, cw, ch});
        return out;
    }

    /** Removes the cells that have no opaque pixel at all. */
    public static ArrayList<int[]> skipEmpty(ArrayList<int[]> cells, byte[] opaque, int width) {
        ArrayList<int[]> out = new ArrayList<>();
        for (int[] c : cells) if (hasPixel(opaque, width, c)) out.add(c);
        return out;
    }

    private static boolean hasPixel(byte[] m, int width, int[] r) {
        for (int y = r[1]; y < r[1] + r[3]; y++) {
            int o = y * width;
            for (int x = r[0]; x < r[0] + r[2]; x++) if (m[o + x] != 0) return true;
        }
        return false;
    }

    /**
     * Finds the objects of the picture: groups of touching opaque pixels (8 neighbours), then joins boxes that are closer than
     * {@code gap} pixels, drops boxes smaller than {@code minSize}, and returns them in reading order (rows, then left to right).
     * One pass over the pixels (run based union-find): about O(pixels), no recursion.
     */
    public static ArrayList<int[]> auto(byte[] opaque, int width, int height, int[] area, int minSize, int gap, Progress progress) {
        area = clampArea(area, width, height);
        int ax = area[0], ay = area[1], aw = area[2], ah = area[3];
        // runs of the previous row and of this row: start, end(inclusive), label
        int[] ps = new int[aw / 2 + 2], pe = new int[aw / 2 + 2], pl = new int[aw / 2 + 2];
        int[] cs = new int[aw / 2 + 2], ce = new int[aw / 2 + 2], cl = new int[aw / 2 + 2];
        int pn = 0;
        int cap = 1024;
        int[] parent = new int[cap], minX = new int[cap], minY = new int[cap], maxX = new int[cap], maxY = new int[cap];
        int labels = 0;
        for (int y = 0; y < ah; y++) {
            if (progress != null && (y & 63) == 0) progress.on(y / (float) ah * 0.8f);
            int o = (ay + y) * width + ax;
            int cn = 0;
            int x = 0;
            while (x < aw) {
                if (opaque[o + x] == 0) { x++; continue; }
                int s = x;
                while (x < aw && opaque[o + x] != 0) x++;
                cs[cn] = s;
                ce[cn] = x - 1;
                cl[cn] = -1;
                cn++;
            }
            // join with the runs above (touching also diagonally)
            int j = 0;
            for (int i = 0; i < cn; i++) {
                while (j < pn && pe[j] < cs[i] - 1) j++;
                int k = j;
                int label = -1;
                while (k < pn && ps[k] <= ce[i] + 1) {
                    int root = find(parent, pl[k]);
                    if (label == -1) label = root;
                    else if (root != label) {
                        // union: keep the smaller index as root, merge boxes
                        int a = Math.min(label, root), b = Math.max(label, root);
                        parent[b] = a;
                        minX[a] = Math.min(minX[a], minX[b]); minY[a] = Math.min(minY[a], minY[b]);
                        maxX[a] = Math.max(maxX[a], maxX[b]); maxY[a] = Math.max(maxY[a], maxY[b]);
                        label = a;
                    }
                    k++;
                }
                if (k > 0) j = Math.max(j, k - 1); // the last run above may touch the next run too
                if (label == -1) {
                    if (labels == cap) {
                        cap *= 2;
                        parent = Arrays.copyOf(parent, cap); minX = Arrays.copyOf(minX, cap); minY = Arrays.copyOf(minY, cap);
                        maxX = Arrays.copyOf(maxX, cap); maxY = Arrays.copyOf(maxY, cap);
                    }
                    label = labels++;
                    parent[label] = label;
                    minX[label] = cs[i]; maxX[label] = ce[i]; minY[label] = y; maxY[label] = y;
                } else {
                    minX[label] = Math.min(minX[label], cs[i]); maxX[label] = Math.max(maxX[label], ce[i]);
                    minY[label] = Math.min(minY[label], y); maxY[label] = Math.max(maxY[label], y);
                }
                cl[i] = label;
            }
            // this row becomes the previous one
            int[] t;
            t = ps; ps = cs; cs = t;
            t = pe; pe = ce; ce = t;
            t = pl; pl = cl; cl = t;
            pn = cn;
            if (pn + 2 > ps.length) { // never happens (max runs = aw/2 + 1), kept for safety
                ps = Arrays.copyOf(ps, pn + 2); pe = Arrays.copyOf(pe, pn + 2); pl = Arrays.copyOf(pl, pn + 2);
                cs = Arrays.copyOf(cs, pn + 2); ce = Arrays.copyOf(ce, pn + 2); cl = Arrays.copyOf(cl, pn + 2);
            }
        }
        ArrayList<int[]> boxes = new ArrayList<>();
        for (int i = 0; i < labels; i++)
            if (find(parent, i) == i) boxes.add(new int[]{ax + minX[i], ay + minY[i], maxX[i] - minX[i] + 1, maxY[i] - minY[i] + 1});
        if (progress != null) progress.on(0.9f);
        boxes = mergeClose(boxes, Math.max(0, gap));
        ArrayList<int[]> out = new ArrayList<>();
        for (int[] b : boxes) if (b[2] >= minSize && b[3] >= minSize) out.add(b);
        readingOrder(out);
        if (progress != null) progress.on(1f);
        return out;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** Joins boxes that overlap or are closer than {@code gap} px (repeats until nothing changes). */
    static ArrayList<int[]> mergeClose(ArrayList<int[]> in, int gap) {
        ArrayList<int[]> boxes = new ArrayList<>(in);
        boolean changed = true;
        while (changed && boxes.size() > 1) {
            changed = false;
            Collections2.sortByX(boxes);
            for (int i = 0; i < boxes.size(); i++) {
                int[] a = boxes.get(i);
                if (a == null) continue;
                for (int j = i + 1; j < boxes.size(); j++) {
                    int[] b = boxes.get(j);
                    if (b == null) continue;
                    if (b[0] > a[0] + a[2] + gap) break; // sorted by x: nothing further can touch
                    if (near(a, b, gap)) {
                        int x1 = Math.min(a[0], b[0]), y1 = Math.min(a[1], b[1]);
                        int x2 = Math.max(a[0] + a[2], b[0] + b[2]), y2 = Math.max(a[1] + a[3], b[1] + b[3]);
                        a = new int[]{x1, y1, x2 - x1, y2 - y1};
                        boxes.set(i, a);
                        boxes.set(j, null);
                        changed = true;
                    }
                }
            }
            ArrayList<int[]> keep = new ArrayList<>();
            for (int[] b : boxes) if (b != null) keep.add(b);
            boxes = keep;
        }
        return boxes;
    }

    private static boolean near(int[] a, int[] b, int gap) {
        return a[0] <= b[0] + b[2] + gap && b[0] <= a[0] + a[2] + gap && a[1] <= b[1] + b[3] + gap && b[1] <= a[1] + a[3] + gap;
    }

    /** Rows from top to bottom (boxes that share a row overlap vertically), each row from left to right. */
    public static void readingOrder(ArrayList<int[]> boxes) {
        boxes.sort(new Comparator<int[]>() {
            @Override public int compare(int[] a, int[] b) { return Integer.compare(a[1], b[1]); }
        });
        ArrayList<ArrayList<int[]>> rows = new ArrayList<>();
        for (int[] b : boxes) {
            ArrayList<int[]> row = null;
            for (ArrayList<int[]> r : rows) {
                int[] first = r.get(0);
                int mid = b[1] + b[3] / 2;
                if (mid >= first[1] && mid <= first[1] + first[3]) { row = r; break; }
            }
            if (row == null) { row = new ArrayList<>(); rows.add(row); }
            row.add(b);
        }
        boxes.clear();
        for (ArrayList<int[]> r : rows) {
            r.sort(new Comparator<int[]>() {
                @Override public int compare(int[] a, int[] b) { return Integer.compare(a[0], b[0]); }
            });
            boxes.addAll(r);
        }
    }

    /** Progress 0..1 while an automatic cut runs (called from the worker thread). */
    public interface Progress { void on(float p); }

    private static final class Collections2 {
        static void sortByX(ArrayList<int[]> l) {
            l.sort(new Comparator<int[]>() {
                @Override public int compare(int[] a, int[] b) { return Integer.compare(a[0], b[0]); }
            });
        }
    }
}
