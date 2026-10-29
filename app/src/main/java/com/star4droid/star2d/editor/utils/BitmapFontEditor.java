package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.FilePicker;
import com.star4droid.star2d.ui.Tr;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The bitmap font (.s2df) editor: a full-screen LibGDX dialog around {@link FontMakerForm} (the screen itself) that does the loading and
 * saving of the .s2df file. While it is open the Android editor shell (toolbars, panels) is hidden so the tool gets the whole screen; the shell
 * comes back whenever this dialog leaves the stage, however that happens (Save, Cancel, Back key, stage cleared) -
 * see {@link TestApp.NativeHooks#onFullscreenTool}.
 *
 * Usage (unchanged): getBitmapFontEditor().setData(dir, fileOrNull).setOnSave(...).setTTF("/x.ttf").show(stage).
 * Output format (unchanged): a flat JSON object {name, rtl, font, and every FreeTypeFontParameter field as a string}.
 */
public class BitmapFontEditor extends VisDialog {
	private final TestApp app;
	private final FontMakerForm.Env env;
	private final FontMakerForm form;
	private FileHandle curDir, curFile;
	private PropertySet<String,Object> propertySet = null;
	private boolean shellHidden = false;
	OnSave onSave;

	public BitmapFontEditor(TestApp app){
		super(Lang.getTrans("bitmapFontEditor"));
		this.app = app;
		this.env = new AppEnv();
		this.form = new FontMakerForm(env);
		form.onSave = this::save;
		form.onCancel = this::hide;
		setMovable(false);
		setResizable(false);
		setModal(true);
		pad(0);
		setBackground((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null);
		try {
			getTitleTable().setVisible(false);
			getTitleLabel().setText("");
			getButtonsTable().setVisible(false);
			getContentTable().pad(0);
		} catch(Throwable ignored){}
		getContentTable().add(form).grow();
		for(Cell c:getCells()) c.space(0).pad(0);
	}

	/*
	* dir = directory to save the file (no need for it if the filehandle exists)
	* filehandle = full filepath (null means the user should enter file name)
	*/
	public BitmapFontEditor setData(FileHandle dir,FileHandle fileHandle){
		curDir = dir;
		curFile = fileHandle;
		onSave = null; // each opening sets its own callback (it used to leak into the next opening)
		propertySet = null;
		FileHandle defaultFont = Gdx.files.absolute(app.getEditor().getProject().getPath()+"/DroidSans.ttf");
		if(!defaultFont.exists()){
			try { Gdx.files.internal("files/DroidSans.ttf").copyTo(defaultFont); } catch(Throwable ignored){}
		}
		try {
			if(fileHandle!=null && fileHandle.exists())
				propertySet = PropertySet.getFrom(fileHandle.readString());
		} catch(Exception e){}
		String newName = "font";
		int n = 1;
		while(dir!=null && dir.child(newName+n+".s2df").exists())
			n++;
		newName = newName+n;
		final String root = dir != null ? dir.file().getAbsolutePath() : (fileHandle != null ? fileHandle.parent().file().getAbsolutePath() : app.getEditor().getProject().getPath());
		form.load(fileHandle!=null ? fileHandle.nameWithoutExtension() : newName, fileHandle!=null, root, propertySet);
		return this;
	}

	public BitmapFontEditor setTTF(String name){
		form.setTtf(name);
		return this;
	}

	public BitmapFontEditor setOnSave(OnSave save){
		this.onSave = save;
		return this;
	}

	// ------------------------------------------------------------- save

	private void toast(String key,String def){
		app.toast(Tr.t(key,def));
	}

	private void save(){
		final String name = form.name().trim();
		final String ttf = form.ttf();
		if(name.isEmpty() || ttf.isEmpty()){
			toast("font_err_empty","The file name and the font file can't be empty");
			return;
		}
		if(name.contains("/") || name.contains("\\") || name.startsWith(".")){
			toast("font_err_name","The file name can't contain / or \\");
			return;
		}
		if(propertySet==null)
			propertySet = new PropertySet<>();
		propertySet.put("name",name);
		propertySet.put("rtl",String.valueOf(form.rtl()));
		propertySet.put("font",ttf);
		for(Map.Entry<String,String> e:form.params().entrySet())
			propertySet.put(e.getKey(),e.getValue());
		try {
			if(curFile!=null){
				curFile.writeString(propertySet.toString(),false);
				app.updateFont(curFile.path());
				app.toast(Tr.t("font_saved","Saved to")+":\n"+curFile.name());
				app.getEditor().updateChilds();
				if(onSave!=null)
					onSave.save(curFile.name(),propertySet);
			} else {
				FileHandle path = curDir.child(name+".s2df");
				if(path.exists()){
					toast("font_err_exists","There's already a file with this name");
					return;
				}
				path.writeString(propertySet.toString(),false);
				app.getEditor().updateChilds();
				app.toast(Tr.t("font_saved","Saved to")+":\n"+path.name());
				app.getFileBrowser().refreshFileList();
				if(onSave!=null)
					onSave.save(name+".s2df",propertySet);
			}
		} catch(Exception e){
			app.toast(Tr.t("font_err_save","Could not save the font")+": "+e);
			return;
		}
		hide();
	}

	// ------------------------------------------------------------- show / hide (full screen, shell hidden meanwhile)

	@Override
	public VisDialog show(Stage stage) {
		super.show(stage, null); // no fade: the editor shell is hidden while we are open
		padTop(0);
		setBounds(0,0,stage.getWidth(),stage.getHeight());
		toFront();
		return this;
	}

	/** Closes at once (no fade-out) so the editor comes back immediately. */
	@Override
	public void hide() {
		super.hide((Action) null);
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		Stage st = getStage();
		if(st != null && (getWidth() != st.getWidth() || getHeight() != st.getHeight() || getX() != 0 || getY() != 0))
			setBounds(0,0,st.getWidth(),st.getHeight());
	}

	@Override
	protected void setStage(Stage stage) {
		boolean was = getStage() != null;
		super.setStage(stage);
		boolean now = getStage() != null;
		if(now && !was) notifyShell(true);
		else if(!now && was) {
			try { form.dispose(); } catch(Throwable t){ Gdx.app.log("star2d_fonts","dispose : "+t); }
			notifyShell(false);
		}
	}

	/** Tells the Android shell to hide (open) or show (closed) its chrome. Idempotent. */
	private void notifyShell(boolean open){
		if(shellHidden == open) return;
		shellHidden = open;
		TestApp.NativeHooks hooks = TestApp.nativeHooks;
		if(hooks == null) return;
		try {
			hooks.onFullscreenTool(open, open ? this::closeFromShell : null);
		} catch(Throwable t){
			Gdx.app.log("star2d_fonts","shell hook : "+t);
		}
	}

	/** Back key (called by the shell on the GL thread): close the topmost popup / picker first, then the editor itself. */
	private void closeFromShell(){
		try {
			if(form.closePopup()) return;
			if(closeOverlayAbove()) return;
		} catch(Throwable ignored){}
		if(getStage() != null) hide();
		else {
			// not on a stage any more: make sure the shell is not left hidden
			shellHidden = true;
			notifyShell(false);
		}
	}

	/** A colour picker / number pad / file picker opened from the form and still on top. */
	private boolean closeOverlayAbove(){
		Stage st = getStage();
		if(st == null) return false;
		Array<Actor> actors = st.getActors();
		for(int i = actors.size - 1; i >= 0; i--){
			Actor a = actors.get(i);
			if(a == this) return false;
			if(!a.isVisible()) continue;
			if(a instanceof ColorPicker){ a.remove(); return true; }
			if(a instanceof VisDialog){ ((VisDialog)a).hide(); return true; }
			if(a instanceof FilePicker){ a.setVisible(false); return true; }
			if(a instanceof VisWindow){ a.remove(); return true; }
		}
		return false;
	}

	// ------------------------------------------------------------- what the form needs from the editor

	private final class AppEnv implements FontMakerForm.Env {
		@Override public boolean isRtl(){
			try { return Lang.isRTL(); } catch(Throwable t){ return false; }
		}
		@Override public void toast(String message){
			app.toast(message);
		}
		@Override public ColorPicker colorPicker(){
			return app.getControlLayer().getColorPicker();
		}
		@Override public void pickTtf(String root, Consumer<String> onPick){
			app.getEditor().getFilePicker(true).setRoot(root).setExtensions("ttf","otf").setOnPick((fhandle,path)-> onPick.accept(path));
		}
		@Override public void details(String key, String fallback){
			String details = Lang.getTrans(key+"Details");
			if(details == null || details.equals(key+"Details")) details = fallback;
			if(details == null || details.isEmpty()) return;
			app.getSimpleNote().setMessage(details).show(app.getUiStage());
		}
	}

	public interface OnSave {
		public void save(String s2fName,PropertySet<String,Object> propertySet);
	}
}
