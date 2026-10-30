package com.star4droid.template.Items;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.star2d.ElementDefs.GroupDef;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

/** Empty container: no body, nothing drawn, never captures touches. Its children follow it (see GroupFollower). */
public class GroupItem extends Actor implements PlayerItem {
	StageImp stage;
	ElementEvent elementEvent;
	GroupDef groupDef;
	ChildsHolder childsHolder = new ChildsHolder(this);
	ItemScript itemScript;
	
	public GroupItem(StageImp stageImp){
		super();
		this.stage = stageImp;
		setTouchable(Touchable.disabled);
	}
	
	public static GroupItem create(StageImp stageImp, GroupDef def, ElementEvent elementEvent){
		return new GroupItem(stageImp).setElementEvent(elementEvent).setDef(def);
	}
	
	public GroupItem setDef(GroupDef def){
	    this.groupDef = def;
	    setup();
	    return this;
	}
	
	public GroupDef getGroupDef(){
	    return groupDef;
	}
	
	private void setup(){
		if(groupDef==null) return;
		boolean ui = "UI".equals(groupDef.type);
		float sc = ui ? 1f : StageImp.WORLD_SCALE;
		setName(groupDef.name);
		setSize(sc * groupDef.width, sc * groupDef.height);
		com.star4droid.template.Utils.Pivot.apply(this, groupDef);
		setPosition(sc * groupDef.x, sc * groupDef.y);
		setRotation(-groupDef.rotation);
		setVisible(groupDef.Visible);
		setZIndex((int) groupDef.z);
		setTouchable(Touchable.disabled);
		if(getStage()==null && stage!=null)
		    stage.addActor(this);
		if(stage!=null && stage.onCreateCalled){
			if(elementEvent!=null)
				elementEvent.onBodyCreated(this);
			if(getScript()!=null)
				getScript().bodyCreated();
		}
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		com.star4droid.template.Utils.Pivot.apply(this, groupDef);
	}
	
	@Override
	public boolean setZIndex(int z){
	    boolean b = true;
	    try {
	        b = super.setZIndex(z);
	    } catch(Exception e){}
	    if(stage!=null) stage.updateActors();
	    return b;
	}
	
	@Override
	public void update() {
		if(getScript()!=null)
			getScript().bodyUpdate();
		else if(elementEvent!=null) elementEvent.onBodyUpdate(this);
	}
	
	@Override
	public void act(float delta) {
		super.act(delta);
		if(groupDef != null && groupDef.Y_Sort) ySort();
	}
	
	private final java.util.ArrayList<Actor> sortList = new java.util.ArrayList<>();
	
	private void collect(ChildsHolder h, int depth){
		if(h == null || depth > 20) return;
		for(PlayerItem it : h.childs){
			if(it instanceof Actor && it != this && ((Actor)it).getParent() != null) sortList.add((Actor)it);
			if(it != null) collect(it.getChildsHolder(), depth + 1);
		}
	}
	
	private static float zOf(Actor a){
		if(a instanceof PlayerItem){
			try {
				com.star4droid.star2d.ElementDefs.ItemDef d = ((PlayerItem)a).getProperties();
				if(d != null) return d.getZF();
			} catch(Exception ignored){}
		}
		return 0;
	}
	
	/**
	 * Y-Sort: the descendants of this group are drawn by their height on the screen - the lower one (smaller y) in front.
	 * Only the order inside the slots they already use changes, so everything outside the group keeps its place.
	 */
	private void ySort(){
		sortList.clear();
		collect(getChildsHolder(), 0);
		if(sortList.size() < 2) return;
		com.badlogic.gdx.scenes.scene2d.Group parent = sortList.get(0).getParent();
		for(Actor a : sortList) if(a.getParent() != parent) return; // children on different layers (world / UI): leave them
		final com.badlogic.gdx.utils.SnapshotArray<Actor> kids = parent.getChildren();
		int n = sortList.size();
		int[] slots = new int[n];
		for(int i = 0; i < n; i++) slots[i] = kids.indexOf(sortList.get(i), true);
		for(int i = 0; i < n; i++) if(slots[i] < 0) return;
		java.util.Arrays.sort(slots);
		java.util.Collections.sort(sortList, new java.util.Comparator<Actor>(){
			@Override public int compare(Actor a, Actor b){
				int c = Float.compare(zOf(a), zOf(b));
				if(c != 0) return c;
				return Float.compare(b.getY(), a.getY()); // higher on the screen first = behind
			}
		});
		boolean same = true;
		for(int i = 0; i < n; i++) if(kids.get(slots[i]) != sortList.get(i)) { same = false; break; }
		if(same) return;
		for(int i = 0; i < n; i++) kids.set(slots[i], sortList.get(i));
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		// nothing to draw, only the update event
		update();
	}
	
	@Override
	public Body getBody() {
	    return null;
	}
	
	@Override
	public float getBodyX(){ return getX() + getWidth()*0.5f; }
	
	@Override
	public float getBodyY(){ return getY() + getHeight()*0.5f; }
	
	@Override
	public float distToPoint(float x,float y){
		float xx = x - getBodyX(), yy = y - getBodyY();
		return (float)Math.sqrt(xx*xx+yy*yy);
	}
	
	@Override
	public float distTo(PlayerItem item){
		return distToPoint(item.getBodyX(), item.getBodyY());
	}
	
	@Override
	public ChildsHolder getChildsHolder() {
		if(childsHolder==null) childsHolder = new ChildsHolder(this);
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
	
	/** Clones the group itself. Its children are NOT cloned: clone them one by one and addChild them. */
	@Override
	public PlayerItem getClone(String newName) {
		GroupDef newDef = groupDef.getClone(newName);
		GroupItem item = new GroupItem(stage).setElementEvent(elementEvent).setDef(newDef);
		if(getScript()!=null){
			try {
				ItemScript script = (ItemScript)(getScript().getClass().getConstructor(PlayerItem.class).newInstance(item));
				script.setItem(item).setStage(stage);
				item.setScript(script);
				script.cloneBehaviorsFrom(getScript());
			} catch(Exception ex){}
		}
		return item;
	}
	
	public GroupItem setElementEvent(ElementEvent event){
		elementEvent = event;
		return this;
	}
	
	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties(){
	    return groupDef;
	}
	
	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}
}
