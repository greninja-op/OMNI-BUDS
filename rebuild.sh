#!/usr/bin/env bash
set -euo pipefail

echo "======================================================================"
echo "                     OmniBuds Environment Rebuild"
echo "======================================================================"

# 1. Java check
if [ -z "${JAVA_HOME:-}" ]; then
    if command -v java >/dev/null 2>&1; then
        echo "[INFO] Using Java from PATH: $(command -v java)"
    else
        echo "[ERROR] JAVA_HOME is not set and java was not found on PATH."
        echo "Please install JDK 17 and export JAVA_HOME."
        exit 1
    fi
else
    echo "[INFO] Using JAVA_HOME: $JAVA_HOME"
fi

# 2. Check local.properties / Android SDK
if [ ! -f "local.properties" ]; then
    echo "[WARN] local.properties not found. Attempting auto-detection..."
    if [ -n "${ANDROID_HOME:-}" ]; then
        echo "sdk.dir=$ANDROID_HOME" > local.properties
        echo "[INFO] Created local.properties from ANDROID_HOME."
    elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
        echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
        echo "[INFO] Created local.properties from ANDROID_SDK_ROOT."
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
        echo "[INFO] Created local.properties pointing to macOS Android SDK."
    elif [ -d "$HOME/Android/Sdk" ]; then
        echo "sdk.dir=$HOME/Android/Sdk" > local.properties
        echo "[INFO] Created local.properties pointing to Linux Android SDK."
    else
        echo "[ERROR] Android SDK not detected. Copy local.properties.example to local.properties and set sdk.dir."
        exit 1
    fi
fi

# 3. Build modules
echo ""
echo "[1/3] Building all Gradle modules (:core, :platform:android, :tools:companion-shell)..."
./gradlew build

# 4. Run tests
echo ""
echo "[2/3] Running module test suites..."
./gradlew test

# 5. Device-bridge verification suite
echo ""
echo "[3/3] Running Python device-bridge verification suite..."
if command -v python3 >/dev/null 2>&1; then
    python3 tools/device-bridge/verify_suite.py
elif command -v python >/dev/null 2>&1; then
    python tools/device-bridge/verify_suite.py
else
    echo "[WARN] Python not found on PATH. Skipping device-bridge self-check."
fi

echo ""
echo "======================================================================"
echo "              OmniBuds Rebuild Completed Successfully!"
echo "======================================================================"
