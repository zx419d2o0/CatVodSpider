#!/bin/sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

sh "$SCRIPT_DIR/gradlew" assembleRelease --no-daemon
if [ $? -ne 0 ]; then
  echo "❌ assembleRelease 失败，构建终止"
  exit 1
fi

sh "$SCRIPT_DIR/jar/genJar.sh" "$1"

exit