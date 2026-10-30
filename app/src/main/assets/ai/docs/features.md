# Newer features (one page)

## Elements
- **Pivot** (all elements, UI holder): `Pivot` = center | left | right | top | bottom | topleft | topright | bottomleft | bottomright | custom; `Pivot X` / `Pivot Y` (0..1) only for `custom`. Scale and rotation turn around it. Bodies that rotate: keep `center`.
- **Y Sort** (GROUP or any parent, `"true"`): all descendants are drawn by height, the lower on screen in front (z first, then y). Put units, trees and pickups under one group; use `Pivot bottom` so the feet decide.
- **animation** (BOX): name of a file in `anims/` (inner folders: `hero/run`). Beats `image`, shows live in the editor. At runtime: blocks `setAnimation` / `removeAnimation`.
- **Auto Size** (TEXT, UI holder label/button, default on): the text size follows the box and never leaves it; `Font Scale` is ignored while it is on.

## Animations from sprite sheets
Frame = `{"name":"sheet.png","dur":"100","region":[x,y,w,h]}` (region optional; part of the image). A row of equal frames: one frame per `x = i*w`. Users cut sheets in the Animation page (grid / cell size / auto cut); from `files` write the JSON directly.

## Translations
`strings/strings.json`: `{"default":"en","languages":["en","ar"],"editor":"ar","keys":{"play":{"en":"Play","ar":"العب"}}}`. A TEXT whose `Text` is `@play` shows the current language and follows changes; the editor shows the first language (or `editor`). Blocks (category Language): `setLanguage`, `nextLanguage`, `showTranslated`, `ifLanguage`; values `translate`, `current language`. Choice is remembered by the game. Arabic needs a font with Arabic letters (`fonts`, preset `arabic_clean`).

## Blocks that document
`explain`, `explainIf`, `explainIfElse` generate no code: the `i` button shows the author's note. Use them to describe a script to the user.

## Export / assets
- Export option "Merge images into tilesets": images up to 512 px are packed into pages (`files/_atlas.json`); the project is not changed. Nothing to do from tools.
- Collections (free art packs) are copied into `images/` by the user from the toolbar.
