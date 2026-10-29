#!/usr/bin/env python3
"""Tests of the movement / AI / vehicle behavior library (behaviors/lib: TopDownPlayer, SpaceShip, Car, Follow, Patrol, Chase,
MovingPlatform, JumpPad, Bounce, WrapScreen, LimitToScreen, LookAtTarget, Orbit, TapToMove, TiltMove). No phone needed.
  1. metadata: every class loads through the K1 metadata readers (reflection + text scanner, they must agree), the icon exists in
     assets/icons/catalog.json, every public field has @Param with a description and a sane min/max, defaults inside the range,
     @Block texts (English + Arabic), events fired == events declared, no android.* and no lambdas (Java 7 on the device).
  2. a headless Box2D simulation (no OpenGL): fake stage and elements, scripted Gdx.input; asserts the motion is sensible
     (right direction, y up, no NaN, speed limits, turning at walls/ledges, wheels drive a joint-built car, ...).
Needs the template classes compiled once:  bash gradlew :template:compileDebugJavaWithJavac  (the gdx jars come from ~/.gradle).
Usage: python3 tools/test_lib_movement.py
"""
import glob, os, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAMES = ["TopDownPlayer", "SpaceShip", "Car", "Follow", "Patrol", "Chase", "MovingPlatform", "JumpPad", "Bounce", "WrapScreen",
         "LimitToScreen", "LookAtTarget", "Orbit", "TapToMove", "TiltMove"]


def main():
    classes = os.path.join(ROOT, "template/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    if not os.path.isdir(classes):
        print("template classes missing: run  bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    cp_sh = subprocess.run(["bash", os.path.join(ROOT, "tools/ui/classpath.sh")], capture_output=True, text=True)
    jars = cp_sh.stdout.strip()
    if not jars:
        print("gdx jars not found in ~/.gradle (classpath.sh printed nothing)")
        return 2
    tmp = tempfile.mkdtemp(prefix="movement_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes, jars])
    lib = os.path.join(ROOT, "app/src/main/java/com/star4droid/template/behaviors/lib")
    srcs = [os.path.join(lib, n + ".java") for n in NAMES]
    srcs += glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/behaviors/*.java"))
    srcs += [os.path.join(ROOT, "tools/lib_movement_test/MovementTest.java")]
    r = subprocess.run(["javac", "-nowarn", "-source", "8", "-target", "8", "-Xlint:-options", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + "\n".join(l for l in r.stderr.splitlines() if "Picked up" not in l))
        return 1
    r = subprocess.run(["xvfb-run", "-a", "java", "-cp", os.pathsep.join([out, cp]), "MovementTest", ROOT], capture_output=True, text=True) \
        if subprocess.run(["which", "xvfb-run"], capture_output=True).returncode == 0 else \
        subprocess.run(["java", "-cp", os.pathsep.join([out, cp]), "MovementTest", ROOT], capture_output=True, text=True)
    print("\n".join(l for l in (r.stdout + r.stderr).splitlines() if "Picked up" not in l))
    return r.returncode


if __name__ == "__main__":
    sys.exit(main())
