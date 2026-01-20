# Bootstrap Baseline Results

> **Date**: 2026-01-20
> **Model**: claude-sonnet-4-5
> **Total Tests**: 11
> **Passed**: 11 (100%)

## Summary

All 11 bootstrap tests passed on the first run with Claude Sonnet 4.5.

| Metric | Value |
|--------|-------|
| Pass Rate | 100% (11/11) |
| Total Duration | ~81 seconds |
| Total Tokens | ~48,574 |
| Total Tool Calls | 33 |
| Average Test Duration | 7.4 seconds |

## Detailed Results

| # | Test Name | Status | Tokens | Tool Calls | Turns | Duration | Loss |
|---|-----------|--------|--------|------------|-------|----------|------|
| 01 | Echo Response | PASS | 3,613 | 0 | 1 | 1,380ms | 0.0 |
| 02 | Read File Content | PASS | 3,857 | 1 | 2 | 2,616ms | 0.0 |
| 03 | Create File | PASS | 4,040 | 3 | 3 | 5,029ms | 0.0 |
| 04 | Run Bash Command | PASS | 3,726 | 1 | 2 | 2,922ms | 0.0 |
| 05 | Read Multiple Files | PASS | 4,318 | 4 | 3 | 5,032ms | 0.0 |
| 06 | Edit File Content | PASS | 4,069 | 3 | 3 | 5,201ms | 0.0 |
| 07 | Fix Syntax Error | PASS | 4,443 | 5 | 5 | 9,615ms | 0.0 |
| 08 | Add Null Check | PASS | 4,503 | 5 | 5 | 9,494ms | 0.0 |
| 09 | Find and Fix Bug | PASS | ~5,096 | 5 | 6 | 14,073ms | 0.0 |
| 10 | Implement Method | PASS | 5,558 | 5 | 5 | 14,852ms | 0.0 |
| 11 | List Home Directory | PASS | 6,447 | 1 | 2 | 10,599ms | 0.0 |

## Tool Usage by Test

### Level 0-2 (No Tools / Read-Only)

| Test | Tools Used |
|------|------------|
| 01 Echo Response | (none) → Submit |
| 02 Read File Content | Read → Submit |
| 04 Run Bash Command | Bash → Submit |

### Level 3 (Write Operations)

| Test | Tools Used |
|------|------------|
| 03 Create File | Write → Read → Submit |
| 06 Edit File Content | Read → Edit → Submit |

### Level 4-5 (Multi-File / Complex)

| Test | Tools Used |
|------|------------|
| 05 Read Multiple Files | Read (x3) → Submit |
| 07 Fix Syntax Error | Read → Edit → Bash → Read → Submit |
| 08 Add Null Check | Read → Edit → Bash → Read → Submit |
| 09 Find and Fix Bug | Read → Bash → Read → Edit → Read → Submit |
| 10 Implement Method | Read → Edit → Bash → Edit → Submit |
| 11 List Home Directory | LS → Submit |

## Loss Calculation

Currently using binary pass/fail for loss:
- Pass: loss = 0.0
- Fail: loss = 1.0

**Note**: LLM judge evaluation not yet implemented. When implemented, loss will be calculated as:
```
loss = 1 - (judge_score / max_score)
```

Where judge_score is a numerical evaluation (0-10) of task completion quality.

## Performance Observations

### Test Complexity vs Duration

```
Level 0-2 (Simple):     1-3 seconds, 1-2 turns
Level 3 (Write):        ~5 seconds, 3 turns
Level 4-5 (Complex):    9-15 seconds, 5-6 turns
```

### Token Efficiency

| Complexity | Avg Tokens | Tokens/Turn |
|------------|------------|-------------|
| Simple | ~3,700 | ~3,500 |
| Write | ~4,000 | ~1,350 |
| Complex | ~5,000 | ~900 |

Complex tests are more token-efficient per turn due to larger context accumulation.

## Baseline Metrics for Comparison

These metrics establish the baseline for comparing against Claude Code:

| Metric | MiniAgent (Baseline) |
|--------|---------------------|
| Pass Rate | 100% |
| Avg Duration | 7.4s |
| Avg Turns | 3.4 |
| Avg Tool Calls | 3.0 |
| Total Cost | ~$0.30 |

## Next Steps

1. Run Claude Code on same tests for comparison
2. Compute delta_loss between MiniAgent and Claude Code
3. Implement LLM judge for nuanced scoring
4. Identify tool gaps (B - A) for potential improvements
