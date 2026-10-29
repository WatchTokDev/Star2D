package com.star4droid.template.behaviors;

import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

/** Script installed on an element that has behaviors but no script of its own. Does nothing by itself. */
public class BehaviorHost extends ItemScript {
	public BehaviorHost(PlayerItem item) {
		super(item);
	}
}
