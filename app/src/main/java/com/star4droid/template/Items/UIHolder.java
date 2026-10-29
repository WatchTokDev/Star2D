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
		setSize(scaleFactor * holderDef.width, scaleFactor * holderDef.height);
		setOrigin(getWidth() * 0.5f, getHeight() * 0.5f);
		setPosition(scaleFactor * holderDef.x, scaleFactor * holderDef.y);
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
	
	/** (Re)loads the .ui called {@code name} from the project's UI folder. Values changed at run time are lost. */
	private void loadView(String name){
		if(view != null){
			try { view.dispose(); } catch(Throwable ignored){}
			view = null;
		}
		view = UIView.load(projectPath(), name);
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
	
	/** The shown .ui: find and change inner elements by id ({@code ui().setText("score", "10")}). */
	public UIView ui(){
		return view;
	}
	
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
