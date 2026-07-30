#!/bin/bash
# LeakLens Golden Hydration Script 💧
# Requirements: Emulator running, app installed.

PACKAGE="com.github.devvikassoni.leaklens.sample"
GOLDEN_ROOT="verification/golden"

function capture_scenario() {
    SCENARIO_ID=$1
    ACTIVITY_NAME=$2
    TARGET_DIR="$GOLDEN_ROOT/$SCENARIO_ID"
    mkdir -p "$TARGET_DIR"

    echo "------------------------------------------------"
    echo "🚀 Scenario: $SCENARIO_ID"

    # 1. Force stop and clear
    adb shell am force-stop "$PACKAGE"

    # 2. Start Activity
    echo "  > Starting Activity..."
    adb shell am start -n "$PACKAGE/$ACTIVITY_NAME" > /dev/null
    if [ $? -ne 0 ]; then
        echo "  ❌ ERROR: Could not start app. Is it installed?"
        return 1
    fi
    sleep 4

    # 3. Trigger Leak (Back Button)
    echo "  > Triggering Leak (Simulating user exit)..."
    adb shell input keyevent 4
    sleep 2

    # 4. Capture Heap Dump
    echo "  > Capturing Heap Dump (this take a few seconds)..."
    REMOTE_PATH="/data/local/tmp/leaklens_temp.hprof"
    adb shell am dumpheap "$PACKAGE" "$REMOTE_PATH"
    sleep 6 # Wait for file to be written

    # 5. Pull and Clean
    echo "  > Pulling fixture to $TARGET_DIR/input.hprof"
    adb pull "$REMOTE_PATH" "$TARGET_DIR/input.hprof"
    adb shell rm "$REMOTE_PATH"
    echo "  ✅ Done"
}

# Ensure device is connected
adb wait-for-device

# Hydrate implemented scenarios
capture_scenario "activity_leak" ".MainActivity"
capture_scenario "fragment_leak" ".MainActivity"
capture_scenario "singleton_leak" ".MainActivity"
capture_scenario "compose_leak" ".MainActivity"
capture_scenario "flow_leak" ".MainActivity"

echo "------------------------------------------------"
echo "✅ HYDRATION COMPLETE"
echo "Run './gradlew :verification:verify' to test the engine."
