package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.template.Utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Frames of an animation file (anims/&lt;name&gt;) for the editor, so an element can play its "animation" property live.
 * A frame is an image, or a region [x, y, w, h] of a sprite sheet.
 */
final class EditorAnimation {
    final ArrayList<Drawable> frames = new ArrayList<>();
    float frameTime = 0.3f;
    String key = "";

    private static final HashMap<String, Texture> TEXTURES = new HashMap<>();

    static EditorAnimation load(LibgdxEditor editor, String rel) {
        if (rel == null || rel.trim().isEmpty() || editor == null || editor.getProject() == null) return null;
        try {
            String name = rel.trim().replace(Utils.seperator, "/").replaceFirst("^/+", "");
            FileHandle f = Gdx.files.absolute(editor.getProject().get("anims") + name);
            if (!f.exists() || f.isDirectory()) return null;
            ArrayList<HashMap<String, Object>> list = new Gson().fromJson(f.readString(), new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType());
            if (list == null || list.isEmpty()) return null;
            EditorAnimation a = new EditorAnimation();
            a.key = name + "|" + f.lastModified();
            try { a.frameTime = Float.parseFloat(list.get(0).get("dur").toString()) * 0.001f; } catch (Exception ignored) {}
            if (a.frameTime <= 0) a.frameTime = 0.3f;
            for (HashMap<String, Object> m : list) {
                String path = editor.getProject().getImagesPath() + m.get("name").toString().replace(Utils.seperator, "/");
                Texture tex = texture(editor, path);
                if (tex == null) continue;
                Object r = m.get("region");
                if (r instanceof List && ((List<?>) r).size() == 4) {
                    List<?> q = (List<?>) r;
                    a.frames.add(new TextureRegionDrawable(new TextureRegion(tex, ((Number) q.get(0)).intValue(), ((Number) q.get(1)).intValue(),
                            ((Number) q.get(2)).intValue(), ((Number) q.get(3)).intValue())));
                } else a.frames.add(new TextureRegionDrawable(new TextureRegion(tex)));
            }
            return a.frames.isEmpty() ? null : a;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Texture texture(LibgdxEditor editor, String path) {
        try {
            if (editor.getAssetLoader() != null && editor.getAssetLoader().isLoaded(path)) return editor.getAssetLoader().get(path);
        } catch (Throwable ignored) {}
        Texture t = TEXTURES.get(path);
        if (t == null) {
            FileHandle h = Gdx.files.absolute(path);
            if (!h.exists()) return null;
            try { t = new Texture(h); } catch (Throwable e) { return null; }
            TEXTURES.put(path, t);
        }
        return t;
    }

    Drawable at(float time) {
        int i = (int) (time / frameTime) % frames.size();
        return frames.get(Math.max(0, i));
    }
}
