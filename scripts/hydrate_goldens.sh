#!/bin/bash
# LeakLens Golden Hydration Script 💧
# Requirements: Emulator running, app installed.

PACKAGE="com.github.devvikassoni.leaklens.sample"
GOLDEN_ROOT="verification/golden"

function check_device() {
    DEVICE_COUNT=$(adb devices | grep -v "List of devices attached" | grep "device" | wc -l)
    if [ "$DEVICE_COUNT" -eq 0 ]; then
        echo "❌ ERROR: No Android device/emulator detected."
        echo "Golden hydration requires a connected emulator or physical device."
        exit 1
    fi
}

function force_gc() {
    PID=$(adb shell pidof "$PACKAGE")
    if [ -n "$PID" ]; then
        echo "  > Forcing GC for PID $PID..."
        # Note: 'kill -10' triggers a manual GC in many Android versions
        adb shell kill -10 "$PID"
        sleep 3
    fi
}

function capture_heap() {
    SCENARIO_ID=$1
    TARGET_DIR="$GOLDEN_ROOT/$SCENARIO_ID"
    mkdir -p "$TARGET_DIR"

    echo "  > Capturing Heap Dump..."
    REMOTE_PATH="/data/local/tmp/leaklens_$SCENARIO_ID.hprof"
    adb shell am dumpheap "$PACKAGE" "$REMOTE_PATH"
    sleep 8 # Wait for file to be written

    echo "  > Pulling fixture to $TARGET_DIR/input.hprof"
    adb pull "$REMOTE_PATH" "$TARGET_DIR/input.hprof"
    adb shell rm "$REMOTE_PATH"
}

function activity_leak() {
    echo "------------------------------------------------"
    echo "🚀 Scenario: activity_leak"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/com.github.devvikassoni.leaklens.sample.scenarios.activity.LeakyActivity"
    sleep 4
    echo "  > Exiting Activity to trigger leak..."
    adb shell input keyevent 4 # Back
    sleep 2
    force_gc
    capture_heap "activity_leak"
}

function fragment_leak() {
    echo "------------------------------------------------"
    echo "🚀 Scenario: fragment_leak"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/.FragmentHostActivity" --es EXTRA_SCENARIO "fragment_leak"
    sleep 4
    echo "  > Exiting Fragment host to trigger leak..."
    adb shell input keyevent 4 # Back
    sleep 2
    force_gc
    capture_heap "fragment_leak"
}

function flow_leak() {
    echo "------------------------------------------------"
    echo "🚀 Scenario: flow_leak"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/.FragmentHostActivity" --es EXTRA_SCENARIO "flow_leak"
    sleep 4
    echo "  > Exiting Flow host to trigger leak..."
    adb shell input keyevent 4 # Back
    sleep 2
    force_gc
    capture_heap "flow_leak"
}

function compose_leak() {
    echo "------------------------------------------------"
    echo "🚀 Scenario: compose_leak"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/.ComposeHostActivity"
    sleep 4
    echo "  > Rotating device to trigger leak (recreate Activity)..."
    # Enable auto-rotate and rotate
    adb shell settings put system accelerometer_rotation 1
    adb shell content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:1
    sleep 3
    adb shell content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:0
    sleep 3
    force_gc
    capture_heap "compose_leak"
}

function singleton_leak() {
    echo "------------------------------------------------"
    echo "🚀 Scenario: singleton_leak"
    adb shell am force-stop "$PACKAGE"
    # This activity finished itself immediately after leaking context to AppManager
    adb shell am start -n "$PACKAGE/.SingletonHostActivity"
    sleep 4
    force_gc
    capture_heap "singleton_leak"
}

function workmanager_leak() {
    echo "------------------------------------------------"
    echo "🚀 Scenario: workmanager_leak"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/.WorkerHostActivity"
    sleep 6 # Wait for worker to start
    force_gc
    capture_heap "workmanager_leak"
}

function bitmap_leak() {
    echo "------------------------------------------------"
    echo "🚀 Scenario: bitmap_leak"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/.BitmapHostActivity"
    sleep 4
    force_gc
    capture_heap "bitmap_leak"
}

# Main execution
check_device

if [ -n "$1" ]; then
    # Run specific scenario
    $1
else
    # Run all implemented scenarios
    activity_leak
    fragment_leak
    singleton_leak
    compose_leak
    flow_leak
    workmanager_leak
    bitmap_leak
fi

echo "------------------------------------------------"
echo "✅ HYDRATION COMPLETE"
echo "Run './gradlew :verification:verify' to test the engine."
