# Step 4: Tool Tracking Venn Sets - Learnings

**Date**: January 2026
**Status**: Complete

## Summary

Implemented `ToolUsageComparison` record for computing Venn diagram sets between MiniAgent and Claude Code tool usage. This enables identification of tool gaps that may explain performance differences.

## Implementation

### ToolUsageComparison Record

Created in `harness-test/comparison/ToolUsageComparison.java`:

```java
public record ToolUsageComparison(
    Set<String> miniAgentTools,     // A
    Set<String> claudeCodeTools,    // B
    Set<String> shared,             // A ∩ B
    Set<String> miniAgentOnly,      // A - B
    Set<String> claudeOnly          // B - A (onboarding candidates)
)
```

### Key Methods

| Method | Purpose |
|--------|---------|
| `from(miniAgent, claudeCode)` | Factory method from ExecutionSummary |
| `hasToolGap()` | True if Claude used tools MiniAgent lacks |
| `identicalToolSets()` | True if both used same tools |
| `jaccardSimilarity()` | Set overlap metric (0.0-1.0) |
| `format()` | Human-readable Venn diagram |

### ComparisonReport Integration

Added to `ComparisonReport.java`:

1. **`toolUsageComparison()`** - Returns computed Venn sets
2. **`analyzeDifferences()`** - Now includes tool gap detection
3. **`format()`** - Now includes Venn diagram section

### Output Example

```
─── Tool Usage Venn Diagram ─────────────────────────────────
  MiniAgent tools (A):   {Read, Write, Edit}
  Claude Code tools (B): {Read, Bash, TodoWrite}

  Shared (A ∩ B):        {Read}
  MiniAgent only (A-B):  {Write, Edit}
  Claude only (B-A):     {Bash, TodoWrite}

  Similarity: 25.0%
  ⚠️  TOOL GAP: Claude used tools MiniAgent lacks
  📋 Onboarding candidates: Bash, TodoWrite
```

## Design Decisions

### Why Jaccard Similarity?

The Jaccard coefficient (`|A ∩ B| / |A ∪ B|`) is standard for set similarity:
- 1.0 = identical tool sets
- 0.0 = completely disjoint sets
- Easy to interpret as percentage

### Why Track "MiniAgent Only" (A - B)?

While B - A identifies tools to potentially onboard, A - B can reveal:
- Inefficiencies (MiniAgent uses extra tools unnecessarily)
- Different valid approaches
- MiniAgent capabilities not exposed to Claude

### Edge Cases Handled

1. **Empty tool lists** - Returns empty sets, Jaccard = 1.0 (both empty = identical)
2. **Null tool sequences** - Treated as empty set
3. **Duplicate tool calls** - Deduplicated (we track unique tools used)

## Unit Tests

12 tests in `ToolUsageComparisonTest.java`:

| Test | Coverage |
|------|----------|
| `fromComputesVennSetsCorrectly` | Core set computation |
| `hasToolGapReturnsTrueWhenClaudeHasExclusiveTools` | Tool gap detection |
| `hasToolGapReturnsFalseWhenNoGap` | No gap case |
| `identicalToolSetsReturnsTrueWhenSameTools` | Identical sets |
| `jaccardSimilarityComputesCorrectly` | Similarity math |
| `jaccardSimilarityIsOneForIdenticalSets` | 1.0 case |
| `jaccardSimilarityIsZeroForDisjointSets` | 0.0 case |
| `jaccardSimilarityIsOneForBothEmpty` | Both empty |
| `formatIncludesAllSets` | Format output |
| `formatShowsToolGapWarning` | Warning display |
| `formatShowsIdenticalWhenSameTools` | Identical message |
| `handlesNullToolSequence` | Null handling |

## Files Created

| File | Purpose |
|------|---------|
| `harness-test/.../comparison/ToolUsageComparison.java` | Venn set record |
| `harness-test/.../comparison/ToolUsageComparisonTest.java` | Unit tests |

## Files Modified

| File | Change |
|------|--------|
| `harness-test/.../comparison/ComparisonReport.java` | Added `toolUsageComparison()`, updated `analyzeDifferences()` and `format()` |

## Test Results

```
Tests run: 123, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Key Insight

The B - A set ("Claude Only") is the most actionable output. When a comparison shows:
- High loss for MiniAgent
- Non-empty B - A set (tool gap)

Then the tools in B - A become candidates for onboarding from spring-ai-agent-utils.

## Next Steps

Step 5: Run first comparison to:
1. Execute same test on both MiniAgent and Claude Code
2. Compute Δloss
3. Analyze B - A for tool onboarding candidates
