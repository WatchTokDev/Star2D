# Gameplay, combat and camera behaviors

Seventeen ready-made behaviors for the things almost every game needs: hit points, damage, pickups, spawning, checkpoints,
timers, scores, doors, a win condition and a camera that follows and shakes. Add them from the behaviors dialog of an element
(category Combat, Gameplay, Logic or Camera); their settings appear in the inspector, their events in the Events panel and their
blocks in the block editor as soon as the scene contains an element that uses them.

This page is generated from the annotations in `app/src/main/java/com/star4droid/template/behaviors/lib/` by
`python3 tools/test_lib_gameplay.py --docs`, so the tables always match the code. The notes under each behavior are written by hand.

## Summary

| Behavior | Icon | Target | What it does | Events | Blocks |
|---|---|---|---|---|---|
| [Health](#health) | `heart` | both | Hit points for any element: damage, healing, invulnerability frames, regeneration, a progress bar that follows the health and what happens on death (destroy, hide, respawn, restart the scene or open another one). | `onDamage`, `onHeal`, `onDeath`, `onRevive` | 12 |
| [DamageOnTouch](#damageontouch) | `skull-crossbones` | physics | Spikes, lava, enemies, saws: hurts the elements that touch it (they need a Health or Destructible behavior), once per touch or again and again, with knockback. | `onHit`, `onKill` | 4 |
| [Destructible](#destructible) | `cargo-crate` | physics | Crates, bricks, walls: breaks after a number of hits (bullets, DamageOnTouch, or a ball touching it), shows damaged pictures on the way and can drop an item. | `onHit`, `onDestroyed` | 6 |
| [Projectile](#projectile) | `bullet` | both | Turns an element into a bullet, arrow or fireball: flies in a direction, hurts what it hits, stops at walls and disappears after its lifetime. Pair it with a Spawner to fire copies. | `onHit`, `onExpire` | 5 |
| [Turret](#turret) | `cannon` | physics | An automatic gun: finds the nearest target in range, turns toward it and fires bullets that hurt it (targets need a Health behavior, or give them hit points with Auto health). Bullets are reused, so it is cheap. | `onShoot`, `onTargetFound`, `onTargetLost`, `onHit` | 9 |
| [Spawner](#spawner) | `portal` | both | Creates copies of a template element while the game runs: enemies, coins, rocks. Every few seconds or in waves, at the spawner, inside its area, on a ring around it or at spawn points, with a limit of how many live at once. | `onSpawn`, `onWaveStart`, `onWaveDone`, `onAllWavesDone` | 13 |
| [Collectible](#collectible) | `coin` | physics | Coins, gems, keys, hearts: when the player touches it, it adds a value to a scene variable (score, coins, keys), can heal, plays a sound and disappears or comes back later. | `onCollected`, `onRespawned` | 4 |
| [PowerUp](#powerup) | `lightning-bolt` | physics | A pickup that changes the settings of a behavior on whoever takes it for a while (faster, higher jumps, faster fire...), then puts them back. Can also heal and make invulnerable. | `onCollected`, `onExpired` | 3 |
| [Checkpoint](#checkpoint) | `flag-variant` | physics | A flag that remembers where to come back to: when the player touches it, the Respawn behavior returns there after death or a fall. Works across scene restarts (and, if you want, across app launches). | `onActivated` | 3 |
| [Respawn](#respawn) | `restart` | physics | Brings the player back to the last Checkpoint (or to where it started) after dying or falling out of the world, with a short invulnerability, an optional lives counter and game over scene. | `onRespawn`, `onOutOfLives` | 6 |
| [Lifetime](#lifetime) | `timer-sand` | both | The element removes itself after a while, fading out (and optionally shrinking) first. Bullets, particles, temporary platforms, hints. | `onExpire` | 4 |
| [Timer](#timer) | `timer` | both | A countdown or a stopwatch shown in a Text element: level time limits, race times, cooldown displays. Start, pause and reset it with blocks; it can also write the seconds into a scene variable. | `onTimeout`, `onTick`, `onWarning` | 8 |
| [ScoreCounter](#scorecounter) | `scoreboard` | both | Shows a scene variable (score, coins, lives...) in a Text element with a prefix, padding and a counting animation, and keeps a high score on the device. | `onScoreChanged`, `onNewHighScore` | 6 |
| [Door](#door) | `door-open` | physics | A door, portal or exit: when the player touches it (or presses a button next to it) it opens another scene or teleports the player to another element. Can need a key (a scene variable) first. | `onOpen`, `onLocked` | 3 |
| [Goal](#goal) | `trophy` | physics | The finish line / win condition: the level is won when the player reaches it AND the conditions are met (all coins of a group collected, a score reached, all enemies gone). Shows a win panel and can open the next scene. | `onWin`, `onBlocked` | 5 |
| [CameraFollow](#camerafollow) | `camera` | both | The camera follows an element smoothly, with a dead zone, look-ahead in the direction of movement, scene limits and zoom. Works together with Camera Shake. Put it on the player or on any element and choose the target. | - | 8 |
| [CameraShake](#camerashake) | `vibrate` | both | Screen shake for hits, explosions and landings. Call shake with a strength from 0 to 1; strong hits shake much more than weak ones and fade out by themselves. Health can trigger it too (Shake on hit). Works with or without Camera Follow. | `onShakeEnd` | 4 |

## How they work together

**Damage chain.** `DamageOnTouch`, `Projectile` and `Turret` do not know what a target is. They call the first *Damageable* behavior of
the element they hit: `Health` (hit points) or `Destructible` (a number of hits). An element without either is not hurt, unless the
damager has *Auto health* above 0: then a `Health` with that many hit points is added on the first hit (a plain enemy with "3 hit
points" and no configuration). Your own behaviors can hurt things with `GameplaySupport.hit(target, amount, source, pushX, pushY, stunSeconds, autoHealth)`,
and a new kind of life only has to implement `Damageable`.

**Who counts as a target.** Everywhere a behavior asks "who" there are two fields that add up: a *list* of elements, and a *name prefix*
text (`enemy, boss` matches every element whose name starts with `enemy` or `boss`, ignoring capitals; `*` matches all). Clones made by
a `Spawner` are named `<template>_c<number>`, so the template's name works as the prefix for all its copies. Contacts come from the
physics engine, which gives no contact between two non-dynamic bodies (a kinematic bullet against a static wall): elements in a *list*
are therefore also found by overlap of their boxes, name prefixes rely on real contacts, so one of the two bodies must be Dynamic
(a player, a ball) - or put the element in the list. `Turret` finds prefix targets by looking through the scene four times a second.

**Scene variables.** `Collectible`, `ScoreCounter`, `Timer`, `Door`, `Goal` and `Respawn` read and write a scene variable by *name*
(create an int or float in the Variables of the scene, e.g. `score`). If no variable with that name exists the behaviors still work with a
hidden per-scene number, so two behaviors using the same name always see the same value.

**Hiding without freezing.** The engine does not update invisible elements, so a hidden collectible could never come back and a
hidden spawner template could never spawn. These behaviors hide with alpha 0, no body and no touches instead. The element is still
"visible" for the engine; if you check visibility in your own code use the *alpha* or `isCollected`-style blocks.

**Templates.** The element a `Spawner` (or a `Destructible` drop) copies is hidden when the game starts and its behaviors are switched
off, so a template bullet does not fly away and a template with a `Lifetime` does not expire; every copy gets the behaviors back in
the on/off state you set in the editor, with the same settings and the same events.

**Never inside a contact.** The physics world is locked while it reports contacts. All of these behaviors only *note* the touch and act
in the next frame; do the same in your own event blocks (use the `destroyLater` helper or a behavior block, not `destroy` inside a collision event).

**Camera.** `CameraFollow` places the camera, `CameraShake` adds an offset on top of it, `Health` (*Shake on hit*) can trigger the shake.
They share one camera rig, so they run in any order, with or without each other; anything else that moves the camera
(`setCameraXY`, the stage follow) simply wins for that frame. Units are editor pixels, y up. The camera is updated while the elements
are drawn, so it can lag the player by one frame; the smoothing hides it.

## Typical set-ups

- **Platformer**: player = `PlatformerPlayer` + `Health` (die action RESPAWN) + `Respawn` (fall below -300) + `CameraFollow` (bounds = level size);
  spikes = `DamageOnTouch` (knockback 250, up 300, stun 0.25); flags = `Checkpoint`; coins = `Collectible` (group `coins`) as sensors; exit = `Goal` (collect group `coins`, next scene);
  HUD: a Text with `ScoreCounter`, a ProgressItem set as the *bar* of `Health`, a Text with `Timer`.
- **Top-down / space shooter**: `Spawner` with waves on the edge of the screen (template = enemy with `Chase`/`Patrol`, `Health`, `DamageOnTouch`);
  `Turret`s with *Auto health*; `PowerUp` pickups (Shooter `fireRate` x2 for 8 s); `CameraShake` + Health *Shake on hit*; `Goal` with *must be gone* = the boss.
- **Breakout**: bricks = `Destructible` (hits 2, damaged pictures, hitter prefix `ball`, optional drop = power-up) ; ball ; `Goal` with *need touch* off and *must be gone* = the bricks.

## Reference

### Health

Hit points for any element: damage, healing, invulnerability frames, regeneration, a progress bar that follows the health and what happens on death (destroy, hide, respawn, restart the scene or open another one).

Id `Health` · icon `heart` · category Combat · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Hit points** | | | | | |
| `maxHealth` | float | 100 | 1 .. 1000000 |  | Hit points when full. |
| `health` | float | 100 | 0 .. 1000000 |  | Hit points at the start of the scene. |
| `invulnerableTime` | float | 0 | 0 .. 30 (step 0.1) | s | After a hit nothing can hurt it for this long (also stops damage from several touching enemies at once). |
| `regenPerSecond` | float | 0 | 0 .. 10000 (step 0.5) | hp/s | Hit points recovered per second (0 = no regeneration). |
| `regenDelay` | float | 3 | 0 .. 60 (step 0.5) | s | Regeneration starts this long after the last hit. |
| **Display** | | | | | |
| `bar` | ProgressItem | none |  |  | A progress bar element that shows the health (its Max and Progress are driven for you). Optional. |
| `blinkWhileInvulnerable` | yes/no | `true` |  |  | Blink while invulnerable so the player sees the hit. |
| `shakeOnHit` | float | 0 | 0 .. 1 (step 0.05) |  | Shake the camera when hurt (needs a CameraShake in the scene). 0 = off. |
| `hurtSound` | sound | empty |  |  | Played when it takes damage. |
| `deathSound` | sound | empty |  |  | Played when it dies. |
| **Death** | | | | | |
| `dieAction` | choice | `DESTROY` | DESTROY, HIDE, RESPAWN, RESTART_SCENE, OPEN_SCENE, NOTHING |  | What happens when the hit points reach 0. RESPAWN needs a Respawn behavior on the same element (else the scene restarts). |
| `dieDelay` | float | 0 | 0 .. 30 (step 0.1) | s | Wait this long after dying before the action (time for your death animation). |
| `scene` | text | empty |  |  | Scene to open for OPEN_SCENE; for RESTART_SCENE leave empty to reopen the current scene (type its exact name if it has capital letters). |
| **Hit reaction** | | | | | |
| `stunBehaviors` | text | `PlatformerPlayer, TopDownPlayer, JoystickMovement` |  |  | Behaviors switched off while stunned by a hit that has a stun time (names separated by commas). |

**Events** (Events panel of the element)

- `onDamage(float amount, PlayerItem source)`
- `onHeal(float amount)`
- `onDeath(PlayerItem killer)`
- `onRevive()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| damage | amount (float) | Takes hit points away (respects invulnerability). |
| damage by | amount (float), source (PlayerItem) | Takes hit points away and tells who did it. |
| health *(value)* | - | Current hit points. |
| health percent *(value)* | - | Current hit points as 0..100 % of the maximum. |
| heal | amount (float) | Adds hit points (never above the maximum). |
| is dead *(value)* | - | True when the hit points reached 0. |
| is invulnerable *(value)* | - | True while a hit cannot hurt it. |
| kill | - | Sets the hit points to 0 right now (ignores invulnerability). |
| invulnerable for | seconds (float) | Nothing can hurt it for this many seconds (shield power-ups). |
| revive | fraction (float) | Brings it back to life with the given share of the maximum (0..1). |
| set health | health (float) | Sets the current hit points (0 = dead). |
| set max health | max (float), fill (boolean) | Changes the maximum; optionally heals to full. |

**Notes.** Put a ProgressItem in *bar* and the bar follows the health (its Max and Progress are driven for you). *Stun behaviors* are the movement behaviors that are switched off during a hit that has a stun time, so knockback is visible (movement behaviors set the velocity every frame). With *Blink while invulnerable* the element flickers at 12 Hz. `die action` RESTART_SCENE reopens the running scene by its class name (all lower case); if your scene name has capitals, type it in *scene*. OPEN_SCENE with an empty scene does nothing.

### DamageOnTouch

Spikes, lava, enemies, saws: hurts the elements that touch it (they need a Health or Destructible behavior), once per touch or again and again, with knockback.

Id `DamageOnTouch` · icon `skull-crossbones` · category Combat · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Who gets hurt** | | | | | |
| `targets` | list of PlayerItem | empty list |  |  | Elements that get hurt. Leave both fields empty to hurt anything that has a Health behavior. |
| `targetPrefix` | text | `player` |  |  | Also hurt elements whose name starts with one of these words (separate with commas), e.g. player, hero. |
| **Damage** | | | | | |
| `damage` | float | 10 | 0.1 .. 100000 |  | Hit points taken away by one hit. |
| `repeatEvery` | float | 0 | 0 .. 10 (step 0.1) | s | 0 = hurt once when the touch starts. Otherwise hurt again this often while the touch lasts (lava, fire). |
| `autoHealth` | float | 0 | 0 .. 100000 |  | Give a target without Health this many hit points so it can die (0 = leave it alone). |
| `destroySelfOnHit` | yes/no | `false` |  |  | This element disappears after hurting something (mines, thrown objects). |
| **Reaction** | | | | | |
| `knockback` | float | 0 | 0 .. 3000 | px/s | Push the target away horizontally, in px/s (0 = none). |
| `knockbackUp` | float | 0 | 0 .. 3000 | px/s | Push the target up, in px/s. |
| `stunTime` | float | 0 | 0 .. 5 (step 0.05) | s | The target's movement behaviors stay off for this long so the push is visible (see Health > Stun behaviors). |
| `hitSound` | sound | empty |  |  | Played when it hurts something. |

**Events** (Events panel of the element)

- `onHit(PlayerItem target, float damage)`
- `onKill(PlayerItem target)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| add target | target (PlayerItem) | This element will be hurt too. |
| damage *(value)* | - | Current damage of one hit. |
| hurt now | target (PlayerItem) | Hurts this element immediately with the damage and knockback of this behavior. |
| set damage | damage (float) | Hit points taken by one hit. |

**Notes.** With *Repeat every* 0 a target is hurt once per touch, and again as soon as its invulnerability ends if it is still touching (standing on spikes). With *Repeat every* > 0 it is hurt at that rhythm (lava, fire). Knockback pushes away from the center of this element. Needs a Dynamic body somewhere in the contact, or the target in the *targets* list.

### Destructible

Crates, bricks, walls: breaks after a number of hits (bullets, DamageOnTouch, or a ball touching it), shows damaged pictures on the way and can drop an item.

Id `Destructible` · icon `cargo-crate` · category Combat · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Durability** | | | | | |
| `hits` | int | 3 | 1 .. 10000 |  | How many hits it takes. |
| `everyHitCountsOne` | yes/no | `true` |  |  | Every hit counts as 1 whatever its damage. Off: the damage points are subtracted from the hits. |
| **Hit by touching** | | | | | |
| `hitters` | list of PlayerItem | empty list |  |  | Elements that hurt it by touching it (a ball for bricks). |
| `hitterPrefix` | text | empty |  |  | Also elements whose name starts with one of these words, e.g. ball (separate with commas). |
| **Look** | | | | | |
| `damagedImage1` | image | empty |  |  | Picture after the first hits (optional). Up to three pictures are spread over the damage. |
| `damagedImage2` | image | empty |  |  | Picture after more hits (optional). |
| `damagedImage3` | image | empty |  |  | Picture just before it breaks (optional). |
| `hitSound` | sound | empty |  |  | Played at every hit that does not break it. |
| `breakSound` | sound | empty |  |  | Played when it breaks. |
| **When it breaks** | | | | | |
| `breakAction` | choice | `DESTROY` | DESTROY, HIDE, NOTHING |  | DESTROY removes it, HIDE keeps it (invisible, no collision) so you can bring it back with reset. |
| `breakDelay` | float | 0 | 0 .. 10 (step 0.05) | s | Wait this long before it disappears (time for your own effect). |
| `dropItem` | PlayerItem | none |  |  | Element copied when it breaks (power-up, coin...). Put the original anywhere: it is hidden when the game starts. Optional. |
| `dropChance` | float | 100 | 0 .. 100 (step 5) | % | Chance of the drop. |

**Events** (Events panel of the element)

- `onHit(PlayerItem by, int hitsLeft)`
- `onDestroyed(PlayerItem by)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| destroy now | - | Breaks it immediately. |
| hits left *(value)* | - | How many hits it still takes. |
| hit | - | Counts as one hit. |
| is destroyed *(value)* | - | True after it broke. |
| reset | - | Brings it back whole (also if it was hidden when it broke). |
| set hits | hits (int) | Changes the hits it takes (and resets the damage). |

**Notes.** The pictures are spread evenly over the damage: picture k appears when k/(n+1) of the durability is gone (3 hits and 2 pictures: the first hit shows picture 1, the second picture 2, the third breaks it). *Reset* restores the original picture. The drop is a copy of an element that you place anywhere in the scene: it is hidden at the start and its behaviors (a Collectible, a PowerUp) are active in the copy.

### Projectile

Turns an element into a bullet, arrow or fireball: flies in a direction, hurts what it hits, stops at walls and disappears after its lifetime. Pair it with a Spawner to fire copies.

Id `Projectile` · icon `bullet` · category Combat · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Flight** | | | | | |
| `angle` | float | 0 | -360 .. 360 | deg | Direction: 0 = right, 90 = up, 180 = left, -90 = down. |
| `useElementRotation` | yes/no | `false` |  |  | Fly where the element points instead (rotate the element or its spawner to aim). |
| `speed` | float | 500 | 0 .. 5000 | px/s |  |
| `acceleration` | float | 0 | -5000 .. 5000 | px/s2 | Speeds up (or slows down with a negative value) every second. |
| `gravity` | yes/no | `false` |  |  | Gravity bends the flight (grenades, arrows). The speed is then only the launch speed. |
| `lifetime` | float | 3 | 0 .. 60 (step 0.1) | s | Disappears after this long. 0 = never. |
| `autoLaunch` | yes/no | `true` |  |  | Start flying when the element appears. Off: call launch yourself. |
| **Damage** | | | | | |
| `targets` | list of PlayerItem | empty list |  |  | Elements it hurts. Leave both fields empty to hurt anything that has Health. |
| `targetPrefix` | text | empty |  |  | Also elements whose name starts with one of these words (separate with commas), e.g. enemy. |
| `damage` | float | 1 | 0.1 .. 100000 |  | Hit points taken by one hit. |
| `autoHealth` | float | 0 | 0 .. 100000 |  | Give a target without Health this many hit points (0 = leave it alone). |
| `pierce` | int | 0 | 0 .. 100 |  | How many extra targets it can pass through before it stops. |
| `knockback` | float | 0 | 0 .. 3000 | px/s | Push the hit target along the flight, in px/s. |
| `hitSound` | sound | empty |  |  | Played when it hits a target. |
| **Obstacles** | | | | | |
| `obstacles` | list of PlayerItem | empty list |  |  | Walls / ground that stop it. |
| `obstaclePrefix` | text | empty |  |  | Also elements whose name starts with one of these words, e.g. wall, ground. |
| `destroyOnObstacle` | yes/no | `true` |  |  | Disappear when it touches an obstacle. |

**Events** (Events panel of the element)

- `onHit(PlayerItem target)`
- `onExpire()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| remove projectile | - | Makes it disappear now. |
| flying direction *(value)* | - | Current direction in degrees (0 = right, 90 = up). |
| launch | angle (float) | Starts flying in the given direction (0 = right, 90 = up). |
| set projectile damage | damage (float) | Damage of one hit. |
| set projectile speed | speed (float) | Speed in px/s (takes effect on the next launch, or now when it is flying without gravity). |

**Notes.** Put it on the bullet element. Pair with a `Spawner` whose template is the bullet, placed on the gun (spawn at spawner), and *use element rotation* to fire where the gun points - or call the `launch` block with an angle. A Dynamic bullet should have gravity scale 0 unless *gravity* is on (the behavior sets it).

### Turret

An automatic gun: finds the nearest target in range, turns toward it and fires bullets that hurt it (targets need a Health behavior, or give them hit points with Auto health). Bullets are reused, so it is cheap.

Id `Turret` · icon `cannon` · category Combat · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Targets** | | | | | |
| `targets` | list of PlayerItem | empty list |  |  | Elements it shoots at. |
| `targetPrefix` | text | `player` |  |  | Also elements whose name starts with one of these words (separate with commas), e.g. player, enemy. |
| `range` | float | 500 | 10 .. 10000 | px | It only sees targets this close. |
| **Aim** | | | | | |
| `turnSpeed` | float | 240 | 0 .. 3600 | deg/s | Turns toward the target this fast. 0 = instantly. |
| `imageAngle` | float | 0 | -360 .. 360 | deg | Direction the picture looks at rotation 0: 0 = right, 90 = up, 180 = left, -90 = down. |
| `rotateToTarget` | yes/no | `true` |  |  | Rotate the element toward the target. Off: it only fires, in the direction it already points. |
| `aimTolerance` | float | 8 | 0 .. 90 | deg | Fires only when it points this close to the target. |
| `leadTarget` | yes/no | `false` |  |  | Aims where a moving target will be instead of where it is. |
| **Fire** | | | | | |
| `active` | yes/no | `true` |  |  | Fires automatically. Off: call fire now yourself. |
| `fireRate` | float | 1.5 | 0.05 .. 30 (step 0.05) |  | Volleys per second. |
| `burst` | int | 1 | 1 .. 20 |  | Bullets in one volley. |
| `burstGap` | float | 0.10000000149011612 | 0.02 .. 2 (step 0.01) | s | Pause between the bullets of a volley. |
| `spread` | float | 0 | 0 .. 90 (step 0.5) | deg | Random sideways error of each bullet. |
| `muzzle` | float | 0 | 0 .. 1000 | px | Bullets start this far from the center, in the firing direction (the end of the barrel). |
| **Bullet** | | | | | |
| `bulletImage` | image | empty |  |  | Picture of a bullet. Empty = a colored square. |
| `bulletColor` | color | `#FF5252` |  |  | Color of the square (or tint of the picture). |
| `bulletSize` | float | 14 | 2 .. 300 | px |  |
| `bulletSpeed` | float | 400 | 10 .. 3000 | px/s |  |
| `bulletLifetime` | float | 2 | 0.1 .. 30 | s | A bullet that hits nothing disappears after this long. |
| `damage` | float | 1 | 0.1 .. 100000 |  | Hit points taken by one bullet. |
| `autoHealth` | float | 0 | 0 .. 100000 |  | Give a target without Health this many hit points (0 = leave it alone). |
| `knockback` | float | 0 | 0 .. 3000 | px/s | Push the target along the bullet's direction. |
| `shootSound` | sound | empty |  |  | Played at every shot. |
| `hitSound` | sound | empty |  |  | Played when a bullet hits. |
| **Obstacles** | | | | | |
| `obstacles` | list of PlayerItem | empty list |  |  | Walls that stop the bullets. |
| `obstaclePrefix` | text | empty |  |  | Also elements whose name starts with one of these words, e.g. wall, ground. |

**Events** (Events panel of the element)

- `onShoot()`
- `onTargetFound(PlayerItem target)`
- `onTargetLost()`
- `onHit(PlayerItem target)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| add turret target | target (PlayerItem) | The turret will shoot this element too. |
| fire now | - | Fires one bullet in the direction the turret points. |
| turret aim angle *(value)* | - | Direction it points, in degrees (0 = right, 90 = up). |
| turret target *(value)* | - | The element it is aiming at (or nothing). |
| turret has target *(value)* | - | True while a target is in range. |
| turret on | on (boolean) | On: the turret works. Off: it stops aiming and firing. |
| set turret damage | damage (float) | Damage of one bullet. |
| set turret fire rate | rate (float) | Volleys per second. |
| set turret range | range (float) | How far it sees (px). |

**Notes.** The bullets are created once and reused (at most 64), so a fast turret does not create garbage. Change *bullet size* and the image before the game starts. The turret rotates its own body: set *image angle* to the direction the picture looks (0 = right). Use *fire now* with *auto fire off* (`turret on` false) for turrets that fire on a button or timer.

### Spawner

Creates copies of a template element while the game runs: enemies, coins, rocks. Every few seconds or in waves, at the spawner, inside its area, on a ring around it or at spawn points, with a limit of how many live at once.

Id `Spawner` · icon `portal` · category Combat · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **What** | | | | | |
| `template` | PlayerItem | none |  |  | The element to copy. It is hidden when the game starts; every copy keeps its behaviors, events and properties. |
| `moreTemplates` | list of PlayerItem | empty list |  |  | More elements to copy: each spawn picks one of all the templates at random. |
| `spawnedLifetime` | float | 0 | 0 .. 3600 (step 0.5) | s | Every copy removes itself after this long (0 = never). |
| **When** | | | | | |
| `autoStart` | yes/no | `true` |  |  | Start spawning when the scene starts. Off: call start spawning. |
| `firstDelay` | float | 0 | 0 .. 600 (step 0.5) | s | Wait this long before the first spawn. |
| `interval` | float | 2 | 0.05 .. 600 (step 0.05) | s | Seconds between two spawns (in waves: between two members of one wave). |
| `maxAlive` | int | 10 | 0 .. 300 |  | No new copy while this many are alive. 0 = no limit (the engine stops at 300). |
| `totalLimit` | int | 0 | 0 .. 100000 |  | Stop for good after this many spawns. 0 = never. |
| `speedUp` | float | 0 | 0 .. 50 (step 1) | % | Each spawn (each wave in wave mode) makes the interval this much shorter. |
| `minInterval` | float | 0.30000001192092896 | 0.05 .. 60 (step 0.05) | s | The interval never gets shorter than this. |
| **Where** | | | | | |
| `area` | choice | `AT_SPAWNER` | AT_SPAWNER, INSIDE_SELF, RING, POINTS |  | AT_SPAWNER: on the spawner. INSIDE_SELF: anywhere in its box. RING: around it. POINTS: at the marker elements below. |
| `radiusMin` | float | 0 | 0 .. 10000 | px | RING: nearest distance from the spawner. |
| `radiusMax` | float | 300 | 0 .. 10000 | px | RING: farthest distance from the spawner. |
| `points` | list of PlayerItem | empty list |  |  | POINTS: elements that mark where copies appear. |
| `randomPoint` | yes/no | `true` |  |  | POINTS: pick a random point. Off: go through them in order. |
| **Waves** | | | | | |
| `useWaves` | yes/no | `false` |  |  | Spawn in waves: a group, a pause, a bigger group... |
| `waveSize` | int | 5 | 1 .. 300 |  | Members of the first wave. |
| `waveGrowth` | int | 2 | 0 .. 100 |  | Every wave has this many members more than the one before. |
| `waveCount` | int | 3 | 0 .. 1000 |  | Number of waves. 0 = endless. |
| `waveDelay` | float | 4 | 0 .. 600 (step 0.5) | s | Pause between two waves. |
| `waitUntilCleared` | yes/no | `true` |  |  | A wave is over (and the next may start) only when all its members are gone. |
| `loopWaves` | yes/no | `false` |  |  | Start again with the first wave after the last one. |
| `spawnSound` | sound | empty |  |  | Played at every spawn. |

**Events** (Events panel of the element)

- `onSpawn(PlayerItem spawned)`
- `onWaveStart(int wave)`
- `onWaveDone(int wave)`
- `onAllWavesDone()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| remove all spawned | - | Removes every copy this spawner made. |
| alive count *(value)* | - | How many copies of this spawner are alive. |
| spawned count *(value)* | - | How many copies were made so far. |
| current wave *(value)* | - | Number of the wave in progress (1 = first), 0 before the first. |
| is spawning *(value)* | - | True while the spawner is running. |
| next wave now | - | Skips the pause and starts the next wave. |
| restart spawner | - | Counts from zero again (spawns, waves, speed-up) and starts. |
| set spawn interval | seconds (float) | Seconds between two spawns. |
| set max alive | count (int) | No new copy while this many are alive (0 = no limit). |
| spawn at | x (float), y (float) | Spawns one copy with its center at this position (px). |
| spawn now | - | Spawns one copy right away, ignoring the interval and the alive limit. |
| start spawning | - | Starts (or continues) spawning. |
| stop spawning | - | Pauses spawning. The copies that exist stay. |

**Notes.** Place the spawner anywhere; the *template* is any element of the scene (it is hidden). Copies keep the template's properties, behaviors and events and are named `<template>_c<n>`. If the spawner is on the template itself, the copies do not get a spawner. *Max alive* counts the copies that exist; they leave the count when destroyed. In wave mode wave n has `wave size + wave growth * (n-1)` members; *wait until cleared* starts the next wave only when none of this spawner's copies is left. Spawning happens in the next frame after a block asks for it (safe inside collision events).

### Collectible

Coins, gems, keys, hearts: when the player touches it, it adds a value to a scene variable (score, coins, keys), can heal, plays a sound and disappears or comes back later.

Id `Collectible` · icon `coin` · category Gameplay · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Who can pick it up** | | | | | |
| `collectors` | list of PlayerItem | empty list |  |  | Elements that can collect it. |
| `collectorPrefix` | text | `player` |  |  | Also elements whose name starts with one of these words (separate with commas). Leave the list and this text empty to let any element with a Health behavior collect it. |
| **Reward** | | | | | |
| `variable` | text | `score` |  |  | Name of the scene variable (create it in Variables: an int or float), e.g. score or coins. Empty = no variable. |
| `value` | float | 1 | -1000000 .. 1000000 |  | Added to the variable when collected. |
| `heal` | float | 0 | 0 .. 100000 |  | Heals the collector (needs a Health behavior on it). |
| `group` | text | empty |  |  | Counts for a Goal that wants every collectible of this group (coins, stars...). Empty = no group. |
| `collectSound` | sound | empty |  |  | Played when it is collected. |
| **After** | | | | | |
| `afterCollect` | choice | `DESTROY` | DESTROY, HIDE, NOTHING |  | DESTROY removes it, HIDE keeps it invisible (comes back with respawn after), NOTHING leaves it. |
| `popTime` | float | 0.11999999731779099 | 0 .. 2 (step 0.05) | s | A short pop (grow and fade) before it disappears. |
| `respawnAfter` | float | 0 | 0 .. 3600 (step 0.5) | s | HIDE only: it comes back after this long. 0 = never. |

**Events** (Events panel of the element)

- `onCollected(PlayerItem collector, float value)`
- `onRespawned()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| bring back | - | Shows it again and lets it be collected once more. |
| collect | collector (PlayerItem) | Collects it right now as if this element touched it. |
| is collected *(value)* | - | True after it was collected (and not yet brought back). |
| set reward | value (float) | What it adds to the variable when collected. |

**Notes.** Make the element a Sensor, so the player walks through it. The reward is added to the scene variable by name, the *group* is what a `Goal` counts. With HIDE + *respawn after* the coin comes back and counts again. `collect` lets blocks collect it (a magnet, a key press).

### PowerUp

A pickup that changes the settings of a behavior on whoever takes it for a while (faster, higher jumps, faster fire...), then puts them back. Can also heal and make invulnerable.

Id `PowerUp` · icon `lightning-bolt` · category Gameplay · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Who can pick it up** | | | | | |
| `collectors` | list of PlayerItem | empty list |  |  | Elements that can take it. |
| `collectorPrefix` | text | `player` |  |  | Also elements whose name starts with one of these words (separate with commas). Leave the list and this text empty to let any element with a Health behavior take it. |
| **Effect 1** | | | | | |
| `behavior1` | text | `PlatformerPlayer` |  |  | Class name of the behavior on the collector, e.g. PlatformerPlayer, Shooter, TopDownPlayer. Empty = no effect. |
| `parameter1` | text | `jumpSpeed` |  |  | The setting (public field) to change, exactly as written in the behavior, e.g. jumpSpeed, speed, fireRate. |
| `mode1` | choice | `MULTIPLY` | SET, ADD, MULTIPLY |  | SET puts the value, ADD adds it, MULTIPLY multiplies the current value. |
| `amount1` | float | 1.5 | -100000 .. 100000 |  |  |
| **Effect 2** | | | | | |
| `behavior2` | text | empty |  |  | A second setting to change at the same time (optional). |
| `parameter2` | text | `speed` |  |  | The second setting to change, exactly as written in the behavior. |
| `mode2` | choice | `MULTIPLY` | SET, ADD, MULTIPLY |  | SET puts the value, ADD adds it, MULTIPLY multiplies the current value. |
| `amount2` | float | 1.5 | -100000 .. 100000 |  |  |
| **Duration and extras** | | | | | |
| `duration` | float | 10 | 0 .. 3600 (step 0.5) | s | How long the effects last. 0 = for ever. |
| `heal` | float | 0 | 0 .. 100000 |  | Hit points restored to the collector (needs Health). |
| `shield` | float | 0 | 0 .. 600 (step 0.5) | s | The collector cannot be hurt for this long (needs Health). |
| `collectSound` | sound | empty |  |  | Played when it is taken. |
| **After** | | | | | |
| `afterCollect` | choice | `DESTROY` | DESTROY, HIDE |  | DESTROY removes it once the effects ended, HIDE only hides it. |
| `respawnAfter` | float | 0 | 0 .. 3600 (step 0.5) | s | It comes back after this long (so the same power-up can be taken again). 0 = never. |

**Events** (Events panel of the element)

- `onCollected(PlayerItem collector)`
- `onExpired(PlayerItem collector)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| apply to | target (PlayerItem) | Gives the effect to this element now, as if it had taken the power-up. |
| end all effects | - | Puts back all the settings right now. |
| active effects *(value)* | - | How many collectors currently have the effect. |

**Notes.** The *behavior* is the class name on the collector and the *setting* is a field of it, exactly as written in the behavior (see the parameters tables of the behaviors: `PlatformerPlayer` -> `jumpSpeed`, `speed`; `Shooter` -> `fireRate`, `damage`). The old value is remembered and put back when the time is over; taking the power-up again while it is active only extends the time. The pickup stays around (invisible) until the effects ended, then it is destroyed. Permanent effects (*duration* 0) are never put back.

### Checkpoint

A flag that remembers where to come back to: when the player touches it, the Respawn behavior returns there after death or a fall. Works across scene restarts (and, if you want, across app launches).

Id `Checkpoint` · icon `flag-variant` · category Gameplay · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Who activates it** | | | | | |
| `targets` | list of PlayerItem | empty list |  |  | Elements that activate it. |
| `targetPrefix` | text | `player` |  |  | Also elements whose name starts with one of these words (separate with commas). |
| **Respawn point** | | | | | |
| `channel` | text | empty |  |  | Checkpoints and Respawn behaviors with the same channel work together. Use different channels for different players. |
| `offsetX` | float | 0 | -2000 .. 2000 | px | The player comes back this far to the right of the flag's center (negative = left). |
| `offsetY` | float | 0 | -2000 .. 2000 | px | The player comes back this far above the flag's center. |
| `saveToDisk` | yes/no | `false` |  |  | Keep the checkpoint after the app is closed (Preferences). Off: it lasts until the game is closed or forgetCheckpoints is called. |
| **Look** | | | | | |
| `activeImage` | image | empty |  |  | Picture after it is activated (optional), e.g. a raised flag. |
| `activateSound` | sound | empty |  |  | Played when it is activated. |

**Events** (Events panel of the element)

- `onActivated(PlayerItem by)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| activate checkpoint | - | Makes this the place to come back to. |
| forget checkpoints | - | Erases the saved respawn point of this channel (use it for a new game). |
| is active checkpoint *(value)* | - | True if this is the checkpoint the player returns to. |

**Notes.** The respawn point is stored per *channel* in memory (it survives restarting the scene) and optionally on disk. Call `forget checkpoints` from a New Game button. A `Goal` clears the default channel when the level is won.

### Respawn

Brings the player back to the last Checkpoint (or to where it started) after dying or falling out of the world, with a short invulnerability, an optional lives counter and game over scene.

Id `Respawn` · icon `restart` · category Gameplay · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Where** | | | | | |
| `channel` | text | empty |  |  | Must match the channel of the Checkpoints it should use. |
| `startAtCheckpoint` | yes/no | `true` |  |  | At the start of the scene, move to the saved checkpoint if there is one (after a restart). |
| `saveToDisk` | yes/no | `false` |  |  | Also look for a checkpoint saved on disk. |
| **When** | | | | | |
| `respawnWhenFalling` | yes/no | `false` |  |  | Respawn when the element falls below the line below (platformer pits). |
| `fallBelowY` | float | -300 | -100000 .. 100000 | px | Height of the line (editor pixels, y up). Below this the element respawns. |
| **How** | | | | | |
| `delay` | float | 0.5 | 0 .. 10 (step 0.1) | s | Wait this long (invisible) before coming back. |
| `invulnerableTime` | float | 2 | 0 .. 30 (step 0.1) | s | Nothing can hurt it for this long after coming back (needs Health). |
| `restoreHealth` | yes/no | `true` |  |  | Hit points are full again (needs Health). |
| `respawnSound` | sound | empty |  |  | Played when it comes back. |
| **Lives** | | | | | |
| `lives` | int | 0 | 0 .. 99 |  | Number of lives. 0 = unlimited. When the last one is lost the scene below opens. |
| `livesVariable` | text | empty |  |  | Scene variable (int) that always shows the lives left, e.g. lives. Empty = none. |
| `gameOverScene` | text | empty |  |  | Scene to open when there are no lives left (game over). Empty = do nothing, only the event fires. |

**Events** (Events panel of the element)

- `onRespawn(float x, float y)`
- `onOutOfLives()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| add life | count (int) | Gives extra lives. |
| forget checkpoints | - | Erases the saved respawn point (new game). |
| lives left *(value)* | - | Lives that remain (0 when lives are unlimited). |
| reset lives | - | Back to the full number of lives. |
| respawn | - | Takes a life and brings the element back to the last checkpoint after the delay. |
| set respawn point | x (float), y (float) | Chooses the place to come back to (center, px). |

**Notes.** Set the die action of `Health` to RESPAWN. With *lives* > 0 every respawn takes one life; on the last one `onOutOfLives` fires and the *game over scene* opens. *Lives variable* keeps a scene variable in sync, show it with a ScoreCounter. At the start of the scene the element moves to the saved checkpoint if there is one.

### Lifetime

The element removes itself after a while, fading out (and optionally shrinking) first. Bullets, particles, temporary platforms, hints.

Id `Lifetime` · icon `timer-sand` · category Logic · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| `seconds` | float | 3 | 0.05 .. 3600 (step 0.1) | s | How long it lives, counted from when the scene starts (or from reset). |
| `randomExtra` | float | 0 | 0 .. 60 (step 0.1) | s | Random extra life between -x and +x seconds, so a crowd does not vanish all at once. |
| `fadeTime` | float | 0.30000001192092896 | 0 .. 60 (step 0.1) | s | Fades out during the last seconds. |
| `shrink` | yes/no | `false` |  |  | Also shrinks to nothing while it fades. |
| `endAction` | choice | `DESTROY` | DESTROY, HIDE |  | DESTROY removes the element, HIDE keeps it invisible and inactive. |

**Events** (Events panel of the element)

- `onExpire()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| add life | seconds (float) | Adds (or with a negative number removes) seconds of life. |
| life left *(value)* | - | Seconds of life that remain. |
| restart lifetime | - | Starts counting its life again from the full time. |
| set life left | seconds (float) | Sets the remaining seconds of life. |

**Notes.** Works on any element, also on UI. The *fade* happens during the last seconds. With HIDE the element is only hidden (alpha 0, no body). `restart lifetime` brings back the full time and the look.

### Timer

A countdown or a stopwatch shown in a Text element: level time limits, race times, cooldown displays. Start, pause and reset it with blocks; it can also write the seconds into a scene variable.

Id `Timer` · icon `timer` · category Logic · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Timer** | | | | | |
| `mode` | choice | `COUNTDOWN` | COUNTDOWN, STOPWATCH |  | COUNTDOWN counts down from the seconds below to 0, STOPWATCH counts up. |
| `seconds` | float | 60 | 0 .. 360000 (step 1) | s | Countdown: where it starts. Stopwatch: stops (onTimeout) at this time, 0 = never. |
| `autoStart` | yes/no | `true` |  |  | Starts when the scene starts. Off: call start timer. |
| `loop` | yes/no | `false` |  |  | Starts again by itself after onTimeout. |
| **Display** | | | | | |
| `display` | TextItem | none |  |  | The Text element that shows the time. Empty = this element (when it is a Text). |
| `format` | choice | `M_SS` | M_SS, SECONDS, TENTHS, M_SS_CC |  | M_SS = 1:05, SECONDS = 65, TENTHS = 65.3, M_SS_CC = 01:05.30. |
| `prefix` | text | empty |  |  | Written before the time, e.g. TIME  |
| `suffix` | text | empty |  |  | Written after the time, e.g. s |
| `warnAt` | float | 10 | 0 .. 3600 (step 1) | s | Countdown: the text turns the warning color and onWarning fires when this many seconds are left. 0 = off. |
| `warnColor` | color | `#FF5252` |  |  | Color of the text during the warning. |
| **Variable** | | | | | |
| `variable` | text | empty |  |  | Scene variable (int or float) that receives the time in seconds. Empty = none. |

**Events** (Events panel of the element)

- `onTimeout()`
- `onTick(int seconds)`
- `onWarning()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| add time | seconds (float) | Adds seconds to the time shown (countdown: more time left; stopwatch: later). |
| time *(value)* | - | The time shown, in seconds (countdown: left; stopwatch: elapsed). |
| timer is running *(value)* | - | True while it counts. |
| pause timer | - | Stops counting; the time stays. |
| reset timer | - | Back to the starting time (the running state does not change). |
| restart timer | - | Reset and start. |
| set time | seconds (float) | Sets the time shown, in seconds. |
| start timer | - | Starts (or resumes) the timer. |

**Notes.** The text shows `prefix + time + suffix`. Countdowns round up (the last second shows 0:01 until the time is over), stopwatches round down. The color of the text changes at the warning time and returns on reset. *Variable* receives the seconds (so a Goal or Door can check them). `onTick(seconds)` fires once per second.

### ScoreCounter

Shows a scene variable (score, coins, lives...) in a Text element with a prefix, padding and a counting animation, and keeps a high score on the device.

Id `ScoreCounter` · icon `scoreboard` · category Logic · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Value** | | | | | |
| `variable` | text | `score` |  |  | Scene variable to show (an int or float created in Variables). Collectible and Timer write to variables by name. Empty = the counter keeps its own number. |
| **Display** | | | | | |
| `display` | TextItem | none |  |  | The Text element that shows it. Empty = this element (when it is a Text). |
| `prefix` | text | empty |  |  | Written before the number, e.g. SCORE  |
| `suffix` | text | empty |  |  | Written after the number, e.g.  pts |
| `decimals` | int | 0 | 0 .. 6 |  | Digits after the decimal point. |
| `padDigits` | int | 0 | 0 .. 12 |  | Fill with zeros up to this many digits (0005). 0 = off. |
| `thousandsSeparator` | yes/no | `false` |  |  | Group digits: 12,345. |
| `animate` | yes/no | `true` |  |  | The number runs up/down to the new value instead of jumping. |
| `animateTime` | float | 0.4000000059604645 | 0.05 .. 5 (step 0.05) | s | How long the counting takes at most. |
| **High score** | | | | | |
| `highScoreKey` | text | empty |  |  | Name under which the best value is stored on the device (e.g. highscore_level1). Empty = no high score. |
| `highScoreDisplay` | TextItem | none |  |  | Text element that shows the high score (optional). |
| `highScorePrefix` | text | `BEST ` |  |  | Written before the high score. |

**Events** (Events panel of the element)

- `onScoreChanged(float value)`
- `onNewHighScore(float value)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| add score | points (float) | Adds points (negative = subtracts). |
| high score *(value)* | - | The best value stored on the device. |
| score *(value)* | - | Current value. |
| erase high score | - | Sets the stored best value back to 0. |
| reset score | - | Back to 0 without the counting animation. |
| set score | value (float) | Sets the value. |

**Notes.** Put it on the Text itself or point *display* at one. The number runs to the new value over at most *animate time*. The high score is stored on the device under *high score key* (Preferences, at most once a second); give each level its own key. `erase high score` resets it.

### Door

A door, portal or exit: when the player touches it (or presses a button next to it) it opens another scene or teleports the player to another element. Can need a key (a scene variable) first.

Id `Door` · icon `door-open` · category Gameplay · works on physics elements · needs a physical element

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Who** | | | | | |
| `targets` | list of PlayerItem | empty list |  |  | Elements that can use it. |
| `targetPrefix` | text | `player` |  |  | Also elements whose name starts with one of these words (separate with commas). |
| **What it does** | | | | | |
| `mode` | choice | `OPEN_SCENE` | OPEN_SCENE, TELEPORT |  | OPEN_SCENE opens another scene, TELEPORT moves the player to the destination element. |
| `scene` | text | empty |  |  | OPEN_SCENE: the scene to open (exact name). |
| `destination` | PlayerItem | none |  |  | TELEPORT: the player appears at the center of this element (another Door makes a two-way portal). |
| `delay` | float | 0 | 0 .. 10 (step 0.1) | s | Wait this long after the door opens (time for a sound or an animation). |
| **How it opens** | | | | | |
| `trigger` | choice | `ON_TOUCH` | ON_TOUCH, WHEN_PRESSED |  | ON_TOUCH: at once. WHEN_PRESSED: the player must also hold the button below (or press Up / W on a keyboard). |
| `useButton` | PlayerItem | none |  |  | The button element to hold with WHEN_PRESSED (optional). |
| `needVariable` | text | empty |  |  | A scene variable that must reach the number below, e.g. keys. Empty = always open. |
| `needAtLeast` | float | 1 | 0 .. 1000000 |  | The needed amount of the variable. |
| `consume` | yes/no | `false` |  |  | Using the door takes that amount away from the variable (the key is used up). |
| `openSound` | sound | empty |  |  | Played when the door opens. |
| `lockedSound` | sound | empty |  |  | Played when the player touches the door but the key is missing. |

**Events** (Events panel of the element)

- `onOpen(PlayerItem who)`
- `onLocked(PlayerItem who)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| can open *(value)* | - | True if the key condition is met. |
| set door scene | scene (String) | The scene this door opens. |
| use door | who (PlayerItem) | This element uses the door now (checks the key first). |

**Notes.** Set the element as a Sensor. TELEPORT with another Door at the destination gives a two-way portal: a teleported element is ignored by doors for one second. *Need variable* / *consume* implement keys: a Collectible adds 1 to `keys`, the Door needs 1 and takes it. WHEN_PRESSED works with a button element or the Up / W key.

### Goal

The finish line / win condition: the level is won when the player reaches it AND the conditions are met (all coins of a group collected, a score reached, all enemies gone). Shows a win panel and can open the next scene.

Id `Goal` · icon `trophy` · category Gameplay · works on physics elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Who wins** | | | | | |
| `targets` | list of PlayerItem | empty list |  |  | Elements that win by touching it. |
| `targetPrefix` | text | `player` |  |  | Also elements whose name starts with one of these words (separate with commas). |
| `needTouch` | yes/no | `true` |  |  | Off: no touch is needed, the level is won as soon as the conditions below are met (e.g. collect all stars). |
| **Conditions** | | | | | |
| `collectGroup` | text | empty |  |  | Group name of Collectibles that must ALL be collected. Empty = no condition. |
| `needVariable` | text | empty |  |  | A scene variable that must reach the number below, e.g. score or keys. Empty = no condition. |
| `needAtLeast` | float | 1 | -1000000 .. 1000000 |  | The needed amount of the variable. |
| `mustBeGone` | list of PlayerItem | empty list |  |  | Elements that must all be destroyed first (bosses, enemies). |
| **When won** | | | | | |
| `winPanel` | PlayerItem | none |  |  | An element to show (a 'You win' panel). It is hidden when the game starts. |
| `nextScene` | text | empty |  |  | Scene to open after the delay (the next level). Empty = stay. |
| `delay` | float | 1 | 0 .. 30 (step 0.1) | s | Wait this long before opening the scene. |
| `clearCheckpoints` | yes/no | `true` |  |  | Forget the checkpoints when the level is won (so the next play starts at the beginning). |
| `winSound` | sound | empty |  |  | Played when the level is won. |

**Events** (Events panel of the element)

- `onWin(PlayerItem winner)`
- `onBlocked(int missing)`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| conditions met *(value)* | - | True when every condition (not the touch) is fulfilled. |
| is won *(value)* | - | True once the level was won. |
| reset goal | - | The level is not won any more (for retries). |
| set next scene | scene (String) | The scene to open after winning. |
| win now | - | Wins the level right away, whatever the conditions say. |

**Notes.** *Collect group* is satisfied when every Collectible of that group has been collected (collectibles destroyed for other reasons are not counted). *Must be gone* lists elements that must be destroyed (boss, enemies). With *need touch* off the level is won the moment the conditions are met. The *win panel* is hidden at the start and shown on the win. `onBlocked(missing)` tells how many conditions are open when the player touches the goal too early.

### CameraFollow

The camera follows an element smoothly, with a dead zone, look-ahead in the direction of movement, scene limits and zoom. Works together with Camera Shake. Put it on the player or on any element and choose the target.

Id `CameraFollow` · icon `camera` · category Camera · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Target** | | | | | |
| `target` | PlayerItem | none |  |  | The element to follow. Empty = the element that has this behavior. |
| `followX` | yes/no | `true` |  |  | Follow horizontally. |
| `followY` | yes/no | `true` |  |  | Follow vertically. |
| `offsetX` | float | 0 | -5000 .. 5000 | px | Look this far to the right of the target (negative = left). |
| `offsetY` | float | 0 | -5000 .. 5000 | px | Look this far above the target (negative = below). |
| `snapOnStart` | yes/no | `true` |  |  | Put the camera on the target at the start (off: glide from where the camera is). |
| **Feel** | | | | | |
| `followSpeed` | float | 6 | 0 .. 50 (step 0.5) |  | How fast the camera catches up. Higher = tighter. 0 = glued to the target. |
| `deadZoneWidth` | float | 0 | 0 .. 4000 | px | The target can move this far sideways before the camera moves at all. |
| `deadZoneHeight` | float | 0 | 0 .. 4000 | px | Same for the vertical movement. |
| `lookAhead` | float | 0 | 0 .. 2000 | px | The camera looks this far ahead in the direction the target moves (physical targets). |
| `lookAheadSpeed` | float | 300 | 10 .. 5000 | px/s | Target speed at which the look-ahead is complete. |
| **Zoom** | | | | | |
| `zoom` | float | 1 | 0.2 .. 5 (step 0.05) |  | 1 = the scene's normal view, 2 = twice as close, 0.5 = twice as far. |
| `zoomSpeed` | float | 3 | 0 .. 20 (step 0.5) |  | How fast the zoom changes. 0 = at once. |
| **Limits** | | | | | |
| `useBounds` | yes/no | `false` |  |  | Keep the view inside the rectangle below (the size of your level), so it never shows the empty outside. |
| `boundsLeft` | float | 0 | -100000 .. 100000 | px |  |
| `boundsBottom` | float | 0 | -100000 .. 100000 | px |  |
| `boundsRight` | float | 1280 | -100000 .. 100000 | px |  |
| `boundsTop` | float | 720 | -100000 .. 100000 | px |  |

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| camera no limits | - | Removes the camera limits. |
| camera zoom level *(value)* | - | The zoom that is set (not the one in the middle of a change). |
| camera follows axes | horizontal (boolean), vertical (boolean) | Choose which directions the camera follows. |
| camera limits | left (float), bottom (float), right (float), top (float) | Keeps the camera inside this rectangle (editor px). |
| camera offset | x (float), y (float) | Looks this far from the target (px). |
| camera follow | target (PlayerItem) | The camera follows this element from now on. |
| camera zoom | zoom (float) | Zoom: 1 = normal, 2 = twice as close. It changes smoothly. |
| camera jump to target | - | Moves the camera onto the target at once (no gliding). |

**Notes.** Put it on the player (target empty) or on any element and pick the target. *Follow speed* is a catch-up rate (higher = tighter, independent of the frame rate). The dead zone keeps the camera still while the target moves inside the box. *Bounds* are a rectangle in editor pixels (your level); when the level is smaller than the screen the camera is centered. Zoom 2 = twice as close; if the scene has a Camera element, set the zoom in only one of them.

### CameraShake

Screen shake for hits, explosions and landings. Call shake with a strength from 0 to 1; strong hits shake much more than weak ones and fade out by themselves. Health can trigger it too (Shake on hit). Works with or without Camera Follow.

Id `CameraShake` · icon `vibrate` · category Camera · works on both elements

**Parameters**

| Parameter | Type | Default | Range / choices | Unit | Meaning |
|---|---|---|---|---|---|
| **Shake** | | | | | |
| `maxOffset` | float | 24 | 1 .. 500 | px | Largest displacement of the camera at full strength. |
| `frequency` | float | 25 | 1 .. 80 | Hz | How fast it trembles. |
| `decay` | float | 1.2000000476837158 | 0.1 .. 10 (step 0.1) |  | How fast the strength fades, per second (1 = a full shake lasts one second). |
| `power` | float | 2 | 1 .. 4 (step 0.5) |  | 2 makes weak hits very gentle and strong hits violent; 1 is linear. |
| `shakeX` | yes/no | `true` |  |  | Shake sideways. |
| `shakeY` | yes/no | `true` |  |  | Shake up and down. |
| `shakeOnStart` | float | 0 | 0 .. 1 (step 0.05) |  | Shake once when the scene starts (0 = no). |

**Events** (Events panel of the element)

- `onShakeEnd()`

**Blocks** (shown while the scene has an element with this behavior)

| Block | Arguments | What it does |
|---|---|---|
| camera is shaking *(value)* | - | True while the screen shakes. |
| shake camera | strength (float) | Adds a shake: 0.2 = a bump, 0.5 = a hit, 1 = an explosion. |
| shake camera for | strength (float), seconds (float) | Keeps shaking at this strength for some seconds, then fades (earthquakes, engines). |
| stop camera shake | - | Ends the shake immediately. |

**Notes.** Strength 0..1 is added to a *trauma* that fades by itself; the displacement grows with trauma^*power* so a gentle bump and an explosion feel different. `shake camera for` keeps a minimum strength for some seconds (earthquake, engine). One CameraShake per scene is enough; Health's *Shake on hit* needs it.
