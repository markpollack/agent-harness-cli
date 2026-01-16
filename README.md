# Agent Harness CLI

```
      🖥️
       \
        💥════► ✨
       /
      👤

    agent-harness-cli v0.1.0
```

Terminal User Interface for [Agent Harness](https://github.com/markpollack/agent-harness) - an agentic CLI built with TUI4J.

## Overview

Agent Harness CLI provides a modern terminal interface for interacting with AI agents powered by Spring AI. Built with [TUI4J](https://github.com/WilliamAGH/tui4j) (Java port of Bubble Tea), it follows the Elm Architecture for a reactive, component-based UI.

## Requirements

- Java 21+
- Maven 3.8+
- `ANTHROPIC_API_KEY` environment variable

## Prerequisites

This project depends on SNAPSHOT artifacts that must be built locally first:

```bash
# 1. Clone and build spring-ai-agent-utils
git clone https://github.com/springaicommunity/spring-ai-agent-utils.git
cd spring-ai-agent-utils && ./mvnw install -DskipTests && cd ..

# 2. Clone and build spring-ai-sandbox
git clone https://github.com/springaicommunity/spring-ai-sandbox.git
cd spring-ai-sandbox && ./mvnw install -DskipTests && cd ..

# 3. Clone and build agent-harness
git clone https://github.com/markpollack/agent-harness.git
cd agent-harness && ./mvnw install -DskipTests && cd ..
```

## Quick Start

```bash
# Set your API key
export ANTHROPIC_API_KEY="sk-ant-..."

# Build
./mvnw clean package -DskipTests

# Run (TUI mode)
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar
```

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `ANTHROPIC_API_KEY` | Yes | Your Anthropic API key for Claude |
| `BRAVE_API_KEY` | No | Brave Search API key for web search (optional) |

### Getting API Keys

**Anthropic API Key:**
1. Visit [console.anthropic.com](https://console.anthropic.com/)
2. Create an account and generate an API key
3. Export: `export ANTHROPIC_API_KEY="sk-ant-..."`

**Brave Search API Key (Optional):**
1. Visit [brave.com/search/api](https://brave.com/search/api/)
2. Sign up (Free tier: 2,000 queries/month)
3. Export: `export BRAVE_API_KEY="your-key"`

## Building

```bash
# Full build with tests
./mvnw clean install

# Quick build (skip tests)
./mvnw clean package -DskipTests

# Build specific module
./mvnw compile -pl cli-app
```

## Running

Agent Harness CLI supports three execution modes:

### TUI Mode (Default)

Interactive terminal UI with real-time updates:

```bash
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar
```

Example session:

```
      🖥️
       \
        💥════► ✨
       /
      👤

agent-harness-cli v0.1.0

Agent Harness CLI
=================

(No messages yet)

> what files are in ~/

You: what files are in ~/
Assistant: The home directory contains 36 items:
- Documents/
- Downloads/
- projects/
...

> _
```

Controls:
- **Enter** - Send message
- **q** (when input empty) or **Ctrl+C** - Quit

### Linear Mode

Plain text I/O, suitable for scripting and expect-based testing:

```bash
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar --linear
```

Commands:
- `/quit` or `q` - Exit
- `/clear` - Clear conversation history

### Print Mode

Single-task execution for CI/CD pipelines:

```bash
# With argument
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar -p "List files in current directory"

# With piped input
echo "What is 2+2?" | java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar -p
```

## CLI Options

| Option | Description | Default |
|--------|-------------|---------|
| `-d, --directory` | Working directory | Current directory |
| `-m, --model` | Model name | `claude-sonnet-4-20250514` |
| `-t, --max-turns` | Maximum turns per request | 20 |
| `-p, --print` | Non-interactive mode | false |
| `--linear` | Interactive plain I/O mode | false |
| `-h, --help` | Show help | |
| `-V, --version` | Show version | |

### Examples

```bash
# Use a different model
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar -m claude-sonnet-4-20250514

# Set working directory
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar -d /path/to/project

# Limit agent turns
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar -t 10
```

## Modules

| Module | Description |
|--------|-------------|
| `cli-core` | Core TUI components and abstractions |
| `cli-app` | Main CLI application with agent integration |
| `harness-test` | Test framework for AI agent testing |

## Agent Capabilities

The agent has access to the following tools:

| Tool | Description |
|------|-------------|
| `Read` | Read file contents |
| `Write` | Create or overwrite files |
| `Edit` | Make targeted edits to existing files |
| `LS` | List directory contents |
| `Bash` | Execute shell commands |
| `Glob` | Find files by pattern |
| `Grep` | Search file contents |
| `Submit` | Submit final answer (ends conversation) |

## Development

### Running Tests

```bash
# Unit tests
./mvnw test

# Integration tests (requires ANTHROPIC_API_KEY)
./mvnw verify

# Single test
./mvnw test -pl cli-app -Dtest=AgentCliTest
```

### Project Structure

```
agent-harness-cli/
├── cli-core/           # TUI components (ChatModel, ChatEntry)
├── cli-app/            # Main application (AgentCli)
├── harness-test/       # Test framework
└── tests/              # Test resources
    ├── ai-driver/      # AI test driver (JBang)
    └── expect/         # Expect-based tests
```

## Dependencies

- [TUI4J](https://github.com/WilliamAGH/tui4j) - Terminal UI framework
- [Picocli](https://picocli.info/) - CLI argument parsing
- [Agent Harness](https://github.com/markpollack/agent-harness) - Agent loop patterns
- [Spring AI](https://spring.io/projects/spring-ai) - LLM integration
- [spring-ai-agent-utils](https://github.com/springaicommunity/spring-ai-agent-utils) - Agent tools

## Troubleshooting

### "ANTHROPIC_API_KEY environment variable not set"

```bash
export ANTHROPIC_API_KEY="sk-ant-api03-..."
```

### "Unable to access jarfile"

Build the project first:
```bash
./mvnw clean package -DskipTests
```

### Agent not responding / timeout

Increase the max turns:
```bash
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar -t 50
```

## License

Apache License 2.0
