# UI files (`.ui`), UIHolder and the UI runtime

A **UI** is a screen (HUD, menu, settings, dialog…) described in one JSON file, `UI/<name>.ui`, inside the project.
The **UI Maker** edits that file; the **UIHolder** element shows it in a scene as ONE block; the **runtime**
(`com.star4droid.template.ui`, shared with the exported game) builds the widgets, lays them out and reports events.

```
project/
  UI/hud.ui, UI/settings.ui …        <- the documents (exported with the game, the whole project folder is zipped)
  UI/menus/pause.ui …                <- documents may live in sub folders: they are addressed as "menus/pause" (no .ui) everywhere
  images/…  sounds/…                 <- images/sounds referenced by the documents (project relative)
  Events/<scene>/private/<holder>/ui/<nodeId>/<event>.java|.visual   <- event code of inner elements
```

Everything in `com.star4droid.template.ui` is pure Java + libGDX (no `android.*`) so it is part of
`template/runtime-sources.txt` (`com/star4droid/template/**`). Whenever it changes, `tools/make_template.py build` must be re-run.

---------------------------------------------------------------------------------------------------

## 1. File format (version 1)

```json
{
  "version": 1,
  "name": "hud",
  "designWidth": 1280, "designHeight": 720,
  "theme": { "preset": "jelly", "primary": "#35c46a", "radius": 22, "font": "fonts/my.s2f",
             "images": { "button": "ui/btn.9.png", "button.pressed": "ui/btn_p.9.png" } },
  "root": {
    "id": "root", "type": "Stack",
    "children": [
      { "id": "btnJump", "type": "Button", "anchor": "bottomright", "margin": [0,0,40,40], "w": 150, "h": 150,
        "props": { "text": "JUMP", "style": "jelly", "shape": "pill" } }
    ]
  }
}
```

| field | meaning |
|---|---|
| `version` | always 1 (unknown newer versions are read as far as possible and give a warning) |
| `name` | file name without `.ui` |
| `designWidth/Height` | size the layout is designed for. `fit=stretch` lays out at the real size of the holder; `contain`/`cover` lay out at the design size and scale uniformly |
| `theme` | see section 3 |
| `root` | the root node (a container, usually `Stack` or `Panel`; `anchor: stretch`) |

### Node

```json
{ "id": "btnJump", "type": "Button", "anchor": "bottomright", "margin": 8 | [l,t,r,b],
  "x": 0, "y": 0, "w": "120" | "50%" | "wrap" | "fill", "h": …,
  "visible": true, "enabled": true, "alpha": 1, "weight": 0, "touchable": "enabled|disabled|childrenOnly", "locked": true,
  "props": { … type specific + common props … }, "children": [ … ] }
```

* `id` – unique in the file, only letters, digits, `_` (it is used in generated Java and in event file names). `UIDocument.cleanId`.
* `x`, `y` – extra offset in px added after anchoring; **y points up** (like the rest of Star2D).
* `w`, `h` – a number (px), `"50%"` (of the parent's content box), `"wrap"` (content size), `"fill"` (the rest of the parent's content box; inside a `Linear` it is a flexible share, see `weight`). Missing = the default of the type (Button 160×56, Icon 32, Image 64, Switch 56×32, TextField 240×48, Progress 200×thickness, Slider 200×max(32, knob+8), containers fill).
* `weight` – flexible share inside a `Linear` (0 = no share).
* `visible=false` – node and children are not drawn and **take no room** in layouts.
* `enabled=false` or `interactable=false` – drawn dimmed (alpha × 0.45) and does not react to touch.
* `touchable` – `enabled`, `disabled` (node and children ignore touch) or `childrenOnly` (the node itself ignores touch, its children work).
  Defaults: Label/Image/Icon/Progress `disabled`; Stack/Linear/Grid `childrenOnly` (unless they have a background colour/image); the rest `enabled`.
* `locked` – **editor only** (written only when `true`, ignored by the game): the UI Maker will not select it by tapping in the preview, will not draw resize handles on it and will not move, wrap, indent or delete it until it is unlocked.
* `props` – everything else; the full list per type is generated from `UIWidgetRegistry` in section 9.

Unknown types/props are kept when reading and writing (round trip is lossless) and reported by `UIDocument.validate()`.

Types: `Panel, Stack, Linear, Grid, ScrollView, Label, Image, Icon, Button, Progress, Slider, Checkbox, Radio, Switch, TextField`
(aliases such as `Text`, `Box`, `HBox`, `VBox`, `CheckBox`, `Toggle`, `Scroll`, `Bar`… are accepted by `UINode.canonicalType`).

---------------------------------------------------------------------------------------------------

## 2. Layout rules

Coordinates inside the runtime are bottom-left, y up. `UINode.rx/ry/rw/rh` hold the result of a layout pass, relative to the parent.

