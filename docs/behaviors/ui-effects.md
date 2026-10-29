# UI, effects and input behaviors

Ready-made behaviors for menus, HUDs and "juice". Add them from the element's **Additional behaviors** section (or `item.addBehavior("Pulse")`).
They work on UI elements (no body) and on physical ones. Everything is also reachable from blocks: each behavior has its own blocks that appear only while a
scene element uses it (see `docs/BEHAVIORS.md`). The tables below are generated from the annotations of the classes
(`python3 tools/test_lib_ui.py --docs`), so they always match the code.

## How they behave (all of them)

- **The saved element is never rewritten.** A behavior remembers the look the element had (size, opacity, color, rotation, pivot, position) and gives it back when it
  is disabled (`item.setBehaviorEnabled("Pulse", false)`) or the element is destroyed. Offsets (Shake, SlideIn, Parallax) are added on top of the element's
  *current* place, so they combine with Floating, a script or a drag.
- **Several effects on one element work**: sizes and opacity are set from the saved look each frame, so the last behavior of the list wins on the same property
  (e.g. Pulse + FadeIn both writing the size). Use different properties: Pulse (size) + TintCycle (color) + Wobble (rotation) + Shake (position).
- **Text and Progress elements** pivot around their corner by default; while an effect scales or rotates them the pivot moves to the center and is restored on disable.
  They are click-through in generated games when they have no touch event, so every behavior that needs touches (Draggable, LongPress, ButtonSound,
  OpenSceneOnClick, ToggleOnClick, Typewriter tap-to-skip, Shake/Wobble on touch) turns touch on for its element by itself.
- **Physical elements**: rotation effects turn the body (Wobble, Shake rotation), position effects move the body (`setTransform`), so use them on static / kinematic
  elements or accept that the physics keeps pushing a dynamic one. Draggable zeroes the velocity while held; when released, a dynamic body falls/continues normally.
- **Units**: pixels of the editor (y up) and seconds, like every behavior. Swipe distance and long-press tolerance are in *screen* pixels of the device.
- **No allocation per frame** (checked by the tests: below 1 byte/frame on average for all the effects).
- **Easing** (`FadeIn`, `FadeOut`, `SlideIn`, `CountUp`): LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT (overshoots a little), BOUNCE_OUT, ELASTIC_OUT. The curves
  live in `behaviors/Easing.java`; each behavior declares its own nested `Ease` enum with the same constants (the editor reads enums from the same source file).

## Recipes

- **Animated main menu**: title with `Wobble` (angle 4) + `TintCycle`; Play button with `Pulse`, `ScaleTouchEffect`, `ButtonSound` and `OpenSceneOnClick` (fade overlay = a
  black box over the screen); buttons below with `SlideIn` (delay 0.1, 0.2, 0.3...).
