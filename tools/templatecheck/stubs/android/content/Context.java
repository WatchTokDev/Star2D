package android.content;
public abstract class Context {
    public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
    public java.io.File getExternalFilesDir(String type) { return null; }
}