**Anchors** (inside the parent's content box): `topleft` (default), `top`, `topright`, `left`, `center`, `right`, `bottomleft`,
`bottom`, `bottomright`, `stretch` (both axes), `stretchx`, `stretchy`. Margins shrink the box the node is anchored in.

**Containers**

| type | rule |
|---|---|
| `Panel`, `Stack` | children are placed independently with their anchor (Panel also draws a background) |
| `Linear` | `orientation` vertical/horizontal, `spacing`, `padding`, `align` start\|center\|end\|stretch (cross axis), `gravity` start\|center\|end\|space_between\|space_around\|space_evenly (main axis). Children with `weight>0` (or `w/h: fill`) share the remaining space by weight. A child whose `anchor` is not `topleft` overrides `align` for itself |
| `Grid` | `columns`, `spacing` (or `hspacing`/`vspacing`), `rowHeight`, `align`; cells have the same width, rows the height of their tallest child |
| `ScrollView` | `direction` vertical\|horizontal\|both, ONE content child laid out in two passes (its size gives `contentW/H`); drag to scroll |

`padding` (number or `[l,t,r,b]`) works on every container. Text and images are measured with the real font
(`UILayout.Measurer`; `UILayout.APPROX` is used by tests and tools without GL).

---------------------------------------------------------------------------------------------------

## 3. Themes and styles

`theme.preset`: `jelly, candy, flat_3d, glass_neon, hex_arcade, dark, light, minimal`. One property switches the whole look.

Overrides in `theme` (all optional, flat keys): **colour roles** `background, surface, surfaceAlt, text, textDim, border, primary, secondary, accent, success, danger`
(`#rgb`, `#rrggbb`, `#rrggbbaa`), **numbers** `radius, borderWidth, shadow, gloss, bevel, glow, fontSize`, `font` (project font path),
and `images`: image overrides keyed `"<kind>"`, `"<kind>.<style>"`, `"<kind>.pressed"` with `kind` = `button, panel, …`
(a `.9.png` becomes a 9-patch; see docs for 9-patch).

Props that take a colour accept a **role name** (`"primary"`) or a hex colour, so a theme change recolours everything.

**Per-widget `style` presets** (`props.style`), each also overridable per node with plain props
(`trackColor, fillColor, knobColor, knobSize, trackHeight, radius, borderColor …`, see the tables):

| widget | styles |
|---|---|
| Slider | classic, jelly, neon, minimal_line, thick_candy, game_bar, round_knob |
| Progress | classic, jelly, neon, minimal_line, thick_candy, game_bar, health, xp |
| Switch / Checkbox / Radio | classic, jelly, neon, minimal, square |
| TextField | classic, jelly, neon, minimal_line, dark, flat |
| Button | colour: primary, secondary, accent, success, danger, ghost, flat · look: outline, neon, glass, pill, jelly, soft, square, hex |
| Panel | card, solid, glass, outline, flat, dark, transparent |

**Theme of one element**: the common prop `theme` (`""` = the theme of the whole UI, or one of the presets above) gives a node – and everything inside it – a look of its own,
resolved by `UITheme.forNode(node, docTheme)` (the node's own setting, else the nearest parent that has one, else the document theme). A node with its own theme uses that preset as it is
(the document's colour / number overrides do not apply to it; the document `font` does).

Lookup order for every visual value: node `props` > style preset > theme > built-in default.
Images the user supplies (`image`, `pressedImage`, `iconImage`… project relative paths, 9-patch supported) always win over generated art.

---------------------------------------------------------------------------------------------------

## 4. Events

Interface `UIEvents` (`UIEventsAdapter` has empty defaults). Every callback gets the `UIView` and the **node id**, so ONE object handles a whole file:

| callback | when | extra |
|---|---|---|
| `onCreate(ui)` | after the view was built (again after `rebuild()`) | |
| `onClick(ui, id)` | button / clickable node released inside it | |
| `onTouchStart(ui, id)` / `onTouchEnd(ui, id)` | finger down / up (also when cancelled) | |
| `onChange(ui, id, value)` | Slider: `Float` (continuous while dragging, or on release only when `changeMode` = release), Checkbox/Radio/Switch: `Boolean`, TextField: `String` | |
| `onSubmit(ui, id, text)` | TextField enter/done | |
| `onScroll(ui, id, x, y)` | ScrollView moved (px from the top-left of the content) | |

Which events a type offers: `UIWidgetRegistry.eventsOf(node)`. A `clickSound` prop plays a project sound on click; `pressScale` (default true) scales buttons down while pressed;
`tooltip` shows a small label above the node after a long press.

### Event code of a UIHolder (generated Java)

Inner events are edited like any other event (blocks or Java). The files are
`Events/<scene>/private/<holder>/ui/<nodeId>/<event>.java` (+ `.visual`). `Helpers/UiEventsGen` turns the non-empty ones into

```java
hud_def.uiEvents = new com.star4droid.template.ui.UIEventsAdapter(){
  @Override public void onClick(com.star4droid.template.ui.UIView ui, String id) {
    if (id.equals("btnJump")) { /* code of btnJump/onClick.java */ }
  }
  …
};
```

Variables available in the code: `ui` (UIView), `id`, and `value` (onChange, Object), `text` (onSubmit), `x`, `y` (onScroll).
Code can use the whole `UIView` API (below) and the usual game API, so a jump button connects to the game with ordinary code.

### Inner elements from code (holder, `UINodeRef`, listeners)

The inner nodes of a holder are first-class for scripts, no `findItem` needed:

```java
// generated base class <scene>ItemRefs / <scene>SceneRefs (every Body Script and the Scene Script extends one)
public UIHolder hud;                                   // typed: hud.ui(), hud.node(..), hud.onClick(..), setUi(..)
public UINodeRef hud_root, hud_btnPlay, hud_score;     // one per node id of the holder's .ui: <holder>_<nodeId>
```
```java
hud_score.setText("12").setVisible(true);                  // same as hud.node("score")
hud_btnPlay.setEnabled(false);
hud.onClick("btnPlay", new Runnable() { public void run() { startGame(); } });     // Java 8+ projects: () -> startGame()
hud_volume.onChange(new UIListeners.OnValue() { public void on(Object v) { setVolume(((Float) v).floatValue()); } });
```
* `UINodeRef` (package `com.star4droid.template.ui`) wraps (holder, id) and looks the widget up in the holder's CURRENT view each time, so it survives `setUi(..)` / `rebuild()`; an unknown id does nothing (setters) or returns ""/0/false (getters). Setters return the ref: `setText setValue setRange setChecked setVisible setEnabled setAlpha setImage setColor scrollTo click`, `set(key, value)`; getters `getText getValue isChecked isVisible isEnabled getTag exists node view`.
* Listeners: `onClick onTouchStart onTouchEnd` take a `Runnable`; `onChange(id, UIListeners.OnValue)` (Float for Slider, Boolean for Checkbox / Radio / Switch, String for TextField), `onSubmit(id, UIListeners.OnText)`, `onScroll(id, UIListeners.OnScroll)` (x, y). Same methods on `UINodeRef` without the id, and `UIView.listeners()` for a standalone view. They run in addition to (after) the `UIEvents` object and the event code written in the editor, on the GL thread; an exception in one is logged and the others still run. `UIListeners.remove(id, event|null)` / `clear()` / `UINodeRef.clearListeners()` remove them. A holder keeps its listeners when another .ui is loaded with `setUi`.
* Fields that would not be valid Java names (`bad-id`) or would clash with an element are skipped (the Refs comment lists them); the file may be in a sub folder (`ui` = `folder/name`, a trailing `.ui` is fine), see `UIView.uiFile`.
* Script variables: a field of type `UINodeRef` is a variable; the picker (Properties > Script variables) lists the inner nodes of every UI holder of the scene as `hud/score`. Stored as the string `"hud/score"`, resolved by `BehaviorRegistry.ref(this, "hud/score", UINodeRef.class)`. Lists of them work too. See SCRIPTVARS.md.
* The Events panel of a selected holder lists the nodes of its `.ui` (interactive ones and any with code) with ALL their events, a `●` marks the events that have code, and shows the code shortcuts above.

---------------------------------------------------------------------------------------------------

## 5. UIHolder (scene element)

* Element type `UIHOLDER` (`TYPE`), `type: "UI"` (screen space, px, origin bottom-left), default `z=100`, size 300×300.
* Properties: `ui` (file name without `.ui`, empty = nothing), `fit` (`stretch|contain|cover`), plus the usual `x, y, z, width, height, rotation, Visible, name, Script`.
* In the editor the `.ui` is rendered directly as one block (dashed orange frame + name; empty = placeholder text). The inner elements cannot be selected or moved in the scene; the Properties panel has **Edit** (choose a file from the project's `UI/` folder, then the UI Maker opens) and **New** (asks a name, creates `UI/<name>.ui`, opens the Maker).
  The editor reloads the view when the file changes (checked every 0.6 s: modification time + length).
* Runtime class `com.star4droid.template.Items.UIHolder` (a `Group implementing PlayerItem`). In scripts (the generated Refs make `hud` a `UIHolder` field):
  `UIView ui = hud.ui();`  `hud.setUi("settings");`  `hud.node("score").setText("5")`  `hud.onClick("btnPlay", runnable)` (see "Inner elements from code" in section 4).
  Inside the UI event code the `ui` variable is already the view.
* When a holder is in world space (`type` ≠ UI) the view is scaled by `StageImp.WORLD_SCALE` like every world element.

---------------------------------------------------------------------------------------------------

## 6. UIView API (runtime, GL thread)

```java
UIView ui = UIView.load(projectPath, "hud");          // <project>/UI/hud.ui ; broken/missing file -> empty view, see ui.document().warnings
ui.setSize(1280, 720); stage.addActor(ui);
ui.setEvents(new UIEventsAdapter(){ … }).created();    // created() fires onCreate (UIHolder does this for you)
ui.setFit("stretch" | "contain" | "cover");
ui.setPlaceholders(true);                              // editor/preview: faint box where an Image has no picture
```

| group | methods |
|---|---|
| lookup | `has(id)`, `ids()`, `node(id)` (the `UINode`), `find(id)` (the `UIActor`) |
| text/value | `setText/getText`, `setValue/getValue`, `setRange(id,min,max)`, `setChecked/isChecked` |
| state | `setVisible/isVisible`, `setEnabled/isEnabled`, `setAlpha`, `setImage(id,path)`, `setColor(id,tint)` |
| generic | `set(id, key, value)` – any registry property by key, returns an error text or null; `getTag(id)` |
| actions | `click(id)` (fires the same events), `scrollTo(id,x,y)`, `relayout()`, `rebuild()` (values changed at run time are lost), `dispose()` |
| document | `document()`, `setDocument(doc)`, `theme()`, `projectPath()` |

---------------------------------------------------------------------------------------------------

## 7. Java API for the UI Maker (fork H2)

All pure Java, safe on any thread (no GL) unless noted. Package `com.star4droid.template.ui`.

* **Opening**: `com.star4droid.star2d.ui.UiMakerLauncher.open(Context, projectPath, uiName, Runnable onSaved)` opens the UI Maker
  (`com.star4droid.star2d.ui.uimaker.UiMakerDialog`) unless the `UiMakerLauncher.impl` hook is set. The Maker saves with `UIDocument.write(file)` (atomic) and runs `onSaved` on the UI thread, the editor then reloads the holder.
  Helpers: `UiMakerLauncher.uiDir(projectPath)`, `UiMakerLauncher.list(projectPath)` (names without `.ui`). Entry points: Files panel (`UI/*.ui`, "New UI"), UIHolder Properties (Edit / New).
* **UI Maker classes** (`ui/uimaker/`): `UiMakerDialog` (screen), `UiPreviewView` (device frame, selection, drag/resize), `UiPropsView` (data-driven inspector), `PreviewEngine` + `UiPreviewGl` (real offscreen libGDX render of the document in an FBO on the GL thread, node rectangles, touch routing for Test mode),
  `UiRects` (absolute rectangles, `hit` skips locked nodes), `UiLint` (overlaps, off-screen, touch targets), `UiHistory` (undo/redo snapshots), `UiFolders` (folders inside `UI/`), `UiTr` (translation keys of registry texts and editing errors), `UiOps` (add/set/remove/move/wrap/sanitize/themes, shared with the AI), `UiTemplates` (8 ready screens), `UiAi` (logic of the `ui` AI tool). Tests: `python3 tools/test_uimaker.py`.
* **Editor screen** (`UiMakerDialog`), top to bottom:
  * *Toolbar*: close, the file path (`menus/pause.ui *`, a star = unsaved; tap = Save as), undo, redo, layout warnings, More, **Save**. Saving shows a snackbar *inside* the Maker ("Saved menus/pause.ui", the dialog hosts the snackbars with `Snack.pushHost/popHost`) and the Save button turns green for a moment.
    *More*: Save as (`UiFolders.newUiDialog`: name + folder chooser), **Open another UI…** (`UiFolders.pickUi`, asks about unsaved changes, then loads the file into the same editor), insert a ready-made screen, show/copy JSON, clear, warnings.
    Names may contain folders; saving creates the folder.
  * *Preview*: the phone (design) size is `curW x curH`, starting from the document's design size. Chips under the phone: Edit/Test, orientation (swaps width and height), **size** (`WxH`: picker with phones, tablets, 16:9 / 4:3 helpers in the current orientation and "Custom…" = `WxH`), grid (off/8/16/32), background, zoom.
    In **Edit** mode the frame has three handles (right edge, bottom edge, bottom-right corner): drag them to change the phone size freely (snaps to the grid when it is on, 120…4096). The scale is frozen during the drag so the top-left corner stays put; the picture is re-rendered live and the view is refitted when you let go.
    "Use preview size" (Theme tab) copies the current phone size into the document's design size.
  * *Bottom panel* (tabs Elements / Tree / Properties / Theme): the bar on its edge facing the preview (top edge in portrait, the side next to the phone in landscape, also in Arabic) is a **grabber**. Drag it to resize the panel (20 %…80 % of the screen; dragging nearly closed hides it), tap it to hide / show the panel with a short animation. When hidden only the grabber stays and the preview gets the whole screen. The size per orientation and the hidden state are remembered (`uim_split_p`, `uim_split_l`, `uim_split_collapsed` in the app preferences).
  * *Window*: the dialog is edge-to-edge (targetSdk 36), so the content gets system bar / cutout / keyboard insets as padding (the toolbar sits under the status bar).
  * *Events* (Properties tab, under Parent): one row per event of the selected widget (`UIWidgetRegistry.eventsOf`), `●` when it has code. Tapping opens the normal block / Java editor (`VisualScriptingDialog.showForUi`). The code belongs to a UI holder of the open scene that shows this UI (`Events/<scene>/private/<holder>/ui/<node>/<event>`): the Maker finds it by itself (the selected holder first, one holder = used, several = asks), unsaved changes are saved first, no holder = a hint says to add one.
  * *Tree*: starts collapsed (the root shows its children); an arrow per container opens it with a height animation (`ui/behaviors/Collapse`); selecting an element in the preview opens its parents. Every row has a **lock** toggle and an eye; a coloured dot marks an element that has its own theme. Tree actions on a locked element (move, indent, outdent, wrap, delete) answer "unlock first" in a snackbar.
  * *Properties*: header with the type, the id and a **lock** toggle; under it the **Parent** row (id and type of the parent): tapping it lists the root and every container that is not the element itself or inside it (indented by depth, the current one marked); choosing one moves the element to the end of that container (`UINode.moveTo`; refused moves show a snackbar).
    The **Theme** property in Appearance is a row of preset chips (a dot in the preset's main colour) plus "Same as UI".
  * *Theme tab*: a switch **Whole UI / Selected: id** (disabled when nothing or the root is selected). With *Selected* a tap on a preset chip sets that element's `theme` (the "Same as UI" chip clears it); with *Whole UI* it sets `theme.preset` as before. Colours and shape below belong to the whole UI. The design size is shown in both modes.
  * *Locked elements*: `UiRects.hit(x, y)` skips them (a tap selects the element below, or nothing), `hit(x, y, true)` includes them (Test mode needs them). A locked element selected from the tree is outlined dashed in amber with a lock in its label chip, has no resize handles and cannot be dragged.
  * *Translations*: every visible text goes through `Tr.t("uim_…", English)`. Texts that come from the registries are keyed by `UiTr`: widget name `uim_w_<type>`, widget text `uim_w_<type>_d`, property label `uim_p_<c|type>_<key>` (`c` = a property every widget has), property help `uim_pd_<c|type>_<key>`, enum option / style `uim_o_<option>`, theme role `uim_role_<role>`, theme preset `uim_preset_<name>`, template `uim_tpl_<name>` / `_d`; editing errors `uim_e_*`; layout warnings `uim_lint_*` (`UiLint.check(…, Translator)`). All keys are lower case.
* A new document (`UiOps.newDocument`) has a transparent `Stack` root (`UIDocument.empty` has a filled Panel root).
* **Document**: `UIDocument.read(File)`, `.parse(String)`, `.empty(name, w, h)`, `.write(File)`, `.toJsonString()`, `.find(id)`, `.add(parentId, type, idBase, index)`,
  `.remove(id)`, `.duplicate(id)`, `.uniqueId(base)`, `.makeIdsUnique(sub)`, `.allNodes()`, `.validate()` → list of problems (text for a person/AI), `.warnings` (read problems), `.theme`, `.designWidth/Height`.
* **Node model**: `UINode.create(type, id)` (palette defaults), fields `id,type,anchor,ml/mt/mr/mb,x,y,w,h,visible,enabled,alpha,weight,touchable,props,children,parent`,
  `addChild(child)` / `addChild(index, child)` (**returns the parent**, for chaining), `removeFromParent()`, `moveTo(newParent, index)` (returns an error text when it would create a cycle),
  `copyDeep()`, `find(id)`, `walk(visitor)`, `isContainer()`, `set(key, value)`, `str/num/integer/bool/has`.
  After `UILayout.layout(root, theme, w, h, measurer)` every node has `rx, ry, rw, rh` (parent relative, bottom-left origin) – use it for selection frames and drag handles.
* **Palette / inspector**: `UIWidgetRegistry` – `types()`, `get(type)` → `Widget{type,name,icon,category,desc,container,events,styles,props}`, `all()`, `categories()` (`Layout, Display, Input`),
  `commonProps()`, `propsFor(type)`, `find(type,key)`, and `get(node,key)` / `set(node,key,text)` (**use these for every edit**: they validate, clamp, parse sizes/margins/colours and return `null` or an error message).
  `Prop{key,label,kind,where,def,group,min,max,step,options,advanced,desc}`; `Kind` = `NUMBER, INT, TEXT, MULTILINE, BOOL, ENUM, COLOR, IMAGE, SOUND, FONT, SIZE, ANCHOR, MARGIN, PADDING` (drives the editor widget to show). `Widget.icon` is an id of `assets/icons/catalog.json` (ui set).
  `UIWidgetRegistry.describe()` is the same data as JSON (used by docs and by the AI tool).
* **Preview**: `UIView view = new UIView(projectPath, doc)` (GL thread; call `view.relayout()` after edits or `view.rebuild()` after structural edits), `view.setPlaceholders(true)`.
  The editor item `EditorUIHolderItem` shows how a view is hosted in a libGDX stage. For a Maker running in an Android `Dialog` with its own GL surface use the same `UIView` in a small `Stage`.
* **Themes**: `UITheme.PRESETS`, `.ROLES`, `th.preset = …`, `th.colors/numbers/images` maps, `toJson()/fromJson()`; colour helpers `parseColor, toHex, mix, lighten, darken, withAlpha`.
* **Event files**: `Helpers/UiEventsGen.body(holder, nodeId)` is the "body" argument for `Project.getEventPath(scene, body, event)`; `VisualScriptingDialog.showForUi(holder, nodeId, event)` opens the block editor for it.
  The Events panel already lists inner elements of the selected UIHolder (interactive types and any node that has code).
* **Icons**: `Icon` nodes use a project image (`props.image`); the Maker must export the chosen library icon to `images/` (white PNG, `assets/icons` → `tools/make_icons.py` format) and store its relative path.

---------------------------------------------------------------------------------------------------

## 8. Samples and tests

* `docs/ui-samples/hud.ui` (game HUD), `settings.ui` (every input widget), `levels.ui` (grid in a scroll view), `gallery.ui` (every style preset of every widget side by side).
* `python3 tools/test_ui.py` – compiles, runs `tools/ui/UiTest.java` (registry set/get of every property, JSON round trip, layout math), checks icon ids,
  renders the samples (`tools/ui/UiPreview.java`, AWT, PNGs in `/tmp/uiout`), runs the real runtime headless under Xvfb/Mesa (`tools/ui/run_gdx.sh`: clicks, drags, typing, scroll, tooltip) and checks that this file is up to date.
* After changing `UIWidgetRegistry`: `python3 tools/ui/gen_ui_docs.py` regenerates section 9.
* Not verifiable headless: behaviour on a real Android GL (context loss), TextField with RTL project fonts, soft keyboard. See "phone tests" in the hand-over report.

---------------------------------------------------------------------------------------------------

## 9. Widget reference (generated – do not edit by hand)

<!-- BEGIN WIDGETS -->

### Common properties (every node)

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `id` | node | text | `` |  | Unique name. Used in events (case "id") and in UIView.find(id). Letters, digits, _. |
| `anchor` | node | anchor | `topleft` | topleft, top, topright, left, center, right, bottomleft, bottom, bottomright, stretch, stretchx, stretchy | Where the node sticks inside its parent. |
| `margin` | node | margin | `0` |  | Distance from the parent edges: one number or [left, top, right, bottom]. |
| `x` | node | number | `0` | -4000.0..4000.0 step 1.0 | Extra move to the right after anchoring (px). |
| `y` | node | number | `0` | -4000.0..4000.0 step 1.0 | Extra move UP after anchoring (px). |
| `w` | node | size | `` |  | px ("120"), percent ("50%"), "wrap" or "fill". Empty = default of the type. |
| `h` | node | size | `` |  | px ("120"), percent ("50%"), "wrap" or "fill". Empty = default of the type. |
| `weight` | node | number | `0` | 0.0..20.0 step 0.5 | Share of the free space inside a Linear parent (0 = none). |
| `visible` | node | bool | `true` |  | Hidden nodes are not drawn, take no room in layouts and get no touch. |
| `enabled` | node | bool | `true` |  | false: no events and drawn dimmed. Change at run time with ui.setEnabled(id, v). |
| `interactable` | props | bool | `true` |  | false: visible but ignores touch and is dimmed (design time switch, same effect as enabled=false). |
| `alpha` | node | number | `1` | 0.0..1.0 step 0.05 | 0 transparent .. 1 opaque (applies to the children too). |
| `theme` | props | enum | `` | , jelly, candy, flat_3d, glass_neon, hex_arcade, dark, light, minimal | Look of this element and everything inside it. Empty = the theme of the whole UI (Theme tab). |
| `tint` | props | color | `` |  | Color multiplied over the whole node. Empty = none. |
| `rotation` | props | number | `0` | -360.0..360.0 step 1.0 | Degrees, counter-clockwise, around the pivot. Does not change the layout. |
| `scale` | props | number | `1` | 0.1..5.0 step 0.05 | Scale around the pivot. Does not change the layout. |
| `pivotX` | props | number | `0.5` | 0.0..1.0 step 0.05 | 0 left .. 1 right. |
| `pivotY` | props | number | `0.5` | 0.0..1.0 step 0.05 | 0 bottom .. 1 top. |
| `touchable` | node | enum | `` | , enabled, disabled, childrenOnly | enabled: gets touches (default for widgets) \| disabled: node and children ignore touch \| childrenOnly: only the children get touch. |
| `tag` | props | text | `` |  | Free text you can read with ui.getTag(id) to group nodes. |
| `tooltip` | props | text | `` |  | Text shown while the node is long pressed (optional). |
| `clickSound` | props | sound | `` |  | Sound file of the project (sounds/ folder) played on click, e.g. "click.ogg". |
| `pressScale` | props | bool | `` |  | Shrinks a little while pressed. Default true for Button, false for the others. |
| `action` | props | text | `` |  | Built-in actions, ';' between several: show:id, hide:id, toggle:id, page:id (show it, hide its siblings), tab:id (page + mark this tab), select (mark this button in its row), open:uiName, back, set:id.key=value. |
| `tabOn` | props | text | `` |  | Button style while this tab is selected (tab: action). Empty = primary. |
| `tabOff` | props | text | `` |  | Button style while another tab is selected. Empty = ghost. |

### Panel  (`Panel`)

Box with a background (card, glass, outline...) that holds children placed with anchors.  
Icon id: `square-rounded` · palette category: **Layout** · container: yes · events: `onClick`, `onTouchStart`, `onTouchEnd`

Style presets (`props.style`): `card`, `solid`, `glass`, `outline`, `flat`, `dark`, `transparent`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `style` | props | enum | `card` | card, solid, glass, outline, flat, dark, transparent | Look of the panel. |
| `radius` | props | number | `` | 0.0..200.0 step 1.0 | px. Empty = theme. |
| `panelColor` | props | color | `` |  | Fill color (role or #rrggbb[aa]). Empty = style. |
| `borderColor` | props | color | `` |  | Empty = theme. |
| `border` | props | number | `` | 0.0..20.0 step 1.0 | px. Empty = style. |
| `shadow` | props | number | `` | 0.0..30.0 step 1.0 | px. Empty = style. |
| `image` | props | image | `` |  | Project image or .9.png that replaces the drawn background. |
| `padding` | props | padding | `0` |  | Space between the border and the children: number or [l,t,r,b]. |
| `clip` | props | bool | `false` |  | Hides what leaves the panel. |

### Stack  (`Stack`)

Invisible container: children are placed on top of each other with anchors (HUD layers).  
Icon id: `layers` · palette category: **Layout** · container: yes · events: `onClick`, `onTouchStart`, `onTouchEnd`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `padding` | props | padding | `0` |  | Space around the children: number or [l,t,r,b]. |
| `bgColor` | props | color | `` |  | Optional fill color. Empty = transparent. |
| `clip` | props | bool | `false` |  | Hides what leaves the box. |

### Linear layout  (`Linear`)

Puts children in a row or a column with spacing, alignment and weights.  
Icon id: `view-list` · palette category: **Layout** · container: yes · events: `onClick`, `onTouchStart`, `onTouchEnd`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `orientation` | props | enum | `vertical` | vertical, horizontal | vertical = column, horizontal = row. |
| `spacing` | props | number | `8` | 0.0..200.0 step 1.0 | px between children. |
| `padding` | props | padding | `8` |  | px inside the box: number or [l,t,r,b]. |
| `align` | props | enum | `start` | start, center, end, stretch | Position across the direction. |
| `gravity` | props | enum | `start` | start, center, end, space_between, space_around, space_evenly | Position along the direction when nothing has a weight. |
| `bgColor` | props | color | `` |  | Optional fill color. Empty = transparent. |
| `image` | props | image | `` |  | Optional picture / .9.png. |
| `clip` | props | bool | `false` |  | Hides what leaves the box. |

### Grid  (`Grid`)

Children in rows and columns of equal width (inventory, level select).  
Icon id: `view-grid` · palette category: **Layout** · container: yes · events: `onClick`, `onTouchStart`, `onTouchEnd`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `columns` | props | int | `3` | 1.0..20.0 step 1.0 | Number of columns. |
| `spacing` | props | number | `8` | 0.0..200.0 step 1.0 | px between cells (hspacing / vspacing override one direction). |
| `rowHeight` | props | number | `0` | 0.0..1000.0 step 1.0 | px. 0 = tallest child of the row. |
| `padding` | props | padding | `8` |  | px inside the box. |
| `align` | props | enum | `center` | start, center, end, stretch | Position of a smaller child inside its cell. |
| `bgColor` | props | color | `` |  | Optional fill color. |

### ScrollView  (`ScrollView`)

Scrolls its content by dragging. Put ONE child (usually a Linear or Grid); vertical, horizontal or both.  
Icon id: `unfold-more-horizontal` · palette category: **Layout** · container: yes · events: `onScroll`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `direction` | props | enum | `vertical` | vertical, horizontal, both | Which way the content scrolls. |
| `scrollbar` | props | bool | `true` |  | Shows a thin bar while scrolling. |
| `overscroll` | props | bool | `true` |  | Content may be pulled past the end and springs back. |
| `fling` | props | bool | `true` |  | Keeps moving after a quick swipe. |
| `padding` | props | padding | `0` |  | px around the scrolled content. |
| `bgColor` | props | color | `` |  | Optional fill color. |

### Label  (`Label`)

Text. Supports several lines, wrapping and Arabic shaping with the project RTL font.  
Icon id: `text` · palette category: **Display** · container: no · events: `onClick`, `onTouchStart`, `onTouchEnd`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `text` | props | multiline | `Label` |  | The words. \n makes a new line. |
| `fontSize` | props | number | `` | 8.0..200.0 step 1.0 | px. Empty = theme (28). |
| `color` | props | color | `` |  | Role (text, primary...) or #rrggbb. Empty = theme. |
| `font` | props | font | `` |  | Font json made by the font creator. Empty = theme / default font. |
| `wrap` | props | bool | `false` |  | Breaks long text into lines inside the width. |
| `textAlign` | props | enum | `left` | left, center, right | Horizontal alignment of the lines. |
| `vAlign` | props | enum | `top` | top, middle, bottom | Where the text sits in the box when the box is taller than the text. |
| `autoFit` | props | bool | `true` |  | On by default: makes the letters smaller until the text fits the box (Arabic and English). |
| `minFontSize` | props | number | `12` | 6.0..100.0 step 1.0 | px. Shrink to fit never goes below this. |
| `ellipsis` | props | bool | `false` |  | Cuts a too long line with three dots. |
| `uppercase` | props | bool | `false` |  | Shows the text in capital letters (the stored text is not changed). |
| `textOpacity` | props | number | `100` | 0.0..100.0 step 5.0 | 0 - 100 percent. |
| `shadow` | props | bool | `false` |  | Dark drop shadow behind the letters. |

### Image  (`Image`)

Picture from the project's images folder (also .9.png).  
Icon id: `image` · palette category: **Display** · container: no · events: `onClick`, `onTouchStart`, `onTouchEnd`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `image` | props | image | `` |  | Project image path, e.g. "/hero.png". |
| `scaling` | props | enum | `fit` | fit, fill, stretch, none | fit keeps the shape, fill covers the box, stretch distorts, none = real size. |
| `flipX` | props | bool | `false` |  | Mirror left/right. |
| `flipY` | props | bool | `false` |  | Mirror up/down. |

### Icon  (`Icon`)

Small one-color picture (from the icon library) painted with the color property.  
Icon id: `star` · palette category: **Display** · container: no · events: `onClick`, `onTouchStart`, `onTouchEnd`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `image` | props | image | `` |  | Project image of the icon (white icons from the icon library). |
| `color` | props | color | `text` |  | Role or #rrggbb. |

### Progress bar  (`Progress`)

Bar filled up to value/max (health, loading, XP).  
Icon id: `battery-medium` · palette category: **Display** · container: no · events: none

Style presets (`props.style`): `classic`, `jelly`, `neon`, `minimal_line`, `thick_candy`, `game_bar`, `health`, `xp`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `value` | props | number | `50` |  | Current value. |
| `min` | props | number | `0` |  | Smallest value. |
| `max` | props | number | `100` |  | Largest value. |
| `style` | props | enum | `classic` | classic, jelly, neon, minimal_line, thick_candy, game_bar, health, xp | Look of the bar. |
| `trackColor` | props | color | `` |  | Empty = style. |
| `fillColor` | props | color | `` |  | Empty = style. |
| `trackHeight` | props | number | `` | 1.0..100.0 step 1.0 | px. Empty = style. |
| `trackImage` | props | image | `` |  | Picture / .9.png for the empty part. |
| `fillImage` | props | image | `` |  | Picture / .9.png for the filled part. |
| `vertical` | props | bool | `false` |  | Fills from bottom to top. |
| `showText` | props | bool | `false` |  | Writes the value on the bar. |
| `textFormat` | props | enum | `percent` | percent, value, fraction | percent: 50% \| value: 50 \| fraction: 50/100 |
| `animate` | props | bool | `true` |  | Moves smoothly to a new value. |

### Button  (`Button`)

Pressable button with text and/or icon. Scales down a little while pressed.  
Icon id: `button-a` · palette category: **Input** · container: no · events: `onClick`, `onTouchStart`, `onTouchEnd`

Style presets (`props.style`): `primary`, `secondary`, `accent`, `success`, `danger`, `ghost`, `flat`, `outline`, `neon`, `glass`, `pill`, `jelly`, `soft`, `square`, `hex`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `text` | props | multiline | `Button` |  | The words. \n makes a new line. |
| `fontSize` | props | number | `` | 8.0..200.0 step 1.0 | px. Empty = theme (28). |
| `color` | props | color | `` |  | Role (text, primary...) or #rrggbb. Empty = theme. |
| `font` | props | font | `` |  | Font json made by the font creator. Empty = theme / default font. |
| `autoFit` | props | bool | `true` |  | On by default: makes the text smaller when it is wider than the button. |
| `style` | props | enum | `primary` | primary, secondary, accent, success, danger, ghost, flat, outline, neon, glass, pill, jelly, soft, square, hex | Color variant or look. |
| `shape` | props | enum | `` | , round, pill, square, cut | Corner shape. Empty = theme. |
| `buttonColor` | props | color | `` |  | Fill color (role or #rrggbb[aa]). Empty = style. |
| `icon` | props | image | `` |  | Small picture next to the text. |
| `iconPosition` | props | enum | `left` | left, right, top |  |
| `image` | props | image | `` |  | Picture / .9.png replacing the drawn button. |
| `pressedImage` | props | image | `` |  | Picture shown while pressed. Empty = name_pressed.9.png if it exists, else the normal one. |
| `radius` | props | number | `` | 0.0..200.0 step 1.0 | px. Empty = theme. |
| `callClick` | props | text | `` |  | Calls a method of a scene element when clicked (set with the editor). |
| `callStart` | props | text | `` |  | Calls a method of a scene element when the touch starts. |
| `callEnd` | props | text | `` |  | Calls a method of a scene element when the touch ends. |
| `pressScaleAmount` | props | number | `0.94` | 0.5..1.0 step 0.01 | Size while pressed (1 = no effect). |

### Slider  (`Slider`)

Drag the knob to choose a number between min and max.  
Icon id: `tune` · palette category: **Input** · container: no · events: `onChange`

Style presets (`props.style`): `classic`, `jelly`, `neon`, `minimal_line`, `thick_candy`, `game_bar`, `round_knob`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `value` | props | number | `0.5` |  | Current value. |
| `min` | props | number | `0` |  | Smallest value. |
| `max` | props | number | `1` |  | Largest value. |
| `style` | props | enum | `classic` | classic, jelly, neon, minimal_line, thick_candy, game_bar, round_knob | Look of track, fill and knob. |
| `trackColor` | props | color | `` |  | Empty = style. |
| `fillColor` | props | color | `` |  | Empty = style. |
| `trackHeight` | props | number | `` | 1.0..100.0 step 1.0 | px. Empty = style. |
| `trackImage` | props | image | `` |  | Picture / .9.png for the empty part. |
| `fillImage` | props | image | `` |  | Picture / .9.png for the filled part. |
| `knobColor` | props | color | `` |  | Empty = style. |
| `knobSize` | props | number | `` | 4.0..150.0 step 1.0 | px diameter. Empty = style. |
| `knobImage` | props | image | `` |  | Picture for the knob. |
| `step` | props | number | `0` | 0.0..1000.0 step 0.01 | Size of one step. 0 = smooth. |
| `snapToStep` | props | bool | `true` |  | Knob jumps from step to step. false: only the value is rounded. |
| `vertical` | props | bool | `false` |  | Bottom = min, top = max. Give it a tall size. |
| `showValueLabel` | props | bool | `false` |  | Writes the value above the knob. |
| `decimals` | props | int | `0` | 0.0..6.0 step 1.0 | Digits after the point in the value label. |
| `changeMode` | props | enum | `continuous` | continuous, release | continuous: while dragging \| release: only when the finger lifts. |

### Checkbox  (`Checkbox`)

Box with a tick and a text.  
Icon id: `checkbox-marked` · palette category: **Input** · container: no · events: `onChange`, `onClick`

Style presets (`props.style`): `classic`, `jelly`, `neon`, `minimal`, `square`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `text` | props | text | `Checkbox` |  | Words next to the box. |
| `checked` | props | bool | `false` |  | Current state. |
| `style` | props | enum | `classic` | classic, jelly, neon, minimal, square | Look of the box. |
| `boxColor` | props | color | `` |  | Empty = style. |
| `checkColor` | props | color | `` |  | Empty = style. |
| `boxSize` | props | number | `` | 12.0..120.0 step 1.0 | px. Empty = from the font size. |
| `fontSize` | props | number | `` | 8.0..200.0 step 1.0 | px. Empty = theme. |
| `color` | props | color | `` |  | Role or #rrggbb. Empty = theme. |
| `font` | props | font | `` |  | Font json. Empty = theme / default. |

### Radio button  (`Radio`)

Round choice; only one radio of the same group is on.  
Icon id: `radiobox-marked` · palette category: **Input** · container: no · events: `onChange`, `onClick`

Style presets (`props.style`): `classic`, `jelly`, `neon`, `minimal`, `square`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `text` | props | text | `Radio` |  | Words next to the box. |
| `checked` | props | bool | `false` |  | Current state. |
| `style` | props | enum | `classic` | classic, jelly, neon, minimal, square | Look of the box. |
| `boxColor` | props | color | `` |  | Empty = style. |
| `checkColor` | props | color | `` |  | Empty = style. |
| `boxSize` | props | number | `` | 12.0..120.0 step 1.0 | px. Empty = from the font size. |
| `fontSize` | props | number | `` | 8.0..200.0 step 1.0 | px. Empty = theme. |
| `color` | props | color | `` |  | Role or #rrggbb. Empty = theme. |
| `font` | props | font | `` |  | Font json. Empty = theme / default. |
| `group` | props | text | `group1` |  | Radios with the same group name exclude each other (the group is the whole .ui). |

### Switch  (`Switch`)

On/off toggle.  
Icon id: `toggle-switch` · palette category: **Input** · container: no · events: `onChange`, `onClick`

Style presets (`props.style`): `classic`, `jelly`, `neon`, `minimal`, `square`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `checked` | props | bool | `false` |  | Current state. |
| `style` | props | enum | `classic` | classic, jelly, neon, minimal, square | Look of the switch. |
| `trackOnColor` | props | color | `` |  | Track color when on. Empty = style. |
| `trackOffColor` | props | color | `` |  | Track color when off. Empty = style. |
| `knobColor` | props | color | `` |  | Empty = style. |
| `knobScale` | props | number | `` | 0.3..1.0 step 0.05 | Knob diameter as part of the height (0.5 .. 1). |

### Text field  (`TextField`)

Single line text input with the on-screen keyboard.  
Icon id: `keyboard` · palette category: **Input** · container: no · events: `onChange`, `onSubmit`

Style presets (`props.style`): `classic`, `jelly`, `neon`, `minimal_line`, `dark`, `flat`

| key | where | kind | default | range / options | description |
|---|---|---|---|---|---|
| `text` | props | text | `` |  | Current content. |
| `hint` | props | text | `` |  | Grey text shown while empty. |
| `style` | props | enum | `classic` | classic, jelly, neon, minimal_line, dark, flat | Look of the box. |
| `password` | props | bool | `false` |  | Shows dots instead of letters. |
| `numeric` | props | bool | `false` |  | Only digits, point and minus. |
| `maxLength` | props | int | `0` | 0.0..1000.0 step 1.0 | 0 = unlimited. |
| `readOnly` | props | bool | `false` |  | Shows the text but cannot be edited. |
| `fontSize` | props | number | `` | 8.0..200.0 step 1.0 | px. Empty = theme. |
| `color` | props | color | `` |  | Role or #rrggbb. Empty = theme. |
| `font` | props | font | `` |  | Font json made by the font creator. Empty = theme / default. |
| `bgColor` | props | color | `` |  | Empty = style. |
| `borderColor` | props | color | `` |  | Empty = style. |
| `radius` | props | number | `` | 0.0..100.0 step 1.0 | px. Empty = style. |

Theme presets: `jelly`, `candy`, `flat_3d`, `glass_neon`, `hex_arcade`, `dark`, `light`, `minimal`

<!-- END WIDGETS -->
