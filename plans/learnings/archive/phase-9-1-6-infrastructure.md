# Phase 9.1.6: Claude Code Comparison Testing Infrastructure

> **Created**: 2026-01-16
> **Updated**: 2026-01-16
> **Status**: ✅ COMPLETE - Tool Selection Fixed, Both Agents Now Aligned

## Overview

This phase adds the ability to run the same test case against both MiniAgent and Claude Code CLI, generating comparison reports to distinguish between test design bugs and agent capability gaps.

## New Components

### Tracking Package (`harness-test/.../tracking/`)

| File | Purpose |
|------|---------|
| `ToolCallEvent.java` | Records a single tool call (name, input, output, success) |
| `ExecutionSummary.java` | Aggregates execution metrics (tool calls, tokens, turns) |

### Comparison Package (`harness-test/.../comparison/`)

| File | Purpose |
|------|---------|
| `ComparisonRunner.java` | Orchestrates running both agents on same test |
| `ComparisonReport.java` | Analyzes and formats comparison results |

### Updated Executor

| File | Purpose |
|------|---------|
| `ClaudeCodeExecutor.java` | Runs prompts via Claude Code SDK with hook-based tool tracking |

## Usage

### Running a Comparison

```bash
cd tests/ai-driver

# Compare on simplest test first
jbang RunAITest.java --compare use-cases/bootstrap/01-echo-response.yaml

# Compare on the API Design test (the one that "failed")
jbang RunAITest.java --compare use-cases/intermediate/21-api-design.yaml
```

### Output

1. **Console output**: Formatted comparison showing both executions side-by-side
2. **Markdown report**: Saved to `plans/learnings/comparison-<test-name>.md`

### Example Output

```
═══════════════════════════════════════════════════════════════
COMPARISON REPORT: Echo Response
═══════════════════════════════════════════════════════════════

RESULT: ✅ BOTH PASSED

INSIGHT: IDENTICAL: Both agents succeeded with similar approaches

─── MiniAgent ───────────────────────────────────────────────
[MiniAgent] SUCCESS in 1500ms, 1 turns
  Tokens: input=0, output=0, thinking=0 (total=0)
  Tool calls: 0

─── Claude Code ─────────────────────────────────────────────
[ClaudeCode] SUCCESS in 2000ms, 1 turns
  Tokens: input=150, output=50, thinking=10 (total=210)
  Tool calls: 0

─── Tool Sequences ──────────────────────────────────────────
MiniAgent:   (no tools)
ClaudeCode:  (no tools)
═══════════════════════════════════════════════════════════════
```

## Test Progression Strategy

Run comparisons from simplest to most complex to build learnings incrementally:

### Level 1: No-Tool Tests
1. `01-echo-response.yaml` - Pure text response
2. `02-file-read.yaml` - Single Read tool

### Level 2: Single-Tool Tests
3. `03-file-create.yaml` - Single Write tool
4. `04-bash-command.yaml` - Single Bash tool

### Level 3: Multi-Tool Tests
5. `05-multi-file-read.yaml` - Multiple Reads
6. Tests in intermediate/

### Level 4: Complex Reasoning
7. `20-multi-bug-fix.yaml` - Multi-step debugging
8. `21-api-design.yaml` - The test that "failed" (likely test design issue)
9. `22-test-driven-fix.yaml` - Test-driven development

## Telemetry Captured

### From Claude Code (via SDK hooks)

| Metric | Source | Status |
|--------|--------|--------|
| Tool name | `PreToolUseInput.toolName()` | ✅ |
| Tool input | `PreToolUseInput.toolInput()` | ✅ |
| Tool output | `PostToolUseInput.toolResponse()` | ✅ |
| Input tokens | `ResultMessage.usage()` | ✅ |
| Output tokens | `ResultMessage.usage()` | ✅ |
| Thinking tokens | `ResultMessage.usage()` | ✅ |
| Turn count | `ResultMessage.numTurns()` | ✅ |
| Duration | `ResultMessage.durationMs()` | ✅ |

### From MiniAgent (via OutputParser)

| Metric | Status | Notes |
|--------|--------|-------|
| Tool name | ✅ | Parsed from LoggingToolCallListener output |
| Tool input | ✅ | Parsed from LoggingToolCallListener output |
| Tool output | ✅ | Parsed from LoggingToolCallListener output |
| Input tokens | ⚠️ TODO | Not captured (would require CLI enhancement) |
| Thinking tokens | ❌ | MiniAgent doesn't use extended thinking |
| Turn count | ✅ | Heuristic from output parsing |
| Duration | ✅ | Measured externally |

**Implementation Note**: Tool calls are captured by parsing `LoggingToolCallListener` output from stderr.
The `DefaultCliExecutor` was updated with `redirectErrorStream(true)` to capture stderr.
The `OutputParser` class extracts tool calls from the log format: `[toolName] {args}({})` and `[toolName] completed in Xms`.

## Key Insights to Gather

1. **Test Design Issues**: When both agents fail or behave differently than expected
   - Test 21 creates proper Java package structure but test expected flat files
   - Fix: Use glob patterns (`**/Todo.java`) instead of exact paths

2. **Capability Gaps**: When Claude Code succeeds but MiniAgent fails
   - Document which tools/patterns Claude Code uses
   - Identify what MiniAgent needs to learn

3. **Tool Sequence Patterns**: How Claude Code approaches tasks
   - Order of tool calls
   - Information gathering vs execution phases

## Root Cause Analysis & Fix (2026-01-16)

