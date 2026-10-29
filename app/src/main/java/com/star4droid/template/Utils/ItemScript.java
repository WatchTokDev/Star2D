package com.star4droid.template.Utils;

import box2dLight.Light;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.template.Items.StageImp;
import com.badlogic.gdx.physics.box2d.Joint;
import com.star4droid.template.behaviors.Behavior;
import java.util.ArrayList;

public class ItemScript {
	public PlayerItem playerItem;
	public Body body;
	public Actor actor;
	public StageImp stage;
	/** Behaviors attached to the element (see com.star4droid.template.behaviors.Behavior); null when there are none. */
	private ArrayList<Behavior> behaviors;
	
	public ItemScript(){}
	
	public ItemScript(PlayerItem item){
	    playerItem = item;
	    try {
	        body = item.getBody();
		    actor = item.getActor();
		} catch(Exception e){}
	}
	
	public ItemScript(PlayerItem item,StageImp stage){
		playerItem = item;
		body = item.getBody();
		actor = item.getActor();
		this.stage = stage;
	}
	
	public StageImp getStage(){
	    return stage;
	}
	
	public final PlayerItem findItem(String name){
	    if(stage!=null)
    	    try {
    	        return stage.findItem(name);
    	    } catch(Exception ex){}
	    return null;
	}
	
	/** Short for findItem(name): item("coin1"). */
	public PlayerItem item(String name){
	    return findItem(name);
	}

	/** Filled by the generated <scene>ItemRefs base class (elements as fields); nothing to do here. */
	public void bindRefs(java.util.Map<String,PlayerItem> items){}

	public final Joint getJoint(String name){
		return stage.getJoint(name);
	}
	
	public final Light findLight(String name){
		return (stage!=null)?stage.findLight(name):null;
	}
	
	public final void setImage(String image){
	    stage.setImage(playerItem,image);
	}
	
	public final ItemScript setStage(StageImp stage){
	    this.stage = stage;
	    return this;
	}
	
	public final ItemScript setItem(PlayerItem item){
		playerItem=item;
		body = item.getBody();
		return this;
	}
	
	public void onClick(){
		
	}
	public void onTouchStart(InputEvent event){
		
	}
	public void onTouchEnd(InputEvent event){
		
	}
	public void onBodyCreated(){
		
	}
	public void onBodyUpdate(){
		
	}
	public void onCollisionBegin(PlayerItem other){
		
	}
	public void onCollisionEnd(PlayerItem other){
		
	}
	
	// ---- behaviors (several per element, coexisting with the user's script) ----
	public final void addBehavior(Behavior b){
		if(behaviors==null) behaviors=new ArrayList<Behavior>();
		if(!behaviors.contains(b)) behaviors.add(b);
	}
	
	public final ArrayList<Behavior> getBehaviors(){
		return behaviors;
	}
	
	/** First attached behavior of this class (or null): script.getBehavior(Shooter.class). */
	@SuppressWarnings("unchecked")
	public final <T extends Behavior> T getBehavior(Class<T> type){
		if(behaviors!=null)
			for(int i=0;i<behaviors.size();i++)
				if(type.isInstance(behaviors.get(i))) return (T) behaviors.get(i);
		return null;
	}
	
	/** Called when the element is cloned: new instances of the same behaviors with the same parameter values. */
	public final void cloneBehaviorsFrom(ItemScript source){
		if(source==null||source.behaviors==null||source==this) return;
		for(int i=0;i<source.behaviors.size();i++){
			Behavior old=source.behaviors.get(i);
			try {
				Behavior copy=old.getClass().newInstance();
				copy.copyParamsFrom(old);
				copy.copyHandlersFrom(old);
				copy.attach(playerItem,stage!=null?stage:source.stage);
			} catch(Exception ex){}
		}
	}
	
	/** Called from PlayerItem.destroy(). */
	public final void destroyBehaviors(){
		if(behaviors==null) return;
		for(int i=0;i<behaviors.size();i++)
			behaviors.get(i).engineDestroy();
	}
	
	public final PlayerItem getPlayerItem(){
		return playerItem;
	}
	
	/**
	 * Order of everything that reacts to an event: the behaviors (in the order of their list), then the user's Body Script,
	 * then the element events (blocks / code of the Events panel) - so user code always has the last word.
	 */
	private void eachBehavior(int kind,InputEvent ev,PlayerItem other){
		if(behaviors==null) return;
		for(int i=0;i<behaviors.size();i++){
			Behavior b=behaviors.get(i);
			if(!b.enabled) continue;
			switch(kind){
				case 0: b.onClick(); break;
				case 1: b.onTouchStart(ev); break;
				case 2: b.onTouchEnd(ev); break;
				case 3: b.onCollisionBegin(other); break;
				default: b.onCollisionEnd(other);
			}
		}
	}
	
	public final void click(){
		eachBehavior(0,null,null);
		onClick();
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onClick(playerItem);
	}
	
	public final void touchStart(InputEvent inputEvent){
		eachBehavior(1,inputEvent,null);
		onTouchStart(inputEvent);
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onTouchStart(playerItem,inputEvent);
	}
	
	public final void touchEnd(InputEvent inputEvent){
		eachBehavior(2,inputEvent,null);
		onTouchEnd(inputEvent);
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onTouchEnd(playerItem,inputEvent);
	}
	
	public void bodyCreated(){
		if(behaviors!=null)
			for(int i=0;i<behaviors.size();i++) behaviors.get(i).engineStart();
	    onBodyCreated();
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onBodyCreated(playerItem);
	}
	public final void bodyUpdate(){
		if(behaviors!=null)
			for(int i=0;i<behaviors.size();i++) behaviors.get(i).engineUpdate();
	    onBodyUpdate();
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onBodyUpdate(playerItem);
	}
	public final void collisionBegin(PlayerItem other){
		eachBehavior(3,null,other);
	    onCollisionBegin(other);
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onCollisionBegin(playerItem,other);
	}
	public final void collisionEnd(PlayerItem other){
		eachBehavior(4,null,other);
	    onCollisionEnd(other);
		if(playerItem.getElementEvents()!=null)
		    playerItem.getElementEvents().onCollisionEnd(playerItem,other);
	}
	
	// ---- switching behaviors from code ----
	/** Detaches a behavior (its onDisable/onDestroy run). */
	public final boolean removeBehavior(String id){
		Behavior b=getBehavior(id);
		if(b==null) return false;
		b.engineDestroy();
		behaviors.remove(b);
		return true;
	}
	
	/** Behavior by class name ("PlatformerPlayer") or null. */
	public final Behavior getBehavior(String id){
		if(behaviors!=null)
			for(int i=0;i<behaviors.size();i++)
				if(behaviors.get(i).id().equals(id)) return behaviors.get(i);
		return null;
	}
	
	/**
	 * Turns a behavior on or off. Turning one ON switches OFF the other behaviors of its exclusive group
	 * (movement / steering...), so a game can swap controls: player.setBehaviorEnabled("Car", true).
	 */
	public final void setBehaviorEnabled(String id,boolean on){
		Behavior b=getBehavior(id);
		if(b==null) return;
		if(on&&b.group().length()>0)
			for(int i=0;i<behaviors.size();i++){
				Behavior o=behaviors.get(i);
				if(o!=b&&o.group().equals(b.group())) o.setEnabled(false);
			}
		b.setEnabled(on);
	}
	
	public final boolean isBehaviorEnabled(String id){
		Behavior b=getBehavior(id);
		return b!=null&&b.enabled;
	}
}
