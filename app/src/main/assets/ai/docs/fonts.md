# Fonts: the `fonts` tool (.s2df)

A TEXT element normally uses the small built-in font (ASCII only). A **.s2df** file is a Star2D font: a tiny JSON that points at a TTF/OTF and says how to draw it. The game builds the bitmap font with FreeType when the TEXT loads it, so there are no generated glyph files. You make one with the `fonts` tool, then give the TEXT element `font: "/fonts/<name>.s2df"`.

## Quick start

1. `fonts {"action":"list"}` returns the presets (id + look), the real parameters with ranges, and the bundled TTFs.
2. `fonts {"action":"create","name":"title_font","preset":"gold_royal","element":"TitleText"}` writes `fonts/title_font.s2df`, copies the TTF next to it, and sets that TEXT's `font`. Pass only what you want to change: `{"preset":"gold_royal","size":80,"borderColor":"#401800"}`. Anything you do not pass keeps the preset.
3. Size the TEXT so it does not wrap: `fonts {"action":"measure","name":"title_font","text":"GAME OVER","font_scale":1}` answers the width and height to give the TEXT (the layout warning does not measure custom fonts).
4. `screenshot` to check it.

Without `element`, set it yourself: `elements set {name, props:{font:"/fonts/title_font.s2df"}}`. `fonts apply {name, element}` re-applies an existing font. `fonts get {name}` reads one back. A name that exists needs `replace:true`.

## What a font can look like

FreeType has no gradient and no glow. Every look is made of: **fill colour** (`color`), **one outline** (`borderWidth` + `borderColor`), **one drop shadow** (`shadowOffsetX/Y` + `shadowColor`; both offsets 0 = no shadow; +X right, +Y down), letter spacing (`spaceX`) and smoothing (`hinting`, `mono`). Glow-like looks use a bright outline plus a faint shadow (see `neon_*`). Hollow (outline-only) text is not possible.

The fill is multiplied by the TEXT's `Text Color` (default white), so a white-filled font can be tinted per element. A coloured fill (gold, red...) is already coloured: keep Text Color white to see it as made.

## Parameters

(Run `fonts list` for the exact current list with ranges; these are the ones that matter.)

| param | meaning |
|---|---|
| `size` | glyph height in px at Font Scale 1 (8..160). Bigger is sharper and uses more memory: make it the size you need, use `Font Scale` 1 |
| `color`, `borderColor`, `shadowColor` | `#RRGGBB` or `#RRGGBBAA` or a name (white, black, gold...) |
| `borderWidth` | outline px (0 = none); typical 3-5 on size 48-60 |
| `shadowOffsetX/Y`, `shadowColor` | drop shadow |
| `spaceX`, `spaceY` | extra letter / line spacing px |
| `mono` | true = no anti-aliasing (pixel look; also set `hinting:"None"`, `minFilter/magFilter:"Nearest"`) |
| `hinting` | `None`/`Slight` smoother large text, `Full`/`Auto*` crisper small text |
| `kerning`, `gamma`, `borderGamma`, `renderCount`, `pad*`, `minFilter`, `magFilter` | fine tuning, rarely needed |

## Presets (ids)

arcade_outline (default, white + black outline), comic_pop, pixel_retro, terminal_green, neon_cyan, neon_pink, gold_royal, silver_metal, ice_frost, fire_hot, candy_pink, grass_green, magic_purple, horror_blood, chalk_board, cartoon_shadow, dark_white_stroke, minimal_clean, hud_score (compact numbers), title_giant (size 96), dark_on_light, arabic_clean, arabic_gold.

Picking: titles -> gold_royal / title_giant / comic_pop; score and timers -> hud_score; menus -> minimal_clean / arcade_outline; retro games -> pixel_retro; sci-fi -> neon_*; kids -> candy_pink / comic_pop; any busy background -> a preset with an outline.

## Arabic and other alphabets

- `arabic_clean` / `arabic_gold` use the bundled **Parvin-Regular.ttf** with `rtl:true` (right-to-left, letters joined). `DroidSans` and `VisOpenSans` have NO Arabic letters (boxes). Any preset can be made Arabic: `{"preset":"gold_royal","rtl":true}` switches to Parvin automatically.
- Your own Arabic font: put the .ttf/.otf in the project (e.g. `/fonts/MyArabic.ttf`) and pass `ttf:"/fonts/MyArabic.ttf", rtl:true`.
- The glyph set is fixed: Latin (with accents), digits, punctuation, plus the Arabic letters when `rtl` is true. Cyrillic, Greek, Chinese, Japanese and Korean are not generated.
- Write Arabic text in the TEXT's `Text` property as normal Unicode (logical order); do not reverse it yourself.

## Rules of thumb

- One font file per look. Reuse it on many TEXT elements; change only `Text Color` (tint), `Font Scale` (a little, 0.6..1.5) and the box size.
- A big `size` multiplies memory (a 160 px font is a big texture): do not make five giant fonts.
- After changing a font with `replace:true` the editor reloads the TEXT elements; run the game to see it.
- The TTF is copied into `fonts/` and ships with the game. Use only fonts you may redistribute.
