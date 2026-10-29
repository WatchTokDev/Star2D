#!/bin/bash
# Real offscreen render test of the UI Maker preview under Xvfb: tools/uimaker/run_gl.sh <outDir>
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
CP=$("$HERE/tools/ui/classpath.sh")
APP=$HERE/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes
OUT=/tmp/uimaker_gl; rm -rf $OUT; mkdir -p $OUT
mkdir -p /tmp/uimaker_proj
javac -d $OUT -cp "$CP:$APP" "$HERE/tools/uimaker/GlPreviewTest.java" 2>&1 | grep -v Picked
LIBGL_ALWAYS_SOFTWARE=1 timeout 180 xvfb-run -a -s "-screen 0 1280x720x24" java -cp "$OUT:$CP:$APP:$HERE/app/src/main/assets" GlPreviewTest "$1" 2>&1 | grep -v "^Picked\|Lwjgl3Application\] Couldn\|ALSA\|pw.conf\|ALSOFT"
