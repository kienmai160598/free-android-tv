#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"

find_java_home() {
  /usr/libexec/java_home -v 17 2>/dev/null && return
  [ -d /opt/homebrew/opt/openjdk@17 ] && echo /opt/homebrew/opt/openjdk@17 && return
  java -XshowSettings:properties -version 2>&1 | awk '/java.home/ {print $3}'
}

find_android_home() {
  local homebrew=/opt/homebrew/share/android-commandlinetools
  for candidate in ~/Library/Android/sdk ~/Android/Sdk "$homebrew"; do
    [ -d "$candidate" ] && echo "$candidate" && return
  done
}

export JAVA_HOME="${JAVA_HOME:-$(find_java_home)}"
export ANDROID_HOME="${ANDROID_HOME:-$(find_android_home)}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleRelease -q --console=plain
exec ../../scripts/tvctl install "$@" app/build/outputs/apk/release/km-launcher.apk
