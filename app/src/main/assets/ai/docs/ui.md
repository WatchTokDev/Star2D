# ui: menus, HUDs, dialogs as UI files

A **UI** is one screen (HUD, pause menu, settings, dialog, game over...) stored in `UI/<name>.ui`. The user edits it in the **UI Maker** (Files panel > `UI/` > the file, or UIHOLDER Properties > Edit); you build and edit the same file with the `ui` tool. It is resolution independent (anchors, margins, Linear/Grid layouts, percentages) and themed, so prefer it to building menus from BOX + TEXT elements.

## Workflow
1. `ui {action:'list'}`: existing screens, templates, widgets, themes.
2. `ui {action:'create', name:'pause', template:'pause_menu', theme:{preset:'candy'}}`: the reply has the outline, `warnings` and what to do next. Templates: `hud, pause_menu, settings_panel, dialog_box, controls` (joystick-style movement + action buttons), `main_menu, game_over, level_select`. `templates:[...]` adds several. `orientation` defaults to the project's (portrait 720x1280, landscape 1280x720 design size).
3. `ui {action:'get', name}`: outline, one line per node (`id Type anchor=.. margin=.. w=.. h=.. text="..."`), indented by parent; `size:[w,h]` adds the computed rectangles (px from the top-left).
4. `ui {action:'set', name, ops:[...]}`, then read `warnings`.
5. Show it in a scene: `elements add {type:'UIHOLDER', name:'pause_ui', ui:'pause', x:0, y:0, width:<frame w>, height:<frame h>}` (fit:'stretch' by default; `contain`/`cover` keep the aspect). Then `layout_check` is not needed for the inside of a UI: use `ui check`.
6. Game logic: `ui {action:'event', scene, holder:'pause_ui', node:'btnResume', event:'onClick', code:'...'}` writes Java (variables `ui`, `id`; `value` in onChange, `text` in onSubmit, `x`,`y` in onScroll; plain statements). Omit `code` to read it. Events: `onClick onTouchStart onTouchEnd onChange onSubmit onScroll` (only those the widget has, see `describe`). In other scripts: `((UIHolder) pause_ui).ui()` returns the UIView.

## Widgets (15)
Layout: `Panel` (background + stacked children), `Stack`, `Linear` (orientation, spacing, padding, align, gravity, children with `weight`/`w:'fill'`), `Grid` (columns, spacing), `ScrollView` (direction, ONE content child, usually a Linear). Display: `Label`, `Image`, `Icon`, `Progress`. Input: `Button`, `Slider`, `Checkbox`, `Radio`, `Switch`, `TextField`.
`ui describe {type:'Slider'}` lists every property (kind, default, range, options). Never guess a property name: an unknown property or bad value comes back as an error naming the valid ones; numbers outside the range are clamped to it.

## Node basics
`{id, type, anchor, margin, x, y, w, h, weight, visible, enabled, alpha, props:{...}, children:[...]}`.
- `anchor`: topleft (default) top topright left center right bottomleft bottom bottomright stretch stretchx stretchy. Positions are relative to the PARENT's content box, origin bottom-left, **y up** (`y:20` moves up 20).
- `w`/`h`: px number, `"50%"`, `"wrap"`, `"fill"`. Missing = widget default (Button 160x56, ...).
- Prefer anchors + margins + Linear over x/y; use `x`/`y` only for small nudges.
- Colours: `#RRGGBB[AA]` or a theme role (`primary secondary accent success danger background surface surfaceAlt text textDim border`): roles follow the theme.
- Images: project path like `/btn.png` (from `images/`); icons: import with `icons import`, then set `image`.
- `id`: letters, digits, `_`, unique. The ids are what events and scripts use.

## ops for `ui set`
- `{op:'add', parent:'root', type:'Button', id:'btnPlay', index?:0, anchor:'center', w:240, h:72, props:{text:'Play', style:'primary'}, children?:[...]}`. Props may be in `props` or loose in the op.
- `{op:'set', id:'btnPlay', props:{text:'Start'}, anchor:'bottom', margin:[0,0,0,40]}`; `rename:'newId'` renames.
- `{op:'remove', id}`, `{op:'duplicate', id}`, `{op:'move', id, parent, index}`, `{op:'wrap', id, type:'Panel'}` (puts the node inside a new container).
- `theme:{preset:'dark', overrides:{primary:'#ff8800', radius:12}}` (presets: jelly, candy, flat_3d, glass_neon, hex_arcade, dark, light, minimal).
Ops apply in order; a failed op is listed in `errors` and the others are still saved.

## Layout warnings (ui check, also in every reply)
Checked at the design size in portrait AND landscape: `off-screen`, `touch target < 44px` (buttons, text fields), text wider than a fixed width, overlapping interactive widgets. Text widths are approximate (no GL); look at the result on the phone with `screenshot` of the scene when it matters. Fix by anchoring (not hard-coded x/y), `fill`/`%` sizes or a ScrollView.

## Tips
- Start from a template and edit it; it already uses sensible anchors for both orientations.
- A HUD sits in the corners (`topleft`, `topright`) with margins >= 16; big buttons for thumbs at the bottom corners.
- Dialogs: a `Stack` root child filling the screen with a dimmed Panel, and a centred card.
- Slider/Switch/Checkbox values live in the widget; read them in `onChange` (`value`) or `ui.getValue(id)`.
