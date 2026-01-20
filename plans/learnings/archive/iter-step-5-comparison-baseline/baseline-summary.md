# Bootstrap Comparison Baseline Summary

**Date**: 2026-01-20
**Tests**: 11 bootstrap tests
**Result**: All 11 pass for both MiniAgent and Claude Code

## Results Table

| # | Test | MiniAgent | Claude Code | Insight | Notes |
|---|------|-----------|-------------|---------|-------|
| 01 | Echo Response | ✅ PASS | ✅ PASS | IDENTICAL | No tools used |
| 02 | Read File Content | ✅ PASS | ✅ PASS | IDENTICAL | Both use Read |
| 03 | Create File | ✅ PASS | ✅ PASS | IDENTICAL | Both use Write |
| 04 | Run Bash Command | ✅ PASS | ✅ PASS | Different | Tool case: bash vs Bash |
| 05 | Read Multiple Files | ✅ PASS | ✅ PASS | Different | Claude uses Glob |
| 06 | Edit File Content | ✅ PASS | ✅ PASS | IDENTICAL | Both use Edit |
| 07 | Fix Syntax Error | ✅ PASS | ✅ PASS | Different | Tool count differs |
| 08 | Add Null Check | ✅ PASS | ✅ PASS | IDENTICAL | Similar approach |
| 09 | Find and Fix Bug | ✅ PASS | ✅ PASS | Different | Tool differs |
| 10 | Implement Method | ✅ PASS | ✅ PASS | Different | Tool count differs |
| 11 | List Home Directory | ✅ PASS | ✅ PASS | Different | Tool case: bash vs Bash |

## Key Observations

### 1. Tool Name Case Sensitivity Issue

MiniAgent's BashTool is registered as `bash` (lowercase) while Claude Code's is `Bash` (capitalized). This creates false "tool gaps" in the Venn comparison.

**Affected tests**: 04, 11 (bash commands)

**Fix needed**: Normalize tool names to consistent case in ToolUsageComparison.

### 2. Claude Code Uses Glob More Often

For multi-file operations (test 05), Claude Code uses `Glob` to find files while MiniAgent tends to use bash commands.

**Implication**: MiniAgent could benefit from using Glob for file discovery.

### 3. Similar Approach for Core Operations

For basic file operations (Read, Write, Edit), both agents use identical approaches.

### 4. Efficiency Comparison

| Metric | MiniAgent | Claude Code |
|--------|-----------|-------------|
| Avg Turns | 1-4 | 2-4 |
| Avg Duration | 1-10s | 4-15s |
| Token tracking | Not captured | Full tracking |

MiniAgent tends to be faster but Claude Code has more detailed token tracking.

## Tool Gap Analysis

**True tool gaps** (after normalizing case):
- `Glob`: Claude uses for file discovery, MiniAgent uses bash

**False positives** (case sensitivity):
- `bash` vs `Bash` - same tool, different naming

## Issues Found

1. **Tool name normalization**: Need to normalize to consistent case
2. **Token tracking**: MiniAgent doesn't report tokens in comparison (shows 0)
3. **Loss calculation**: Not yet implemented in comparison reports

## Next Steps

1. Fix tool name case sensitivity in ToolUsageComparison
2. Add token tracking to MiniAgent execution
3. Implement loss calculation (judge scores) in comparisons
4. Create JUnit-based comparison tests for proper test reports
