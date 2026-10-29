# Game templates: the `templates` tool

Ready, playable games built in ONE call, with real elements and blocks (everything stays editable). Use a template whenever the request matches one: it gets orientation, layering (z), physics reach, touch controls and a restart panel right.

## Use it

1. `templates {"action":"list"}`: short catalog (id, one line, params with defaults).
2. `templates {"action":"apply","id":"platformer"}`: builds it in the open scene when that is empty, else in a NEW scene named after the template. Optional: `scene` (name; an existing non-empty scene needs `"replace":true`, which deletes and recreates it), `orientation` (`portrait`|`landscape`; default = the scene's / project's), `params` (below).
3. The reply lists the scene, orientation and size, created names (`Brick1..Brick40 (40)`), `tweak` (variables to change), the params actually used and `notes` (values that were clamped or ignored), `failed`, `warnings`.
4. `run_game`, then `screenshot`. Change numbers with `variables set` / `elements set`, not by rebuilding.
5. `templates {"action":"info","id":"..."}`: what it creates and which variables to tweak.

Rules: the game always starts at `scene1`. Text must be plain ASCII (default font). Params outside the range are clamped (see `notes`). Layers: background 0, ground 10, items 20, actors 30, effects 40, controls 100, HUD 110, joystick 120, end panel 200+.

## Catalog

| id | what you get |
|---|---|
| `platformer` | side-view jump & run, scrolling level, pits, stairs, coins, patrolling enemies (stomp), goal, lives, win/lose panel, Left/Right/Jump buttons |
| `top_down` | one-screen arena, joystick hero, gems, crates, chasing enemies, lives, win/lose panel |
| `space_shooter` | vertical shooter, drag to steer, auto-fire, faster aliens, score + saved best, lives |
| `flappy` | tap to flap, endless pipes, score + saved best |
| `breakout` | brick breaker, drag paddle, tap to launch, coloured rows, lives, win/lose |
| `main_menu` | title, PLAY (opens `play_scene`), optional QUIT. Apply it to `scene1` |

## Params (default; range)

`theme` is `day | night | sunset` unless noted. `win_text`: ASCII, max 14 chars.

**platformer**: `length` 3 (2..6 screens), `lives` 3 (1..9), `enemies` 3 (0..6), `theme` day, `win_text`.
Variables: `jumpPower` (jump height = jumpPower^2/1400), `moveSpeed`, `enemySpeed`, `lives`, `spawnX/spawnY`. Raising gravity or lowering jumpPower can make the level impossible (steps 100 px, gaps 190 px, jump 138 px).

**top_down**: `gems` 8 (3..20), `enemies` 2 (0..5), `lives` 3, `theme` grass (`grass | dungeon | desert`), `win_text`.
Variables: `heroSpeed` 220, `foeSpeed` 110, `lives`, `spawnX/spawnY`. Chasers have no path finding and may stick behind a crate.

**space_shooter**: `lives` 3, `spawn` 1 (0.4..3 s between aliens), `fire` 0.3 (0.15..1 s between shots), `theme` space (`space | nebula | mars`).
Variables: `bulletSpeed` 420, `alienSpeed` 110 (+12 / 10 s, max 220), `shipSpeed` 400, `lives`. Spawn/fire timers are the two `everySeconds` blocks in OnCreate.

**flappy**: `gap` 300 (220..420 px), `speed` 200 (120..300 px/s), `theme`.
Variables: `flapPower` 380, `pipeSpeed` 200, `gap`. Pipes wait for the first tap.

**breakout**: `rows` 5 (3..8, fewer on short screens), `cols` 8 (5..10), `lives` 3, `speed` 380 (250..440), `theme` night.
Variables: `ballSpeed` (real speed, re-set every frame), `lives`.

**main_menu**: `title` "MY GAME" (ASCII, 20), `subtitle` (30, empty = none), `play_scene` scene2, `quit` 1 (0|1), `theme` night.
PLAY runs `openScene(play_scene)`; that scene must exist before the game runs.

## Notes

- Applying again to a scene that already holds a template needs `replace:true` (or another `scene`).
- Mixing: apply a template, then add your own elements/events on top (names above are the handles). Collision handlers only set flags; teleporting/creating happens in OnStep.
- Portrait vs landscape: sizes come from the real scene frame, so a template built for 720x1280 is laid out for it, not scaled from landscape.
