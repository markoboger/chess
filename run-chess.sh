#!/bin/bash
# Chess Dual UI Launcher with macOS activation fix

cd "$(dirname "$0")"

echo "Starting Chess with Dual UI (GUI + Console)..."
echo "================================================"
echo ""

# Start chess in background
sbt run &
SBT_PID=$!

# Wait for JavaFX to initialize
sleep 8

# Find and activate the Java process
echo "Attempting to bring GUI window to front..."
osascript -e 'tell application "System Events" to set frontmost of first process whose name contains "java" to true' 2>/dev/null

echo ""
echo "✓ Chess should now be visible!"
echo "✓ If not, check your Dock or press Cmd+Tab"
echo ""

# Keep script alive to maintain process
wait $SBT_PID
