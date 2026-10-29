#!/bin/bash
# Renders the UiKit screens headless: tools/uikit/run.sh <projectDir> <outDir> [screen|all] [theme]
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
CP=$("$HERE/tools/ui/classpath.sh")
APP=$HERE/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes
OUT=/tmp/uikit_cls; rm -rf $OUT; mkdir -p $OUT
python3 "$HERE/tools/uikit/make_icons.py" "$1" >/dev/null
javac -d $OUT -cp "$CP:$APP" "$HERE/tools/uikit/KitRenderTest.java" 2>&1 | grep -v Picked
LIBGL_ALWAYS_SOFTWARE=1 timeout 300 xvfb-run -a -s "-screen 0 1280x720x24" java -cp "$OUT:$CP:$APP:$HERE/app/src/main/assets" KitRenderTest "$@" 2>&1 | grep -v "^Picked\|Lwjgl3Application\] Couldn\|ALSA\|pw.conf\|ALSOFT"
