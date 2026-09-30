#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
    export ANDROID_HOME
fi
exec ./gradlew testDebugUnitTest lintDebug assembleDebug
