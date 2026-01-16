#!/bin/bash
# Test: Quit command in linear mode (no expect required)
# Tests that 'q' exits the CLI

set -e

JAR="cli-app/target/cli-app-0.1.0-SNAPSHOT.jar"

echo "Testing 'q' quit command..."

# Send 'q' and check for Goodbye
OUTPUT=$(echo "q" | timeout 10 java -jar "$JAR" --linear 2>&1) || true

if echo "$OUTPUT" | grep -q "Goodbye"; then
    echo "PASS: q quits correctly"
else
    echo "FAIL: Expected 'Goodbye' in output"
    echo "Output was: $OUTPUT"
    exit 1
fi

echo ""
echo "Testing '/quit' command..."

OUTPUT=$(echo "/quit" | timeout 10 java -jar "$JAR" --linear 2>&1) || true

if echo "$OUTPUT" | grep -q "Goodbye"; then
    echo "PASS: /quit quits correctly"
else
    echo "FAIL: Expected 'Goodbye' in output"
    echo "Output was: $OUTPUT"
    exit 1
fi

echo ""
echo "All quit tests PASSED"
