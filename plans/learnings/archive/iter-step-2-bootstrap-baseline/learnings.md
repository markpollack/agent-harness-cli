# Step 2 Learnings: Establish Bootstrap Baseline

> **Completed**: 2026-01-20
> **Duration**: ~5 minutes (test execution ~81 seconds)

## Summary

Ran all 11 bootstrap tests with MiniAgent using Claude Sonnet 4.5. All tests passed, establishing a 100% pass rate baseline.

## What Was Done

### 1. Executed All Bootstrap Tests

```bash
jbang RunAITest.java --category bootstrap
```

**Results**: 11/11 tests passed (100% pass rate)

### 2. Captured Metrics

For each test:
- Pass/fail status
- Token usage
- Tool call count
- Turn count
- Duration
- Approximate cost

### 3. Documented Baseline

Created `bootstrap-baseline.md` with:
- Summary statistics
- Detailed per-test results
- Tool usage patterns by complexity level
- Performance observations

## Key Findings

### 1. Perfect Pass Rate

All 11 bootstrap tests passed with Claude Sonnet 4.5. This suggests:
- MiniAgent's tool set is sufficient for bootstrap-level tasks
- System prompt provides adequate guidance
- Tool descriptions are clear enough for the model

### 2. Complexity Scaling

| Level | Tests | Avg Duration | Avg Turns |
|-------|-------|--------------|-----------|
| 0-2 | 3 | 2.3s | 1.7 |
| 3 | 2 | 5.1s | 3.0 |
| 4-5 | 6 | 10.6s | 4.7 |

Duration and turns scale linearly with task complexity.

### 3. Tool Usage Patterns

**Most Used Tools**:
1. Read (9 tests)
2. Submit (11 tests - always)
3. Edit (5 tests)
4. Bash (5 tests)
5. Write (1 test)

**Least Used Tools**:
- Glob (0 tests)
- Grep (0 tests)
- LS (1 test)

Bootstrap tests don't require search/discovery tools.

### 4. JuryFactory Warnings

Observed warnings during test execution:
```
WARN - Unknown criterion type: output_not_contains
WARN - Expected behavior defined but LLM validation not yet implemented
```

**Implications**:
- Need to add `output_not_contains` judge type
- LLM-based validation needs implementation for nuanced scoring

## Issues Discovered

### 1. Missing Judge Types

The JuryFactory doesn't recognize `output_not_contains` criterion used in test 11 (List Home Directory). This is a simple deterministic judge that should be added.

### 2. No LLM Judge Yet

The "expected_behavior" field in use cases is parsed but not evaluated. Implementing an LLM judge would provide:
- Nuanced scoring (0-10 instead of pass/fail)
- Better loss calculation
- More detailed feedback for improvement

### 3. Tool Call Count Discrepancy

The "tool calls" count in logs excludes the final Submit tool. This is fine but worth noting for accurate comparisons.

## Metrics Summary

| Metric | Value |
|--------|-------|
| Tests Run | 11 |
| Tests Passed | 11 (100%) |
| Total Duration | ~81 seconds |
| Total Tokens | ~48,574 |
| Total Tool Calls | 33 |
| Estimated Cost | ~$0.30 |
| Average Loss | 0.0 |

## Recommendations

### For Step 3 (Tool Tracking)

When comparing with Claude Code:
- Track exact tool sequences (order matters)
- Capture tool arguments for deeper analysis
- Note any tools Claude Code uses that MiniAgent doesn't have

### For Improvement

1. **Add `output_not_contains` judge** - Simple addition to JuryFactory
2. **Implement LLM judge** - For `expected_behavior` evaluation
3. **Consider caching** - Schema generation takes ~100ms per test, could be cached

## Exit Criteria Verification

- [x] All 11 bootstrap tests executed
- [x] Pass/fail captured for each test
- [x] Tool calls captured for each test
- [x] Loss computed (binary: pass=0, fail=1)
- [x] Baseline document created: `bootstrap-baseline.md`
- [x] Learnings document created

## Next Step

Step 3: Implement Tool Tracking Venn Sets - Create `ToolUsageComparison` record and compute A, B, A∩B, A-B, B-A for MiniAgent vs Claude Code comparisons.
