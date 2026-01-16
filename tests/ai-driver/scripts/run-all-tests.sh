#!/bin/bash
# Run all AI-driven integration tests
#
# Usage:
#   ./scripts/run-all-tests.sh           # Run all tests
#   ./scripts/run-all-tests.sh basic     # Run basic category
#   ./scripts/run-all-tests.sh --no-api  # Run only non-API tests

set -e

cd "$(dirname "$0")/.."

CATEGORY="${1:-}"

if [[ "$CATEGORY" == "--no-api" ]]; then
    echo "Running non-API tests only..."
    # Unset API key to force skip of API tests
    unset ANTHROPIC_API_KEY
    jbang RunAITest.java --all
elif [[ -n "$CATEGORY" ]]; then
    echo "Running category: $CATEGORY"
    jbang RunAITest.java --category "$CATEGORY"
else
    echo "Running all tests..."
    jbang RunAITest.java --all
fi
