# Strategy: Addressing False Tool Gap Issues

## Problem Statement

The Venn diagram comparison detects "tool gaps" (B-A) that are false positives due to:
1. **Case sensitivity**: `bash` vs `Bash`
2. **Naming variants**: `Read` vs `ReadFile` vs `cat`
3. **Aliased tools**: Different names for equivalent functionality

## Sources of False Positives

| Issue | MiniAgent | Claude Code | Real Gap? |
|-------|-----------|-------------|-----------|
| Case | `bash` | `Bash` | No |
| Case | `edit` | `Edit` | No |
| Variant | `Read` | `Read` | No |
| Category | `bash ls` | `Glob` | **Partial** |

## Proposed Solution: Multi-Level Normalization

### Level 1: Case Normalization (Required)

Normalize all tool names to lowercase before comparison.

```java
private static Set<String> toToolSet(List<String> toolSequence) {
    if (toolSequence == null) return Set.of();
    return toolSequence.stream()
        .map(String::toLowerCase)  // Normalize case
        .collect(Collectors.toSet());
}
```

### Level 2: Tool Name Mapping (Recommended)

Create equivalence mapping for known tool variants:

```java
private static final Map<String, String> TOOL_ALIASES = Map.of(
    "bash", "shell",
    "sh", "shell",
    "cmd", "shell",
    "read", "read_file",
    "readfile", "read_file",
    "cat", "read_file",
    "write", "write_file",
    "writefile", "write_file",
    "edit", "edit_file",
    "editfile", "edit_file"
);

private static String normalizeToolName(String toolName) {
    String lower = toolName.toLowerCase();
    return TOOL_ALIASES.getOrDefault(lower, lower);
}
```

### Level 3: Tool Category Comparison (Optional)

Group tools by functional category for higher-level comparison:

```java
public enum ToolCategory {
    FILE_READ,      // Read, cat, head, tail
    FILE_WRITE,     // Write, echo >, tee
    FILE_EDIT,      // Edit, sed, awk
    FILE_SEARCH,    // Glob, Grep, find, rg
    SHELL_EXEC,     // Bash, sh, exec
    TASK_MGMT,      // TodoWrite, Task
    USER_INTERACT,  // AskUser, Submit
    WEB,            // WebFetch, WebSearch
    OTHER
}
```

## Implementation Plan

### Phase 1: Case Normalization (Immediate)

1. Update `ToolUsageComparison.toToolSet()` to normalize case
2. Update unit tests to verify case-insensitive comparison
3. Re-run baseline comparisons

### Phase 2: Alias Mapping (Short-term)

1. Create `ToolNameNormalizer` utility class
2. Define standard tool name mappings
3. Apply in `ToolUsageComparison`

### Phase 3: Category Analysis (Medium-term)

1. Create `ToolCategory` enum
2. Add `categorizeTools()` method
3. Add category-level Venn comparison to reports

## Recommended Output Format

After normalization, the Venn diagram should show:

```
─── Tool Usage Venn Diagram ─────────────────────────────────
  MiniAgent tools (A):   {bash, read, write}
  Claude Code tools (B): {bash, read, write, glob}

  Shared (A ∩ B):        {bash, read, write}
  MiniAgent only (A-B):  (none)
  Claude only (B-A):     {glob}   ← TRUE gap (file search tool)

  Similarity: 75.0%
  ⚠️  TOOL GAP: Consider onboarding: glob
```

## Impact on Baseline

After implementing Phase 1 (case normalization):

| Test | Before | After |
|------|--------|-------|
| 04-bash-command | "Gap: Bash" | "No gap" |
| 11-list-home-dir | "Gap: Bash" | "No gap" |
| 05-multi-file-read | "Gap: Glob" | "Gap: glob" (real) |

## Decision

**Recommended**: Implement Phase 1 immediately, Phase 2 when needed.

Phase 1 alone will eliminate the most common false positives (case sensitivity) with minimal code change.
