package com.star4droid.template.Items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.star2d.ElementDefs.UIHolderDef;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.ui.UIEvents;
import com.star4droid.template.ui.UIListeners;
import com.star4droid.template.ui.UINodeRef;
import com.star4droid.template.ui.UIView;

/**
 * Shows a .ui file (made with the UI Maker) as one element. Its inner elements are not scene elements: find them by id with
 * {@link #ui()} ({@code hud.ui().setText("score", "10")}). Only the holder itself catches no touch; the inner buttons do.
 *
 * Size: {@code width x height} px of the element. In a UI element (type UI) one px is one screen px, in a world element (type STATIC)
 * the view is scaled by WORLD_SCALE like every other element.
 */
public class UIHolder extends Group implements PlayerItem {
	StageImp stage;
	ElementEvent elementEvent;
	UIHolderDef holderDef;
	ChildsHolder childsHolder = new ChildsHolder(this);
	ItemScript itemScript;
	UIView view;
	private final UIListeners listeners = new UIListeners();
	private float scaleFactor = 1f;
	
	public UIHolder(StageImp stageImp){
		super();
		this.stage = stageImp;
		setTouchable(Touchable.childrenOnly);
	}
	
	public static UIHolder create(StageImp stageImp, UIHolderDef def, ElementEvent elementEvent){
		return new UIHolder(stageImp).setElementEvent(elementEvent).setDef(def);
	}
	
	public UIHolder setDef(UIHolderDef def){
		this.holderDef = def;
		setup();
		return this;
	}
	
	public UIHolderDef getUIHolderDef(){
		return holderDef;
	}
	
	private String projectPath(){
		try { return stage.project.getPath(); } catch(Throwable t){ return ""; }
	}
	
	private void setup(){
		if(holderDef == null) return;
		boolean ui = "UI".equals(holderDef.type);
		scaleFactor = ui ? 1f : StageImp.WORLD_SCALE;
		setName(holderDef.name);
		if(applyScreenLayout()){
			// size and position come from the screen box
		} else {
			setSize(scaleFactor * holderDef.width, scaleFactor * holderDef.height);
			setOrigin(getWidth() * 0.5f, getHeight() * 0.5f);
			setPosition(scaleFactor * holderDef.x, scaleFactor * holderDef.y);
		}
		setRotation(-holderDef.rotation);
		setVisible(holderDef.Visible);
		setZIndex((int) holderDef.z);
		setTouchable(Touchable.childrenOnly);
		loadView(holderDef.ui);
		if(getStage() == null && stage != null)
			stage.addActor(this);
		if(stage != null && stage.onCreateCalled){
			if(elementEvent != null)
				elementEvent.onBodyCreated(this);
			if(getScript() != null)
				getScript().bodyCreated();
		}
	}
	
	private float lastScreenW = -1, lastScreenH = -1;

	/** Layout "screen" (UI elements only): size and position follow the screen box. Returns false when the layout is free. */
	private boolean applyScreenLayout(){
		if(holderDef == null || !"UI".equals(holderDef.type) || !com.star4droid.template.ui.UIHolderLayout.isScreen(holderDef.layout)) return false;
		float sw, sh;
		try {
			com.badlogic.gdx.utils.viewport.Viewport vp = stage.getUiStage().getViewport();
			sw = vp.getWorldWidth();
			sh = vp.getWorldHeight();
		} catch(Throwable t){
			return false;
		}
		if(sw <= 0 || sh <= 0) return false;
		lastScreenW = sw;
		lastScreenH = sh;
		float[] r = com.star4droid.template.ui.UIHolderLayout.compute(holderDef, sw, sh);
		holderDef.x = r[0];
		holderDef.y = r[1];
		holderDef.width = r[2];
		holderDef.height = r[3];
		setSize(r[2], r[3]);
		setOrigin(r[2] * 0.5f, r[3] * 0.5f);
		setPosition(r[0], r[1]);
		return true;
	}

	/** (Re)loads the .ui called {@code name} from the project's UI folder. Values changed at run time are lost. */
	private void loadView(String name){
		if(view != null){
			try { view.dispose(); } catch(Throwable ignored){}
			view = null;
		}
		view = UIView.load(projectPath(), name);
		view.setListeners(listeners);
		view.setFit(holderDef.fit);
		applyViewSize();
		if(holderDef.uiEvents != null){
			view.setEvents(holderDef.uiEvents);
			view.created();
		}
		addActor(view);
	}
	
	private void applyViewSize(){
		if(view == null) return;
		float w = scaleFactor > 0 ? getWidth() / scaleFactor : getWidth(), h = scaleFactor > 0 ? getHeight() / scaleFactor : getHeight();
		view.setScale(scaleFactor);
		view.setTransform(true);
		view.setSize(w, h);
		view.setPosition(0, 0);
	}
	
	/** The inner element {@code id} of a holder element; safe when the element is not a holder (every call then does nothing). */
	public static UINodeRef ref(PlayerItem item, String id){
		return new UINodeRef(item instanceof UIHolder ? (UIHolder) item : null, id);
	}

