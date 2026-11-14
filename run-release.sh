#!/usr/bin/env bash
set -e

# Use Java 21 JDK
if [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
fi

echo "=========================================================="
echo " Building Mustre in Release Mode (R8 Minified & Optimized) "
echo "=========================================================="

./gradlew assembleRelease

echo ""
echo "=========================================================="
echo " Installing and Running Release APK on Connected Device   "
echo "=========================================================="

./gradlew installRelease

if command -v adb >/dev/null 2>&1; then
    echo "Starting Mustre MainActivity..."
    adb shell am start -n com.aadith.mustre/.MainActivity
fi

echo "Done! Mustre is running in Release Mode."
