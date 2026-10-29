package com.star4droid.star2d.ElementDefs;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;

/**
 * An {@link ElementEvent} whose methods all do nothing, plus a name. The generated scene code creates
 * {@code new ElementEventAdapter("player"){ ... }} and overrides only the events that have code, so
 * an element with no events costs no class at all and one with a single event costs a single method.
 * {@link #getName()} stays available inside the event code (blocks use it to read the element's name).
 */
public abstract class ElementEventAdapter implements ElementEvent {
	private final String name;

	public ElementEventAdapter(String name){
		this.name = name == null ? "" : name;
	}

	@Override
	public String getName(){
		return name;
	}

	@Override
	public void onClick(PlayerItem current){}

	@Override
	public void onTouchStart(PlayerItem current, InputEvent event){}

	@Override
	public void onTouchEnd(PlayerItem current, InputEvent event){}

	@Override
	public void onBodyCreated(PlayerItem current){}

	@Override
	public void onBodyUpdate(PlayerItem current){}

	@Override
	public void onCollisionBegin(PlayerItem current, PlayerItem body2){}

	@Override
	public void onCollisionEnd(PlayerItem current, PlayerItem body2){}
}