- **Dialogue box**: Text with `Typewriter` (speed 25, tick sound), `onDone` shows a "tap" arrow that has `Blink`.
- **Score**: Text with `CountUp` (autoPlay off, prefix `Score: `, thousands separator on); in the game call the block **count to** with the new score.
- **Health bar**: Progress with `ProgressBind` (source SAVED_VALUE, value name `hp`, maximum = max health); the game does `setValue("hp", "...")`.
- **Settings panel**: gear button with `ToggleOnClick` (targets = the panel), panel with `SlideIn` (autoPlay off, run **slide in** from the toggle's `onToggled`).
- **Inventory / puzzle**: pieces with `Draggable` (drop targets = the slots, snap to target, snap back on).
- **Scrolling sky**: a wide sky image (UI) with `Parallax` (factor 0.9, loop width = image width) while the camera follows the player.
- **Hold to confirm**: button with `LongPress` (hold time 1 s, hold bar = a Progress element) and `onLongPress` -> the dangerous action.

## Known limits

- `ProgressBind` reads a *saved value* by name (`setValue/getValue`); scene-script variables are generated fields and cannot be read by name (use the manual
  block `set bar value`, or save the value too).
- `ToggleOnClick` does not undo what it hid/showed when it is disabled (visibility is game state); `FadeOut` with HIDE shows the element again when the behavior is disabled.
- `OpenSceneOnClick`'s fade needs a big box of your own (an element, any color) because behaviors do not create elements; leave it invisible/transparent in the editor.
- `SwipeDetector` tracks the first finger only.
- `ButtonSound` / `Typewriter` ticks use `startSound` (`sounds/<file>`); a sound that is missing is ignored silently after the first failed attempt.

## Reference

| id | icon | category | description |
|---|---|---|---|
| `Pulse` | `heart-pulse` | UI effects | The element grows and shrinks again and again, like a beating heart. Good for "Play" buttons and pickups. |
| `Blink` | `eye` | UI effects | The element flashes on and off (or fades in and out). Good for "Tap to start" texts, warnings and invincibility. |
| `FadeIn` | `circle-opacity` | UI effects | The element appears smoothly: from invisible (and optionally smaller) to its normal look. Good for menus and titles. |
| `FadeOut` | `eye-off` | UI effects | The element fades away smoothly and can then hide or remove itself. Good for messages, pickups and closing menus. |
| `Shake` | `vibrate` | UI effects | The element trembles for a moment: when the scene starts, when it is touched or from a block. Good for wrong answers, hits and buttons. |
| `SlideIn` | `transfer-right` | UI effects | The element slides to its place from one side, with a bounce or ease if you like. A block slides it back out. Good for menus and panels. |
| `Wobble` | `sine-wave` | UI effects | The element rocks left and right like a toy (and can squash a little). Forever, when touched or from a block. Good for idle icons and attention. |
| `Typewriter` | `keyboard` | Text | A Text element types its text letter by letter, like a dialogue box. Tap to show it all at once. |
| `CountUp` | `numeric` | Text | A Text element counts from one number to another, like a score. Optional prefix, suffix and thousands separator. |
| `ButtonSound` | `volume-high` | Input | Plays a sound when the element is touched, released or clicked. Add it to every button of your menu. |
| `OpenSceneOnClick` | `door-open` | Input | Touching the element opens another scene, after an optional wait and fade. For Play buttons and menus. |
| `ToggleOnClick` | `toggle-switch` | Input | Touching the element shows or hides other elements: a settings panel, a menu, a hint. |
| `Draggable` | `cursor-move` | Input | The player can drag the element with a finger. Keep it inside an area, snap it to a grid, send it back home or drop it on targets (puzzles, inventories, sliders). |
| `Parallax` | `layers` | UI effects | A background layer that moves slower than the world when the camera moves, for depth. Works for screen backgrounds (UI) and world elements. |
| `LongPress` | `timer` | Input | Tells the difference between a quick tap and holding the element. Hold-to-confirm buttons, hidden menus, charge-up. |
| `SwipeDetector` | `gesture` | Input | Detects a finger swipe left, right, up or down, on the element or anywhere on the screen. For page flipping, menus and swipe-controlled games. |
| `ProgressBind` | `link-variant` | Logic | Keeps a Progress bar in step with a number: a value saved with setValue, another bar, or the number written in a Text. Health, loading, XP, timers. |
| `TintCycle` | `palette` | UI effects | The element's color slowly changes: back and forth between two colors, or through the whole rainbow. Good for titles, power-ups and danger signs. |

## Pulse  (`Pulse`)

The element grows and shrinks again and again, like a beating heart. Good for "Play" buttons and pickups.

- Icon: `heart-pulse` · Category: UI effects · Works on: any element
- Events: `onPulse()`

| parameter | default | range | description |
|---|---|---|---|
| `minScale` | 0.95 | 0.1 .. 3 | Smallest size, 1 = normal size. |
| `maxScale` | 1.08 | 0.1 .. 3 | Biggest size, 1 = normal size. |
| `speed` | 1.2 | 0.05 .. 10 Hz | Beats per second. |
| `lowOpacity` | 1 | 0 .. 1 | Opacity at the smallest size (1 = no fading). |
| `autoPlay` | true | on / off | Start pulsing as soon as the scene starts. Otherwise use the start / pulse once blocks. |
| `pulseWhileTouched` | true | on / off | Keep pulsing while the element is touched. |

Blocks:

- **start pulse** – Starts pulsing until stopped. · ابدأ النبض
- **stop pulse** – Stops pulsing and returns to the normal size. · أوقف النبض
- **pulse once** – Does one beat and stops. · نبضة واحدة
- **is pulsing** → boolean – True while the element pulses. · ينبض الآن
- **set pulse speed** (beats) – Beats per second. · اضبط سرعة النبض
- **set pulse size** (scale) – Biggest size (1 = normal). · اضبط حجم النبض

## Blink  (`Blink`)

The element flashes on and off (or fades in and out). Good for "Tap to start" texts, warnings and invincibility.

- Icon: `eye` · Category: UI effects · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `onTime` | 0.5 | 0.02 .. 30 s | How long it stays visible each time. |
| `offTime` | 0.3 | 0.02 .. 30 s | How long it stays hidden each time. |
| `offOpacity` | 0 | 0 .. 1 | Opacity while "off" (0 = invisible). |
| `smooth` | false | on / off | Fade smoothly instead of switching at once. |
| `count` | 0 | 0 .. 1000 | Number of blinks, then it stops visible. 0 = forever. |
| `autoPlay` | true | on / off | Start blinking as soon as the scene starts. |

Blocks:

- **start blinking** – Starts blinking. · ابدأ الوميض
- **stop blinking** – Stops blinking and shows the element. · أوقف الوميض
- **is blinking** → boolean – True while blinking. · يومض الآن
- **blink times** (times) – Blinks the given number of times, then stops. · اومض عدة مرات
- **set blink speed** (seconds) – Seconds visible and seconds hidden in each blink. · اضبط مدة الوميض

## Fade in  (`FadeIn`)

The element appears smoothly: from invisible (and optionally smaller) to its normal look. Good for menus and titles.

- Icon: `circle-opacity` · Category: UI effects · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `duration` | 0.5 | 0.01 .. 30 s | How long the fade lasts. |
| `delay` | 0 | 0 .. 60 s | Wait this long before starting. |
| `fromOpacity` | 0 | 0 .. 1 | Opacity at the start (0 = invisible). |
| `fromScale` | 1 | 0 .. 3 | Size at the start, 1 = normal size. Use 0.5 to make it "pop" in. |
| `ease` | EASE_OUT | LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT | How the speed changes during the fade. |
| `autoPlay` | true | on / off | Start as soon as the scene starts. Otherwise the element is shown normally until the play block runs. |

Blocks:

- **play fade in** – Hides the element and fades it in. · شغّل الظهور التدريجي
- **set fade in time** (seconds) – How long the fade lasts (seconds). · اضبط مدة الظهور
- **is fading in** → boolean – True while the element is fading in. · يظهر تدريجياً الآن

## Fade out  (`FadeOut`)

The element fades away smoothly and can then hide or remove itself. Good for messages, pickups and closing menus.

- Icon: `eye-off` · Category: UI effects · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `trigger` | ON_START | ON_START, ON_CLICK, MANUAL | When the fade starts. MANUAL = only when the play block runs. |
| `duration` | 0.5 | 0.01 .. 30 s | How long the fade lasts. |
| `delay` | 1.5 | 0 .. 600 s | Wait this long before starting (a message that disappears by itself). |
| `toOpacity` | 0 | 0 .. 1 | Opacity at the end (0 = invisible). |
| `toScale` | 1 | 0 .. 3 | Size at the end, 1 = normal size. Use 0 to shrink away. |
| `ease` | EASE_IN | LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT | How the speed changes during the fade. |
| `then` | HIDE | STAY, HIDE, DESTROY | What happens at the end: STAY = stays there (faded), HIDE = hidden (and its physics off), DESTROY = removed. |

Blocks:

- **stop fade out** – Stops the fade and shows the element normally again. · أوقف الاختفاء
- **play fade out** – Fades the element away. · شغّل الاختفاء التدريجي
- **set fade out time** (seconds) – How long the fade lasts (seconds). · اضبط مدة الاختفاء
- **is fading out** → boolean – True while the element is fading out. · يختفي تدريجياً الآن
- **set fade out delay** (seconds) – Seconds to wait before the fade starts. · اضبط تأخير الاختفاء

## Shake  (`Shake`)

The element trembles for a moment: when the scene starts, when it is touched or from a block. Good for wrong answers, hits and buttons.

- Icon: `vibrate` · Category: UI effects · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `trigger` | ON_TOUCH | ON_START, ON_TOUCH, MANUAL | When it shakes. MANUAL = only when the shake block runs. |
| `axis` | BOTH | BOTH, HORIZONTAL, VERTICAL | Which direction it moves. |
| `intensity` | 8 | 0 .. 500 px | How far it moves from its place. |
| `duration` | 0.4 | 0.05 .. 30 s | How long one shake lasts. |
| `frequency` | 30 | 1 .. 120 Hz | Direction changes per second: bigger = nervous, smaller = swaying. |
| `rotation` | 0 | 0 .. 90 deg | Also turns a little back and forth (0 = no turning). |
| `decay` | true | on / off | The shake weakens until it stops. |
| `loop` | false | on / off | Keep shaking until the stop block runs (duration is ignored). |

Blocks:

- **stop shaking** – Stops the shake at once. · أوقف الاهتزاز
- **shake** – Shakes the element once. · اهتز
- **shake with strength** (strength) – Shakes the element; strength is the distance in px. · اهتز بقوة
- **is shaking** → boolean – True while the element shakes. · يهتز الآن

## Slide in  (`SlideIn`)

The element slides to its place from one side, with a bounce or ease if you like. A block slides it back out. Good for menus and panels.

- Icon: `transfer-right` · Category: UI effects · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `from` | LEFT | LEFT, RIGHT, TOP, BOTTOM | The side it comes from. |
| `distance` | 400 | 0 .. 10000 px | How far away it starts. |
| `duration` | 0.6 | 0.01 .. 30 s | How long the slide lasts. |
| `delay` | 0 | 0 .. 60 s | Wait this long before starting (use different values to slide a list one by one). |
| `ease` | BACK_OUT | LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT | How the speed changes during the slide. |
| `fade` | true | on / off | Also fade in while sliding. |
| `autoPlay` | true | on / off | Start as soon as the scene starts. Otherwise the element stays in place until the play block runs. |

Blocks:

- **slide out** – Slides the element away to its side. · انزلق للخارج
- **slide in** – Slides the element in from its side. · انزلق للداخل
- **set slide distance** (distance) – How far away the slide starts (px). · اضبط مسافة الانزلاق
- **is sliding** → boolean – True while the element slides. · ينزلق الآن

## Wobble  (`Wobble`)

The element rocks left and right like a toy (and can squash a little). Forever, when touched or from a block. Good for idle icons and attention.

- Icon: `sine-wave` · Category: UI effects · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `trigger` | ON_START | ON_START, ON_TOUCH, MANUAL | When it wobbles. MANUAL = only when the wobble block runs. |
| `angle` | 8 | 0 .. 90 deg | How far it turns each way. |
| `speed` | 2 | 0.1 .. 20 Hz | Full swings per second. |
| `duration` | 0 | 0 .. 600 s | Wobble for this long, then stop. 0 = until stopped. |
| `decay` | false | on / off | Each swing is smaller than the last (needs a duration). |
| `squash` | 0 | 0 .. 0.5 | Stretch and squash a little while it swings (0 = none). |

Blocks:

- **stop wobbling** – Stops wobbling and straightens the element. · أوقف التمايل
- **wobble** – Starts wobbling. · تمايل
- **set wobble angle** (degrees) – How far it turns each way (degrees). · اضبط زاوية التمايل
- **is wobbling** → boolean – True while the element wobbles. · يتمايل الآن

## Typewriter  (`Typewriter`)

A Text element types its text letter by letter, like a dialogue box. Tap to show it all at once.

- Icon: `keyboard` · Category: Text · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `speed` | 30 | 1 .. 500 letters/s | Letters per second. |
| `startDelay` | 0 | 0 .. 60 s | Wait this long before typing starts. |
| `punctuationPause` | 0.15 | 0 .. 3 s | Extra pause after . , ! ? so sentences breathe (0 = none). |
| `customText` | (empty) |  | Type another text instead of the one written in the element (empty = use the element's text). |
| `tapToSkip` | true | on / off | Touching the text shows all of it at once. |
| `autoPlay` | true | on / off | Start as soon as the scene starts. Otherwise use the type text block. |
| `tickSound` | (none) | sound file | Tiny sound for the letters (optional). |
| `tickEvery` | 2 | 1 .. 20 | Play the tick sound every this many letters. |

Blocks:

- **show all text** – Shows the whole text at once. · أظهر كل النص
- **type text** (text) – Types the given text letter by letter. · اكتب النص
- **set typing speed** (speed) – Letters per second. · اضبط سرعة الكتابة
- **type again** – Types the current text again from the start. · أعد الكتابة
- **is typing** → boolean – True while letters are still being typed. · يكتب الآن

## Count up  (`CountUp`)

A Text element counts from one number to another, like a score. Optional prefix, suffix and thousands separator.

- Icon: `numeric` · Category: Text · Works on: any element
- Events: `onDone()`

| parameter | default | range | description |
|---|---|---|---|
| `from` | 0 | -1.0E9 .. 1.0E9 | Number it starts from. |
| `to` | 100 | -1.0E9 .. 1.0E9 | Number it counts to. |
| `duration` | 1.5 | 0.05 .. 120 s | How long the counting lasts. |
| `delay` | 0 | 0 .. 60 s | Wait this long before counting. |
| `decimals` | 0 | 0 .. 6 | Digits after the dot. |
| `prefix` | (empty) |  | Text before the number, e.g. $ or Score:  |
| `suffix` | (empty) |  | Text after the number, e.g. % or pts |
| `thousandsSeparator` | false | on / off | Write 1,234,567 instead of 1234567. |
| `ease` | EASE_OUT | LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT | How the speed changes while counting. |
| `autoPlay` | true | on / off | Start counting as soon as the scene starts. Otherwise use the count to block. |

Blocks:

- **shown number** → float – The number shown right now. · الرقم المعروض
- **set number** (number) – Shows the number at once, without counting. · اضبط الرقم
- **count to** (number) – Counts from what is shown now to the given number. · عُدّ حتى
- **count again** – Counts from the start number to the end number. · عُدّ مجدداً
- **is counting** → boolean – True while the number is changing. · يعدّ الآن

## Button sound  (`ButtonSound`)

Plays a sound when the element is touched, released or clicked. Add it to every button of your menu.

- Icon: `volume-high` · Category: Input · Works on: any element

| parameter | default | range | description |
|---|---|---|---|
| `sound` | (none) | sound file | Sound played when the touch starts / the click happens (a file of the project's sounds). |
| `releaseSound` | (none) | sound file | Sound played when the finger lifts (optional). |
| `when` | TOUCH_START | TOUCH_START, CLICK | TOUCH_START = as soon as it is pressed, CLICK = only when it is pressed and released on it. |
| `cooldown` | 0.05 | 0 .. 2 s | Ignore touches that come sooner than this after the last sound. |

Blocks:

- **play button sound** – Plays the button sound now. · شغّل صوت الزر
- **set button sound** (sound) – Changes the sound file (name of a file in the sounds folder). · اضبط صوت الزر

## Open scene on click  (`OpenSceneOnClick`)

Touching the element opens another scene, after an optional wait and fade. For Play buttons and menus.

- Icon: `door-open` · Category: Input · Works on: any element
- Events: `onOpening()`

| parameter | default | range | description |
|---|---|---|---|
| `scene` | (empty) |  | Name of the scene to open (as written in the scenes list). |
| `trigger` | CLICK | CLICK, TOUCH_START | CLICK = when it is pressed and released on the element, TOUCH_START = as soon as it is pressed. |
| `delay` | 0 | 0 .. 30 s | Wait this long before the scene changes (time for a sound or an effect). |
| `fadeOverlay` | none | element | A big black box over the whole screen (optional): it fades in before the scene changes. Make it invisible or 0% opaque in the editor. |
| `fadeTime` | 0.4 | 0.05 .. 5 s | How long the overlay takes to fade in. |
| `clickSound` | (none) | sound file | Sound played when the touch is accepted (optional). |

Blocks:

- **cancel opening** – Cancels a scene change that is waiting. · ألغِ الفتح
- **open scene now** – Starts the scene change (with the delay and fade of this behavior). · افتح المشهد الآن
- **set scene to open** (scene) – Chooses which scene this element opens. · اضبط المشهد المراد فتحه

## Toggle on click  (`ToggleOnClick`)

Touching the element shows or hides other elements: a settings panel, a menu, a hint.

- Icon: `toggle-switch` · Category: Input · Works on: any element
- Events: `onToggled(boolean visible)`

| parameter | default | range | description |
|---|---|---|---|
| `targets` | empty | list of elements | The elements that are shown / hidden. |
| `action` | TOGGLE | TOGGLE, SHOW, HIDE | TOGGLE = flip each time, SHOW = always show, HIDE = always hide. |
| `trigger` | CLICK | CLICK, TOUCH_START | CLICK = when it is pressed and released on the element, TOUCH_START = as soon as it is pressed. |
| `startHidden` | false | on / off | Hide the targets when the scene starts. |
| `affectPhysics` | true | on / off | Hidden physical elements also stop colliding and moving. |

Blocks:

- **targets are shown** → boolean – True while the targets are shown. · العناصر ظاهرة
- **show or hide targets** (show) – true = show the targets, false = hide them. · أظهر أو أخفِ العناصر
- **toggle targets** – Shows the targets if hidden, hides them if shown. · بدّل ظهور العناصر

## Draggable  (`Draggable`)

The player can drag the element with a finger. Keep it inside an area, snap it to a grid, send it back home or drop it on targets (puzzles, inventories, sliders).

- Icon: `cursor-move` · Category: Input · Works on: any element
- Events: `onDragStart()` `onDrag()` `onDrop()` `onDropOn(PlayerItem target)`

| parameter | default | range | description |
|---|---|---|---|
| `lockX` | false | on / off | Do not move sideways. |
| `lockY` | false | on / off | Do not move up and down. |
| `area` | none | element | Keep the element inside the box of this other element (optional). |
| `gridSize` | 0 | 0 .. 1000 px | When released, snap to a grid of this size (0 = no grid). |
| `snapBack` | false | on / off | Go back to the starting place when released. |
| `snapBackTime` | 0.25 | 0 .. 5 s | How long the trip back takes (0 = at once). |
| `dropTargets` | empty | list of elements | Elements it can be dropped on (optional). onDropOn runs when it is released over one. |
| `snapToTarget` | true | on / off | Move to the center of the target it was dropped on. |
| `dragScale` | 1.1 | 0.5 .. 2 | Size while dragging (1 = normal), so it looks lifted. |
| `bringToFront` | true | on / off | Bring the element in front of the others while dragging. |
| `freezePhysics` | true | on / off | A physical element stops moving by itself while it is held. |

Blocks:

- **is being dragged** → boolean – True while a finger holds the element. · يتم سحبه الآن
- **set this place as start place** – The element goes back here when snap back is on. · اجعل هذا المكان هو الأصلي
- **go back to start place** – Moves the element back to where it started (or where it was last set as home). · ارجع إلى المكان الأصلي

## Parallax  (`Parallax`)

A background layer that moves slower than the world when the camera moves, for depth. Works for screen backgrounds (UI) and world elements.

- Icon: `layers` · Category: UI effects · Works on: any element

| parameter | default | range | description |
|---|---|---|---|
| `factorX` | 0.5 | -1 .. 2 | Sideways speed relative to the world: 0 = moves with the world (no effect), 0.5 = half speed (far), 1 = stays still on the screen (very far). |
| `factorY` | 0.5 | -1 .. 2 | Same, for up and down. Only used when "Affect vertical" is on. |
| `affectVertical` | false | on / off | Also react when the camera moves up and down. |
| `loopWidth` | 0 | 0 .. 100000 px | Repeat the movement every this many pixels sideways, so a row of identical tiles scrolls forever (0 = no repeat). |
| `loopHeight` | 0 | 0 .. 100000 px | Same, for up and down (0 = no repeat). |

Blocks:

- **reset parallax** – Uses the camera's current place as the starting point. · أعد ضبط البارالاكس
- **set parallax speed** (factor) – 0 = moves with the world, 1 = stays on the screen. · اضبط سرعة البارالاكس

## Long press  (`LongPress`)

Tells the difference between a quick tap and holding the element. Hold-to-confirm buttons, hidden menus, charge-up.

- Icon: `timer` · Category: Input · Works on: any element
- Events: `onLongPress()` `onShortPress()`

| parameter | default | range | description |
|---|---|---|---|
| `holdTime` | 0.6 | 0.1 .. 10 s | How long the finger must stay down. |
| `moveTolerance` | 30 | 0 .. 500 px | If the finger moves farther than this (screen pixels) it is not a press any more. |
| `repeat` | false | on / off | Keep firing onLongPress every hold time while the finger stays down. |
| `holdBar` | none | element | A Progress element that fills while holding (optional): hold to confirm. |

Blocks:

- **set hold time** (seconds) – How long the finger must stay down (seconds). · اضبط مدة الضغط المطوّل
- **held time** → float – How long the element has been held (seconds), 0 when not touched. · مدة الضغط
- **is held** → boolean – True while a finger is holding the element. · مضغوط الآن

## Swipe detector  (`SwipeDetector`)

Detects a finger swipe left, right, up or down, on the element or anywhere on the screen. For page flipping, menus and swipe-controlled games.

- Icon: `gesture` · Category: Input · Works on: any element
- Events: `onSwipeLeft()` `onSwipeRight()` `onSwipeUp()` `onSwipeDown()`

| parameter | default | range | description |
|---|---|---|---|
| `onlyOnElement` | false | on / off | Only swipes that start on this element count. Off = a swipe anywhere on the screen. |
| `minDistance` | 80 | 10 .. 2000 px | How far the finger must travel (screen pixels). |
| `maxTime` | 1 | 0 .. 10 s | A swipe must be done within this time; slower drags are ignored (0 = no limit). |
| `fireWhileMoving` | false | on / off | React as soon as the distance is reached, without waiting for the finger to lift. |

Blocks:

- **last swipe** → String – left, right, up or down (empty before the first swipe). · آخر سحب
- **set swipe distance** (distance) – Minimum distance of a swipe in screen pixels. · اضبط مسافة السحب

## Progress bind  (`ProgressBind`)

Keeps a Progress bar in step with a number: a value saved with setValue, another bar, or the number written in a Text. Health, loading, XP, timers.

- Icon: `link-variant` · Category: Logic · Works on: any element
- Events: `onFull()` `onEmpty()`

| parameter | default | range | description |
|---|---|---|---|
| `source` | SAVED_VALUE | SAVED_VALUE, OTHER_PROGRESS, OTHER_TEXT, MANUAL | Where the number comes from. SAVED_VALUE = a value saved by name (setValue / getValue), OTHER_PROGRESS = the value of another Progress element, OTHER_TEXT = the first number written in a Text element, MANUAL = only the set value block. |
| `valueName` | (empty) |  | Name of the saved value (SAVED_VALUE). |
| `other` | none | element | The other element (OTHER_PROGRESS / OTHER_TEXT). |
| `minValue` | 0 | -1.0E9 .. 1.0E9 | Number that shows an empty bar. |
| `maxValue` | 100 | -1.0E9 .. 1.0E9 | Number that shows a full bar. |
| `smoothing` | 8 | 0 .. 60 1/s | How fast the bar follows (0 = instantly). |
| `pollInterval` | 0.1 | 0.016 .. 2 s | How often the source is read; a value saved by name is read from storage. |

Blocks:

- **set bar value** (value) – Sets the number the bar shows (use with source MANUAL). · اضبط قيمة الشريط
- **set bar maximum** (maximum) – The number that shows a full bar. · اضبط الحد الأقصى للشريط
- **bar fraction** → float – How full the bar is, from 0 to 1. · نسبة امتلاء الشريط

## Tint cycle  (`TintCycle`)

The element's color slowly changes: back and forth between two colors, or through the whole rainbow. Good for titles, power-ups and danger signs.

- Icon: `palette` · Category: UI effects · Works on: any element

| parameter | default | range | description |
|---|---|---|---|
| `mode` | BETWEEN | BETWEEN, RAINBOW | BETWEEN = fades between the two colors, RAINBOW = goes through all colors. |
| `colorA` | #FFFFFF |  | First color (BETWEEN). |
| `colorB` | #FF5252 |  | Second color (BETWEEN). |
| `speed` | 0.5 | 0.01 .. 10 Hz | Full cycles per second. |
| `saturation` | 0.6 | 0 .. 1 | How strong the colors are (RAINBOW). |
| `brightness` | 1 | 0 .. 1 | How light the colors are (RAINBOW). |
| `autoPlay` | true | on / off | Start as soon as the scene starts. |

Blocks:

- **start color cycle** – Starts changing the color. · ابدأ تغيير اللون
- **stop color cycle** – Stops and gives the element its normal color back. · أوقف تغيير اللون
- **set color cycle speed** (cycles) – Full cycles per second. · اضبط سرعة تغيير اللون

