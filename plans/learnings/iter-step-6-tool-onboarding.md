# Step 6 Learnings: Tool Onboarding from spring-ai-agent-utils

**Date**: 2026-01-20
**Status**: Complete

## Overview

Onboarded TodoWriteTool and TaskTool from spring-ai-agent-utils to address tool gaps identified in bootstrap comparisons.

## Tools Onboarded

### TodoWriteTool

**Source**: `org.springaicommunity.agent.tools.TodoWriteTool`

**Purpose**: Track progress on multi-step tasks with structured todo items.

**Integration**:
```java
toolObjects.add(TodoWriteTool.builder().build());
```

**Key Features**:
- Structured task tracking
- Status management (pending, in_progress, completed)
- Visible progress for users

### TaskTool

**Source**: `org.springaicommunity.agent.tools.task.TaskTool`

**Purpose**: Delegate to specialized sub-agents for complex exploration tasks.

**Integration**:
```java
var taskRepository = new DefaultTaskRepository();
var subagentExecutor = new ClaudeSubagentExecutor(
        Map.of("default", ChatClient.builder(builder.model)),
        List.of() // Sub-agents have own tools via config
);
toolObjects.add(TaskTool.builder()
        .taskRepository(taskRepository)
        .subagentExecutors(subagentExecutor)
        .build());
```

**Key Features**:
- Sub-agent delegation (e.g., Explore subagent)
- Task repository for tracking sub-agent work
- Flexible executor architecture

## System Prompt Updates

Added CRITICAL tool selection rules to `MiniAgentConfig.java`:

```
CRITICAL Tool Selection Rules:
- NEVER use bash find/ls for file discovery - use Glob instead
- NEVER use bash grep/rg for content search - use Grep instead
- NEVER use bash cat/head/tail for reading - use Read instead
- NEVER use bash echo/sed for writing - use Write/Edit instead
- Use TodoWrite for tasks with 3+ steps to track progress
- Use Task with subagent_type=Explore for codebase exploration
```

## BashTool Description Update

Updated `harness-tools/BashTool.java` description:

```java
@Tool(description = """
        Execute a bash command for terminal operations like git, npm, docker, make.
        DO NOT use for file operations - use specialized tools instead.
        Avoid: find, ls (use Glob), grep, rg (use Grep), cat, head, tail (use Read), sed, awk (use Edit).
        """)
```

## Key Findings

### Model Behavior vs Tool Availability

**Observation**: Despite having TodoWriteTool registered and explicit system prompt guidance, MiniAgent chose efficiency over patterns for simple bootstrap tasks.

**Evidence**:
- Test 09: MiniAgent used 4 tool calls (10s) vs Claude Code's 10 calls (40s)
- MiniAgent passed but didn't use TodoWrite for simple tasks
- Model chose bash find when Glob would technically be preferred

**Interpretation**: This is likely **appropriate behavior** for simple tasks:
- Model optimizes for efficiency
- Planning overhead not needed for trivial tasks
- Complex tasks (intermediate suite) will reveal if patterns matter

### Tool Description vs System Prompt

**Confirmed Learning**: Tool descriptions affect tool USAGE, not SELECTION.

- Tool descriptions guide HOW a tool is used once selected
- System prompt guides WHEN/WHETHER to use a tool
- "Prefer X over Y" guidance belongs in system prompt, not tool description

## Comparison Results After Onboarding

| Test | Similarity | MiniAgent Behavior |
|------|------------|-------------------|
| 05-find-file | 66.7% | Uses bash find (efficient) |
| 09-multi-file | 42.9% | Passes with fewer tool calls |
| Others | ~50-70% | Generally passes, different patterns |

## Next Steps

1. **Step 7**: Iterate on remaining bootstrap tests to achieve loss < 0.2
2. **Step 8**: Intermediate tests will validate if tool patterns matter for complex tasks
3. **Monitor**: Track whether MiniAgent uses TodoWrite/TaskTool on complex tasks

## Files Modified

| File | Change |
|------|--------|
| `harness-agents/.../MiniAgent.java` | Added TodoWriteTool, TaskTool registration |
| `harness-agents/.../MiniAgentConfig.java` | CRITICAL tool selection rules |
| `harness-tools/.../BashTool.java` | Updated description with avoid list |

## References

- Claude Code system prompt: `/home/mark/tuvium/claude-code-analysis/system-prompt-reference.txt`
- Explore subagent definition: `spring-ai-agent-utils/src/main/resources/agent/EXPLORE_SUBAGENT.md`
