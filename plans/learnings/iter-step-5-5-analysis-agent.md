# Step 5.5: Analysis Agent & Self-Correction Loop

**Date**: January 20, 2026
**Status**: Complete
**Prereq**: Step 5 (Bootstrap Comparison Baseline)

## Summary

This documents a critical manual step where we:
1. Diagnosed why MiniAgent used `bash ls` instead of `Glob` for file discovery
2. Found root cause by comparing Claude Code tool descriptions
3. Applied a system prompt fix that improved similarity from 25% to 66.7%
4. Implemented ComparisonAnalysisAgent to automate this analysis
5. Created SelfCorrectionLoop for autonomous improvement cycles

## Context

After running bootstrap comparisons (Step 4), we observed:
- Test 05 (multi-file-read): 25% similarity
- MiniAgent used `bash ls` instead of `Glob` for file discovery
- MiniAgent HAS GlobTool registered (verified in MiniAgent.java:89-91)

## Investigation

### Tool Description Comparison

**Claude Code's Glob description** (from `/home/mark/tuvium/claude-code-analysis/cli.readable.js:218616`):
```
- Fast file pattern matching tool that works with any codebase size
- Supports glob patterns like "**/*.js" or "src/**/*.ts"
- Returns matching file paths sorted by modification time
- Use this tool when you need to find files by name patterns
```

**MiniAgent's GlobTool description** (from spring-ai-agent-utils):
```
- Fast file pattern matching tool that works with any codebase size
- Supports glob patterns like "**/*.js" or "src/**/*.ts"
- Returns matching file paths sorted by modification time
- Use this tool when you need to find files by name patterns
```

**Finding**: Tool descriptions are IDENTICAL!

### Key Discovery: Claude Code's BashTool Guidance

**Claude Code's BashTool** contains explicit tool selection guidance:
```
IMPORTANT: This tool is for terminal operations like git, npm, docker, etc.
DO NOT use it for file operations...

Avoid using Bash with the `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`,
or `echo` commands, unless explicitly instructed or when these commands are
truly necessary for the task. Instead, always prefer using the dedicated tools:
  - File search: Use Glob (NOT find or ls)
  - Content search: Use Grep (NOT grep or rg)
  - Read files: Use Read (NOT cat/head/tail)
  - Edit files: Use Edit (NOT sed/awk)
```

**Root Cause**: MiniAgent's system prompt said:
```
- Use Read/Write/Edit for file operations, NOT bash echo/cat
```
But it did NOT say:
```
- Use Glob for file discovery, NOT bash ls/find
```

## Fix Applied

Updated `MiniAgentConfig.DEFAULT_SYSTEM_PROMPT`:

```java
Important:
- Use Read/Write/Edit for file operations, NOT bash echo/cat
- Use Glob for file discovery, NOT bash ls/find      // ADDED
- Use Grep for content search, NOT bash grep/rg      // ADDED
```

## Results

| Test | Before Fix | After Fix | Change |
|------|-----------|-----------|--------|
| Test 05 (multi-file-read) | 25% | 66.7% | +166% improvement |
| Overall Bootstrap | Varied | All 11 PASS | 100% pass rate |

## Key Insight

**Tool descriptions are read when USING tools, not when SELECTING them.**

The model reads tool descriptions after deciding to use a tool. To influence tool SELECTION, guidance must be in the system prompt.

This means:
- BashTool description changes don't affect tool selection
- System prompt is the correct place for "prefer X over Y" guidance

## Artifacts Created

### ComparisonAnalysisAgent

`harness-test/src/main/java/.../analysis/ComparisonAnalysisAgent.java`

- Uses ClaudeSyncClient from claude-agent-sdk-java
- Analyzes comparison reports
- Identifies tool gaps and root causes (PROMPT_ISSUE, TOOL_DESCRIPTION, MISSING_TOOL)
- Generates actionable recommendations

### SelfCorrectionLoop

`harness-test/src/main/java/.../analysis/SelfCorrectionLoop.java`

- Runs comparison tests
- Calculates loss (1 - average similarity)
- Identifies tests with tool gaps
- Feeds reports to ComparisonAnalysisAgent
- Tracks improvement over iterations
- Generates summary reports

### Tests

- `ComparisonAnalysisAgentIT.java` - Tests analysis of single and batch reports
- `SelfCorrectionLoopIT.java` - Tests correction cycle execution

## BashTool Description Update

Initially we added verbose guidance to BashTool. After testing, we learned that:
1. Tool descriptions don't affect tool SELECTION
2. System prompt guidance was the effective fix
3. BashTool description can remain simpler to save context

Final BashTool description:
```java
@Tool(description = "Execute a bash command for terminal operations like git, npm, docker, make. Avoid using for file read/write/search - use dedicated Read, Write, Glob, Grep tools instead.")
```

## Recommended Roadmap Update

Insert new step between Step 4 and Step 5:

### Step 4.5: Implement Analysis Agent & Self-Correction Loop

**Entry Criteria:**
- Step 4 complete (comparison reports generated)
- Tool gaps identified but root causes unclear

**Tasks:**
- Create ComparisonAnalysisAgent using claude-agent-sdk-java
- Create SelfCorrectionLoop for autonomous improvement cycles
- Apply first prompt fix based on analysis

**Exit Criteria:**
- ComparisonAnalysisAgent can analyze reports and identify root causes
- SelfCorrectionLoop can run automated improvement cycles
- At least one prompt fix validated (similarity improvement measured)

## Next Steps

1. **Add TodoWrite tool** - Most frequently identified tool gap
2. **Run intermediate tests** - Graduate from bootstrap when loss < 0.2
3. **Automate prompt fixes** - SelfCorrectionLoop can apply fixes with approval
