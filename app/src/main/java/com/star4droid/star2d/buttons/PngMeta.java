package com.star4droid.star2d.buttons;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Reads/writes one ASCII "tEXt" chunk of a PNG. It is how a saved button keeps the settings it was made with
 * (so it can be edited again): decoders, LibGDX and the exporter ignore the chunk, so no extra file is needed.
 */
public final class PngMeta {
    private PngMeta() {}

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** Returns the PNG with a tEXt chunk {@code key}={@code text} right after IHDR (an existing chunk with that key is dropped). Null-safe: returns the input when it is not a PNG. */
    public static byte[] withText(byte[] png, String key, String text) {
        if (png == null || png.length < 33 || !startsWithSignature(png)) return png;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + text.length() + 32);
            out.write(png, 0, 8);
            int pos = 8;
            boolean inserted = false;
            while (pos + 12 <= png.length) {
                int len = readInt(png, pos);
                String type = new String(png, pos + 4, 4, StandardCharsets.ISO_8859_1);
                int total = 12 + len;
                if (len < 0 || pos + total > png.length) break;
                boolean ours = type.equals("tEXt") && keyOf(png, pos + 8, len).equals(key);
                if (!ours) out.write(png, pos, total);
                if (!inserted && type.equals("IHDR")) {
                    writeChunk(out, "tEXt", join(key, text));
                    inserted = true;
                }
                pos += total;
                if (type.equals("IEND")) break;
            }
            return out.toByteArray();
        } catch (Exception e) {
            return png;
        }
    }

    /** The text of the tEXt chunk {@code key}, or null. */
    public static String readText(byte[] png, String key) {
        if (png == null || png.length < 20 || !startsWithSignature(png)) return null;
        int pos = 8;
        while (pos + 12 <= png.length) {
            int len = readInt(png, pos);
            if (len < 0 || pos + 12 + len > png.length) return null;
            String type = new String(png, pos + 4, 4, StandardCharsets.ISO_8859_1);
            if (type.equals("tEXt") && keyOf(png, pos + 8, len).equals(key)) {
                int k = pos + 8;
                int z = k;
                while (z < pos + 8 + len && png[z] != 0) z++;
                return new String(png, z + 1, pos + 8 + len - (z + 1), StandardCharsets.ISO_8859_1);
            }
            // our chunk sits right after IHDR, before the image data
            if (type.equals("IDAT") || type.equals("IEND")) return null;
            pos += 12 + len;
        }
        return null;
    }

    public static String readText(File f, String key) {
        try (FileInputStream in = new FileInputStream(f)) {
            // the chunk is within the first few hundred bytes after IHDR: no need to read the whole image
            byte[] head = new byte[4096];
            int n = in.read(head);
            if (n <= 0) return null;
            byte[] part = new byte[n];
            System.arraycopy(head, 0, part, 0, n);
            String t = readTextPrefix(part, key);
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    /** Like readText but tolerates a truncated buffer (stops at the first incomplete chunk). */
    private static String readTextPrefix(byte[] png, String key) {
        if (png.length < 20 || !startsWithSignature(png)) return null;
        int pos = 8;
        while (pos + 12 <= png.length) {
            int len = readInt(png, pos);
            if (len < 0) return null;
            String type = new String(png, pos + 4, 4, StandardCharsets.ISO_8859_1);
            if (type.equals("IDAT")) return null;
            if (pos + 12 + len > png.length) return null;
            if (type.equals("tEXt") && keyOf(png, pos + 8, len).equals(key)) {
                int z = pos + 8;
                while (z < pos + 8 + len && png[z] != 0) z++;
                return new String(png, z + 1, pos + 8 + len - (z + 1), StandardCharsets.ISO_8859_1);
            }
            pos += 12 + len;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ internals

    private static byte[] join(String key, String text) {
        byte[] k = key.getBytes(StandardCharsets.ISO_8859_1), t = text.getBytes(StandardCharsets.ISO_8859_1);
        byte[] d = new byte[k.length + 1 + t.length];
        System.arraycopy(k, 0, d, 0, k.length);
        d[k.length] = 0;
        System.arraycopy(t, 0, d, k.length + 1, t.length);
        return d;
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        byte[] tb = type.getBytes(StandardCharsets.ISO_8859_1);
        out.write(intBytes(data.length));
        out.write(tb);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(tb);
        crc.update(data);
        out.write(intBytes((int) crc.getValue()));
    }

    private static String keyOf(byte[] b, int off, int len) {
        int z = off;
        while (z < off + len && b[z] != 0) z++;
        return new String(b, off, z - off, StandardCharsets.ISO_8859_1);
    }

    private static boolean startsWithSignature(byte[] b) {
        for (int i = 0; i < 8; i++) if (b[i] != SIGNATURE[i]) return false;
        return true;
    }

    private static int readInt(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static byte[] intBytes(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }
}
