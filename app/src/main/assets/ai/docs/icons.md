# Icons: the `icons` tool

About 1800 small icons in two sets, drawn on demand into the project's `images/` folder as PNG. Use them for HUD symbols, glyphs on buttons, menus, inventories, item and enemy pictures. Do not draw such things with `create_image`.

- `ui` set (about 1300, Material Design Icons): heart, star, sound, check, close, arrows, controls, shop, weather, emoji...
- `game` set (about 520, game-icons.net): swords, axes, bows, bombs, shields, helmets, crowns, gems, chests, potions, scrolls, wands, skulls, ghosts, dragons, robots, tanks, spaceships, flames, castles, animals... More detailed, filled pictures, good for items and characters.

## Quick start

1. `icons {"action":"search","query":"love"}` returns matching ids (`heart`, `heart-outline`...) with a few tags. Every icon has many English tags, so several names find it (`love`, `like`, `life`, `health` -> heart; `settings` -> cog; `exit` -> exit-run, logout).
   Add `"set":"game"` to search only the game set (`icons {"action":"search","query":"sword","set":"game"}`). Game-set ids that already exist in the ui set end with `-game` (for example `shield-game`, `crown-game`).
2. `icons {"action":"import","id":"heart","size":64,"color":"#FF4081"}` writes `images/icon_heart.png` and answers `use_as: "/icon_heart.png"`.
3. `elements` add: BOX, `image:"/icon_heart.png"`, width/height at least as big as you want it shown (a 64 px icon looks sharp up to about 64 px).

## Import options

| field | meaning |
|---|---|
| `id` | icon id from `search` (or `icons:[{id,...},...]` for up to 40 at once; top-level `size`/`color` are defaults) |
| `name` | file name without extension; default `icon_<id>` (dashes become `_`) |
| `size` | 16-512 px, default 64 |
| `color` | `#RRGGBB`, `#AARRGGBB` or a name (white, red, gold, ...); default white |
| `background` | `circle` or `rounded`: the icon on a coloured badge; `background_color` (default `#2196F3`) |
| `padding` | 0-0.4, empty border as a fraction of the size |
| `shadow` | soft drop shadow |
| `replace` | overwrite a file that exists; automatic when you did not pass `name` |

An existing file with a name YOU chose is protected: pass `replace:true` to overwrite it or choose another name.

## Colours

Icons are white by default. Either import them in the colour you want (`color`) or keep one white icon and recolour each element with its `Tint` property (`RRGGBBAA`). Several colours of the same icon: import with different `name`s.

## Search tips

- Use one or two simple words: `sound`, `exit door`, `coin`, `arrow left`. All words must match.
- Nothing found: try a related word, or `icons {"action":"list_categories"}` then `search` with `category`.
- ui categories: Controls, Game, Arrows, Audio, Shapes, Status, Shop & Money, System, People, Nature, Animals, Weather, Objects, Food, Sport, Emoji, Travel, Time, Math. game categories: Weapons, Armor, Treasure, Magic, Monsters, Heroes, Items, Vehicles, Elements, Board & Cards, Buildings, Wildlife.
- Outline variants exist for the core icons (`heart-outline`, `star-outline`, ...): use them for empty slots (lives lost, stars not earned).

## Typical HUD

lives: three BOX `icon_heart` in a row (use `heart-outline` when lost); score: `icon_star` next to a TEXT; sound toggle: `volume-high` / `volume-off`; pause: `pause`; settings: `cog`; back: `arrow-left`; shop: `cart`; coin counter: `coin`.

## Credits

The game set is from game-icons.net (Lorc, Delapouite & contributors, CC BY 3.0): a game that uses its icons should credit it, for example on a credits screen: "Icons by Lorc, Delapouite & contributors from game-icons.net, CC BY 3.0". The ui set (Material Design Icons, Apache-2.0) needs no credit in the game. Tell the user when you used game-set icons.
