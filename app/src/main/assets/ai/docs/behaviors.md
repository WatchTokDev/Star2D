# Behaviors: the `behaviors` tool

A **behavior** is a ready, configurable piece of logic attached to an element (UI or physical). The user sees it in Properties > "Additional behaviors" (switch, parameters, info) and can edit every value. Prefer a behavior to hand-written movement/effect logic: it is tested, tunable, and the user understands it.

## Quick start

1. `behaviors {"action":"list"}` returns every behavior: `id`, `what`, `for` (ui|physics|both), `params` (name:type=default), `exclusive` group, `needs`, `sets`, `events`. Filter with `target:"ui"|"physics"` or `search`.
2. `behaviors {"action":"add","element":"hero","id":"PlatformerPlayer","params":{"speed":300,"jumpButton":"JumpBtn"}}` attaches it. Several different behaviors can share an element.
3. `behaviors {"action":"info","id":"Shooter"}` shows every param with range, unit and the signature of its events.
4. Change later with `set {element,id,params,enabled}`, drop with `remove {element,id}`, `reset {element,id,param?}` goes back to the defaults.
5. `list {element}` shows what an element has now plus the public variables of its Body Script.

Param values: numbers as numbers (clamped to the range, the reply warns), `true/false`, colours `"#RRGGBB"` / `"#RRGGBBAA"`, enums by constant name, images/sounds as the project path (`"/bullet.png"`), **element references as the element's name** (`"JumpBtn"`), lists as arrays (`"enemies":["e1","e2"]`). Elements must exist; a reference to a missing element is `null` in the game.

## Built-in behaviors (run `list` for the exact current set)

| id | for | what |
|---|---|---|
| `ScaleTouchEffect` | ui/both | button scales (and fades) while pressed, restores on release |
| `Floating` | both | bobs up and down (a sine wave); the saved position is never changed |
| `Spin` | both | rotates continuously |
| `PlatformerPlayer` | physics | left/right/jump with coyote time and jump buffer; buttons or keyboard. Group `movement` |
| `JoystickMovement` | physics | moves by a JOYSTICK element (top-down or left-right). Group `movement` |
| `Shooter` | physics | shoots bullets from the element by a fire button/joystick, auto-aim, enemies list with health, power-ups. Group `weapon` |

More are added over time: always `list` first instead of assuming.

## Exclusive groups (conflicts)

Behaviors of the same `exclusive` group do the same job and fight each other (two movement behaviors, two weapons). `add` is **refused** with an explanation when the element already has an enabled behavior of that group. Decide with the user's intent:
- `replace:true` removes the old one and adds the new one (the usual answer when changing the control scheme);
- `force:true` keeps both; the new one starts **OFF** (enable it later with `set {enabled:true}`, which switches the other off).
Never use `force` to silence the error.

## Requirements and what a behavior sets

`needs` in `list` is what the element must be (e.g. `DYNAMIC` body). The reply of `add` carries a warning when the element does not fit (a physics behavior on a UI element...). `sets` are values a behavior forces at run time (e.g. Floating makes the body KINEMATIC while enabled); it does **not** rewrite the saved properties, so removing the behavior restores everything. Read the warning and fix the property with `elements set` if the user wants it permanent.

## Public variables of scripts

Any `public` field of a Body Script, of the scene script, and of a behavior is a *public variable*: it appears in Properties and the user can override its default without touching code. `behaviors {"action":"vars","element":"hero"}` lists them with the current overrides; `{"action":"vars","element":"hero","values":{"speed":420,"hpBar":"HpBar","tint":"#FF8800"}}` sets them (`null` removes an override); without `element` it works on the scene script. Types: int/long/short/byte/float/double/boolean/String, Color (`"#RRGGBB"`), enums, images/sounds (paths), lists/arrays, and element references (a field typed `PlayerItem`, `TextItem`, `ProgressItem`, `Joystick`... accepts only elements of that type). Declare them in the script first (`files`), e.g. `public float speed = 5; public ProgressItem hpBar; public java.util.List<PlayerItem> enemies;`. In Body / scene scripts also package-private fields (`float timer = 1;`), `static` fields and `private` ones annotated `@Expose` (`com.star4droid.template.Utils.Expose`) are variables; `final`, `transient` and `@Hidden` are not. An inner element of a UI holder is a variable of type `UINodeRef` (value `"holder/nodeId"`). The editor lists new fields automatically when the code editor is closed.

Stored values (what the tool writes): element property `behaviors` = `[{"type":"Floating","params":{"amplitudeY":20},"enabled":true}]`, element property `scriptVars` = `{"speed":420,"hpBar":"HpBar"}`, scene script values in `Events/<scene>/public/scriptvars.json`. Use the tool, not `elements set`, so values are validated.

## Behavior events

Some behaviors raise events (`Shooter`: onShoot, onEnemyHit, onEnemyKilled...). They appear in the Events panel under "Behavior events" and are written with the normal `events` tool using the name `behaviors/<id>/<event>`:

`events {"operations":[{"op":"set","scope":"element","element":"hero","event":"behaviors/Shooter/onEnemyKilled","blocks":[...]}]}`

`info` shows the signature of each event; inside the code `current` is the element and the arguments are typed local variables with the names of the signature. Blocks work as in any element event.

## Order of execution

For one element: its behaviors (in list order) run first, then its Body Script (`onBodyUpdate` etc.), then the element events written in the Events panel. A disabled behavior does nothing; `item.setBehaviorEnabled("Shooter", false)` in Java switches one at run time.

## Custom behaviors

The user (or you, with `files`) can write a Java behavior in `java/com/star4droid/Game/Behaviors/<Name>.java` (package `com.star4droid.Game.Behaviors`, `extends Behavior`, annotated `@BehaviorInfo(name=..., desc=..., targets=...)`, public fields = parameters, override `onStart/onUpdate/...`). It then shows up under "Project" in `behaviors list` like a built-in. Units inside behaviors are editor pixels (y up), px/s.

## Common mistakes

- Adding `PlatformerPlayer` to a STATIC body, or `Floating` to something the user moves by code: read the `needs`/warnings.
- Passing an element name that does not exist in the scene: check `scene_dump {brief:true}`.
- Writing movement in `OnBodyUpdate` blocks when a behavior already does it: use the behavior, tune its params.
- Using `force:true` to keep two movement behaviors.

## Blocks from a behavior (`@Block`)

A public method of a behavior marked `@Block(label = "set run speed", desc = "...", labelAr = "...", descAr = "...", args = {"speed"})` becomes a block, shown (palette and `block_catalog`) only while the scene uses the behavior. `void` = action block; `boolean` / number / `String` / `PlayerItem` result = value block; parameters: int / long / float / double, boolean, String, PlayerItem, Color. When you write a behavior in `java/com/star4droid/Game/Behaviors/`, annotate the methods other logic would want to call (`import com.star4droid.template.behaviors.Block;`) and always give `label` and `desc`.

