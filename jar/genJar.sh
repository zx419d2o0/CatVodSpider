#!/bin/sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

rm -f "$SCRIPT_DIR/custom_spider.jar"
rm -rf "$SCRIPT_DIR/Smali_classes"

java -jar "$SCRIPT_DIR/3rd/apktool_2.11.0.jar" d -f --only-main-classes \
    "$SCRIPT_DIR/../app/build/outputs/apk/release/app-release-unsigned.apk" \
    -o "$SCRIPT_DIR/Smali_classes"

rm -rf "$SCRIPT_DIR/spider.jar/smali/com/github/catvod/spider"
rm -rf "$SCRIPT_DIR/spider.jar/smali/com/github/catvod/js"
rm -rf "$SCRIPT_DIR/spider.jar/smali/org/slf4j"

mkdir -p "$SCRIPT_DIR/spider.jar/smali/com/github/catvod"
mkdir -p "$SCRIPT_DIR/spider.jar/smali/org/slf4j"

mv "$SCRIPT_DIR/Smali_classes/smali/com/github/catvod/spider" \
   "$SCRIPT_DIR/spider.jar/smali/com/github/catvod/"

mv "$SCRIPT_DIR/Smali_classes/smali/com/github/catvod/js" \
   "$SCRIPT_DIR/spider.jar/smali/com/github/catvod/"

mv "$SCRIPT_DIR/Smali_classes/smali/org/slf4j" \
   "$SCRIPT_DIR/spider.jar/smali/org/"

java -jar "$SCRIPT_DIR/3rd/apktool_2.11.0.jar" b \
    "$SCRIPT_DIR/spider.jar" -c

mv "$SCRIPT_DIR/spider.jar/dist/dex.jar" \
   "$SCRIPT_DIR/custom_spider.jar"

md5sum "$SCRIPT_DIR/custom_spider.jar" | awk '{print $1}' \
    > "$SCRIPT_DIR/custom_spider.jar.md5"

rm -rf "$SCRIPT_DIR/spider.jar/build"
rm -rf "$SCRIPT_DIR/spider.jar/smali"
rm -rf "$SCRIPT_DIR/spider.jar/dist"
rm -rf "$SCRIPT_DIR/Smali_classes"