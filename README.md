# Agent Harness CLI

Terminal User Interface for [Agent Harness](https://github.com/markpollack/agent-harness) - an agentic CLI built with TUI4J.

## Overview

Agent Harness CLI provides a modern terminal interface for interacting with AI agents powered by agent-harness patterns. Built with [TUI4J](https://github.com/WilliamAGH/tui4j) (Java port of Bubble Tea), it follows the Elm Architecture for a reactive, component-based UI.

## Modules

| Module | Description |
|--------|-------------|
| `cli-core` | Core TUI components and abstractions |
| `cli-app` | Main CLI application with agent integration |

## Building

```bash
./mvnw clean install
```

## Running

```bash
java -jar cli-app/target/cli-app-0.1.0-SNAPSHOT.jar
```

## Requirements

- Java 17+
- Maven 3.8+

## Dependencies

- [TUI4J](https://github.com/WilliamAGH/tui4j) - Terminal UI framework (Elm Architecture)
- [Picocli](https://picocli.info/) - CLI argument parsing
- [Agent Harness](https://github.com/markpollack/agent-harness) - Agent loop patterns and tools
- [Spring AI](https://spring.io/projects/spring-ai) - LLM integration

## License

Apache License 2.0