### Problem

MiniAgent was using `bash` for ALL operations (file read, file create, directory listing) instead of dedicated tools like `Read`, `Write`, `LS`.

### Root Cause

MiniAgent was using `MiniAgentTools.java` which only had simple `bash` and `submit` tools with minimal descriptions. The tool descriptions didn't guide the LLM to use specialized tools for file operations.

### Solution

Updated `MiniAgent.java` to use tools from `spring-ai-agent-utils`:
- `FileSystemTools` → `Read`, `Write`, `Edit`, `LS` tools
- `ShellTools` → `Bash` tool (with description saying "DO NOT use it for file operations")
- `GlobTool` → Pattern-based file search
- `GrepTool` → Content search
- `SubmitTool` (custom inner class) → Task completion

### Files Changed

- `/home/mark/projects/agent-harness/harness-agents/src/main/java/org/springaicommunity/agents/harness/agents/mini/MiniAgent.java`
  - Replaced `MiniAgentTools` with `FileSystemTools`, `ShellTools`, `GlobTool`, `GrepTool`
  - Added inner `SubmitTool` class for task completion

## Comparison Findings BEFORE Fix

### Bootstrap Tests (Before)

| Test | MiniAgent | Claude Code | Key Difference |
|------|-----------|-------------|----------------|
| 02-file-read | ✅ bash (ls, cat) → submit | ✅ Read | MiniAgent uses bash; Claude uses dedicated Read tool |
| 03-file-create | ✅ bash (echo >, cat) → submit | ✅ Write | MiniAgent uses bash; Claude uses dedicated Write tool |
| 04-bash-command | ✅ bash → submit | ✅ Bash | Both use bash, naming differs (lowercase vs capitalized) |

### Intermediate Tests (Before)

| Test | MiniAgent | Claude Code | Key Insight |
|------|-----------|-------------|-------------|
| 21-api-design | ✅ 21 bash calls | ✅ 14 calls (Write, Bash, TodoWrite) | MiniAgent used bash for everything |

## Comparison Findings AFTER Fix

### Bootstrap Tests (After)

| Test | MiniAgent | Claude Code | Tool Match? |
|------|-----------|-------------|-------------|
| 02-file-read | ✅ Read | ✅ Read | ✅ IDENTICAL |
| 03-file-create | ✅ Write | ✅ Write | ✅ IDENTICAL |
| 04-bash-command | ✅ Bash → Submit | ✅ Bash | ✅ IDENTICAL |

### Intermediate Tests (After)

| Test | MiniAgent | Claude Code | Key Insight |
|------|-----------|-------------|-------------|
| 21-api-design | ✅ 8 calls (LS, Bash, Write) | ✅ 13 calls (Write, Bash, TodoWrite) | Both now use proper tools; Claude uses TodoWrite for planning |

### Before vs After Comparison

| Metric | Before Fix | After Fix | Improvement |
|--------|-----------|-----------|-------------|
| File read tool | `bash` (cat) | `Read` | ✅ Matches Claude Code |
| File write tool | `bash` (echo) | `Write` | ✅ Matches Claude Code |
| Directory listing | `bash` (ls) | `LS` | ✅ Proper tool |
| API design tool calls | 21 | 8 | 62% reduction |

### Key Insights

1. **Tool Selection Now Aligned**:
   - Both MiniAgent and Claude Code now use dedicated `Read`, `Write`, `Bash` tools
   - Tool descriptions from `spring-ai-agent-utils` guide the LLM correctly

2. **Remaining Differences**:
   - Claude Code uses `TodoWrite` for planning (MiniAgent doesn't have this)
   - Turn counts differ (Spring AI architecture vs Claude Code's native loop)

3. **Token Usage**:
   - Claude Code reports token usage; MiniAgent does not
   - This is a telemetry gap for cost tracking

## Next Steps

1. ~~Run comparisons progressively from simplest to most complex~~ ✅
2. ~~Document findings in individual comparison reports~~ ✅
3. ~~Aggregate learnings into test design improvements~~ ✅
4. ~~Fill telemetry gaps in MiniAgent tracking~~ ✅ (tool calls tracked via OutputParser)
5. ~~Fix MiniAgent tool selection to use spring-ai-agent-utils~~ ✅

### Completed
- All comparison tests passing
- MiniAgent now uses proper tools (Read, Write, LS, Bash)
- Tool call tracking working for both agents
- Learnings documented

### Future Improvements
- Add token tracking to MiniAgent (requires CLI enhancement)
- Add TodoWrite tool to MiniAgent for planning
- Run full test suite comparison

## Files Modified

- `harness-test/pom.xml` - Added claude-code-sdk dependency
- `harness-test/.../executor/DefaultCliExecutor.java` - Added `redirectErrorStream(true)` to capture stderr
- `harness-test/.../comparison/ComparisonRunner.java` - Added OutputParser integration and debug logging
- `tests/ai-driver/RunAITest.java` - Added --compare flag

## Files Created

- `harness-test/.../tracking/ToolCallEvent.java`
- `harness-test/.../tracking/ExecutionSummary.java`
- `harness-test/.../tracking/OutputParser.java` - Parses LoggingToolCallListener output for tool calls
- `harness-test/.../tracking/OutputParserTest.java` - Unit tests for OutputParser
- `harness-test/.../executor/ClaudeCodeExecutor.java`
- `harness-test/.../comparison/ComparisonRunner.java`
- `harness-test/.../comparison/ComparisonReport.java`
