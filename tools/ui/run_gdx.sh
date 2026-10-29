#!/bin/bash
# Headless run of the real UI runtime: tools/ui/run_gdx.sh <projectDir> <uiName> <out.png> [test]
# Needs: xvfb-run, mesa software GL, and ./gradlew :desktop:compileJava done once (classes of the runtime).
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
CP=$("$HERE/tools/ui/classpath.sh")
OUT=/tmp/uigdx; mkdir -p $OUT
javac -d $OUT -cp "$CP:$HERE/desktop/build/classes/java/main" "$HERE/tools/ui/GdxUiTest.java" 2>&1 | grep -v Picked
LIBGL_ALWAYS_SOFTWARE=1 timeout 120 xvfb-run -a -s "-screen 0 1280x720x24" java -cp "$OUT:$CP:$HERE/desktop/build/classes/java/main:$HERE/app/src/main/assets" GdxUiTest "$@" 2>&1 | grep -v "^Picked\|Lwjgl3Application\] Couldn\|ALSA\|pw.conf\|ALSOFT"
