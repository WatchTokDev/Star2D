# Buttons and panels: the `buttons` tool

Makes professional UI images with a gradient, outline, shadow, 3D lip and shine, without uploading anything. Buttons have NO text: put a TEXT element on top (see "Use it").

## Quick start

1. `buttons {"action":"list"}`: presets, shapes, colour names, parameter names (once per project is enough).
2. `buttons {"action":"create","name":"btn_play","preset":"jelly","color":"green"}`: the answer has `use_as` (`/btn_play.9.png`).
3. `elements`: BOX, `type:"UI"`, `image:"/btn_play.9.png"`, any width/height, then the TEXT label above it.

Pass only what you want to change; every other parameter keeps the preset default. `replace:true` overwrites an existing image of that name (and its pressed twin). By default a pressed twin is made too: `<name>_pressed.9.png` (same shape, darker and less saturated, shine and shadow reduced, the 3D lip pushed in); `pressed:false` skips it. The reply lists `use_as` and `pressed_use_as`. `preview:true` returns a picture of the result.

## Presets

| id | look | default colour |
|---|---|---|
| `jelly` | glossy rounded, white outline (jelly UI kit) | blue |
| `candy` | pill, thick white outline, soft gradient | red |
| `flat_3d` | solid colour with a darker 3D bottom edge (Duolingo style) | #1CB0F6 |
| `glass_neon` | dark glass, thin glowing coloured border, shiny top half | orange |
| `hex_arcade` | arcade hexagon, black frame, pink outline | orange |
| `flat` | plain rounded rectangle | blue |
| `ghost` | transparent with coloured outline | white |
| `panel_dark`, `panel_light` | dialog / card / HUD background | dark / light grey |
| `jelly_pill`, `jelly_square`, `jelly_circle`, `jelly_blob`, `jelly_arrow_left`, `jelly_arrow_right` | jelly in other shapes (circle, pentagon, triangles are fixed shapes) | |

Same look, other hue: `color` = `#RRGGBB` or a name (`blue green orange red purple cyan pink yellow gray navy teal lime white black`); the preset derives its light/dark shades from it. `color2` = gradient end colour. `colors` = `["#fff","#09f","#00a"]` sets the fill gradient exactly (1-3 colours), `angle` its direction (90 = top to bottom, 0 = left to right).

## Shapes and stretching

- `rect rounded pill chamfer`: saved as `name.9.png` (a 9-patch): the BOX can be ANY size >= `min_size` (reply field); corners, outline, shadow and lip keep their pixel size, only the middle stretches. A button much taller than `base_size` stretches its gradient: make several sizes if you need very different proportions (`width`/`height` set the base size in px, 24..512).
- `circle blob triangle_left triangle_right triangle_up triangle_down`: fixed shapes, saved as `name.png`; give the BOX the same aspect (1:1) or a uniform scale of `base_size`.
- Do NOT set `Tint` on a button BOX (it multiplies the colours); change the look with `color`.

## All parameters (all optional)

| key | meaning (default comes from the preset) |
|---|---|
| `preset`, `shape`, `color`, `color2`, `colors`, `angle` | look, geometry, fill colours |
| `width`, `height` | base bitmap size in px (shadow margin and lip included) |
| `radius` | corner radius px (rounded); `chamfer` cut size px (chamfer) |
| `outline` `outline_w` | outermost ring colour and width px |
| `frame` `frame_w` | second ring (black frame of `hex_arcade`) |
| `border` `border_w` | third ring, just outside the fill (inner coloured border) |
| `shadow` `shadow_dx` `shadow_dy` `shadow_blur` | drop shadow colour (`#AARRGGBB` ok), offset, blur |
| `lip` `lip_color` | 3D bottom edge height px; colour (default: darker fill) |
| `glow` `glow_blur` | outer neon glow |
| `gloss` | `none top_half top_band top_arc top_left_ellipse streak bottom_glow` |
| `gloss_strength` `gloss_height` `gloss_y` `gloss_inset` `gloss_color` | shine 0..1, height as a fraction of the button, vertical offset fraction, side inset px |
| `inner_glow` `inner_glow_w` | soft light along the inner edge |
| `bevel` | 1-2 px light top / dark bottom edge |

Colours: `#RRGGBB`, `#AARRGGBB`, a name, or `none` (switches a ring/shadow/glow off).

## Use it

```json
{"ops":[
 {"op":"add","type":"BOX","name":"playBtn","props":{"x":260,"y":300,"width":200,"height":80,"type":"UI","image":"/btn_play.9.png","z":100}},
 {"op":"add","type":"TEXT","name":"playLbl","props":{"x":260,"y":300,"width":200,"height":80,"Text":"PLAY","z":110}}
]}
```
(`elements` tool; exact property names in `read_docs {topic:'elements'}`.) Pattern, with the same x/y/width/height for both:
- BOX `playBtn`: `type:"UI"`, `image:"/btn_play.9.png"`, `z:100`, the touch event (`onClick` for menus, `onTouchStart` for game controls).
- TEXT `playLbl`: same position and size, `z:110`, text, `Font Scale`; no events (it is click-through, the tap reaches the BOX). Keep the text inside the content area given in the reply (`Label area`).
- Pressed look: set the BOX property `Pressed Image` to the pressed twin; the engine shows it while the finger is down and inside the button and restores the normal image on release, drag-out or cancel. No events needed, and the user's own `onClick` / `onTouchStart` / `onTouchEnd` still run:
```json
{"ops":[{"op":"set","name":"playBtn","props":{"image":"/btn_play.9.png","Pressed Image":"/btn_play_pressed.9.png"}}]}
```
  `Touch Start Image` / `Touch End Image` (same format) are applied at touch start / end and STAY until something changes the image. Remove one with `reset:["Pressed Image"]` in a `set` op. Alternative with events (the image then changes by blocks): `onTouchStart` -> `setImage` pressed, `onTouchEnd` -> `setImage` normal, verified block `setImage`:
```json
{"ops":[
 {"op":"set","scope":"element","element":"playBtn","event":"onTouchStart","blocks":[{"block":"setImage","fields":{"body":"playBtn","image":"btn_play_pressed.9.png"}}]},
 {"op":"set","scope":"element","element":"playBtn","event":"onTouchEnd","blocks":[{"block":"setImage","fields":{"body":"playBtn","image":"btn_play.9.png"}}]}
]}
```
  (`events` tool.) Keep the TEXT label still, or move it down about 2 px in the same events if you want it to look pushed in.

Edit later: `buttons {"action":"get","name":"btn_play"}` returns the stored settings; `create` again with the changed values and `replace:true`. The user can also open the Button Maker (Files panel -> + -> Button Maker) and edit the same image by hand.