	/** Puts the holder in the screen box: anchor (topleft, top, center, bottomright...) with margins in px from the anchored edges. */
	public UIHolder place(String anchor, float marginX, float marginY){
		if(holderDef != null){
			holderDef.layout = "screen";
			holderDef.anchor = anchor == null ? "topleft" : anchor;
			holderDef.Margin_X = marginX;
			holderDef.Margin_Y = marginY;
			applyScreenLayout();
		}
		return this;
	}

	/** Size as percent (0-100) of the screen box; 0 keeps the fixed size of that side. Switches to the screen layout. */
	public UIHolder sizePercent(float widthPercent, float heightPercent){
		if(holderDef != null){
			holderDef.layout = "screen";
			holderDef.Width_Percent = widthPercent;
			holderDef.Height_Percent = heightPercent;
			applyScreenLayout();
		}
		return this;
	}

	/** Fill the whole width and / or height of the screen box (minus the margins). */
	public UIHolder stretch(boolean horizontal, boolean vertical){
		if(holderDef != null){
			holderDef.layout = "screen";
			holderDef.Stretch_X = horizontal;
			holderDef.Stretch_Y = vertical;
			applyScreenLayout();
		}
		return this;
	}

	/** Back to the free layout: x / y / width / height as typed. */
	public UIHolder freeLayout(){
		if(holderDef != null) holderDef.layout = "free";
		return this;
	}

	/** The shown .ui: find and change inner elements by id ({@code ui().setText("score", "10")}). */
	public UIView ui(){
		return view;
	}
	
	/** An inner element by id ({@code hud.node("score").setText("10")}); valid even after another .ui was loaded. */
	public UINodeRef node(String id){
		return new UINodeRef(this, id);
	}

	/** Listeners registered from code on the inner elements; they survive {@link #setUi}. */
	public UIListeners listeners(){
		return listeners;
	}

	public UIHolder onClick(String id, Runnable r){ listeners.onClick(id, r); return this; }
	public UIHolder onTouchStart(String id, Runnable r){ listeners.onTouchStart(id, r); return this; }
	public UIHolder onTouchEnd(String id, Runnable r){ listeners.onTouchEnd(id, r); return this; }
	public UIHolder onChange(String id, UIListeners.OnValue l){ listeners.onChange(id, l); return this; }
	public UIHolder onSubmit(String id, UIListeners.OnText l){ listeners.onSubmit(id, l); return this; }
	public UIHolder onScroll(String id, UIListeners.OnScroll l){ listeners.onScroll(id, l); return this; }

	/** Shows another .ui file of the project's UI folder in this element. */
	public UIHolder setUi(String uiName){
		if(holderDef != null) holderDef.ui = uiName;
		loadView(uiName);
		return this;
	}
	
	/** Replaces the callbacks for the inner elements (use instead of / in addition to the events edited in the editor). */
	public UIHolder setUiEvents(UIEvents events){
		if(holderDef != null) holderDef.uiEvents = events;
		if(view != null){
			view.setEvents(events);
		}
		return this;
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		setOrigin(getWidth() * 0.5f, getHeight() * 0.5f);
		applyViewSize();
	}
	
	@Override
	public boolean setZIndex(int z){
		boolean b = true;
		try {
			b = super.setZIndex(z);
		} catch(Exception e){}
		if(stage != null) stage.updateActors();
		return b;
	}
	
	@Override
	public void update() {
		if(getScript() != null)
			getScript().bodyUpdate();
		else if(elementEvent != null) elementEvent.onBodyUpdate(this);
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		if(holderDef != null && "screen".equals(holderDef.layout)){
			try {
				com.badlogic.gdx.utils.viewport.Viewport vp = stage.getUiStage().getViewport();
				if(vp.getWorldWidth() != lastScreenW || vp.getWorldHeight() != lastScreenH) applyScreenLayout();
			} catch(Throwable ignored){}
		}
		super.draw(batch, parentAlpha);
		update();
	}
	
	@Override
	public Body getBody() {
		return null;
	}
	
	@Override
	public float getBodyX(){ return getX() + getWidth() * 0.5f; }
	
	@Override
	public float getBodyY(){ return getY() + getHeight() * 0.5f; }
	
	@Override
	public float distToPoint(float x, float y){
		float xx = x - getBodyX(), yy = y - getBodyY();
		return (float)Math.sqrt(xx * xx + yy * yy);
	}
	
	@Override
	public float distTo(PlayerItem item){
		return distToPoint(item.getBodyX(), item.getBodyY());
	}
	
	@Override
	public ChildsHolder getChildsHolder() {
		if(childsHolder == null) childsHolder = new ChildsHolder(this);
		return childsHolder;
	}
	
	@Override
	public void setScript(ItemScript script){
		this.itemScript = script;
	}
	
	@Override
	public <T extends ItemScript> T getScript(){
		return (T) itemScript;
	}
	
	@Override
	public PlayerItem getClone(String newName) {
		UIHolderDef newDef = holderDef.getClone(newName);
		UIHolder item = new UIHolder(stage).setElementEvent(elementEvent).setDef(newDef);
		if(getScript() != null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
				script.cloneBehaviorsFrom(getScript());
			} catch(Exception ex){}
		}
		return item;
	}
	
	public UIHolder setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
		return holderDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}
