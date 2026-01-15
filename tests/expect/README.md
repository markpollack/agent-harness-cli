# Expect Integration Tests

This directory contains `expect` scripts for automated integration testing.

## Prerequisites

- `expect` installed (`sudo apt install expect` on Ubuntu/Debian)
- `--linear` mode in agent-harness-cli (Phase 4)

## Test Scripts

| Script | Description | Requires |
|--------|-------------|----------|
| `basic-interaction.exp` | Basic prompt/response flow | `--linear` mode |
| `quit-command.exp` | Test 'q' to quit | `--linear` mode |
| `tool-call-visibility.exp` | Verify tool calls displayed | Agent integration |

## Running Tests

```bash
# Run a single test
expect tests/expect/basic-interaction.exp

# Run all tests
for f in tests/expect/*.exp; do expect "$f"; done
```

## Writing Tests

```tcl
#!/usr/bin/expect -f
set timeout 30

spawn ./agent-harness-cli --linear

expect ">"
send "Hello\n"

expect -re ".*response.*"

expect ">"
send "q\n"

expect eof
```

## Note

Expect tests require `--linear` mode which is implemented in Phase 4.
For Phase 2-3, manual testing is used.
