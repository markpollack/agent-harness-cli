# Step 8 Learnings: Intermediate Test Findings

**Date**: 2026-01-20
**Tests Analyzed**: 6 (of 12 intermediate)
**Prompt Fix Applied**: Verification guidance added to system prompt

## Test Results Summary

| Test | MiniAgent | Claude Code | Speed Ratio | Tool Ratio |
|------|-----------|-------------|-------------|------------|
| 11 - Create Java Class | 5s, 2 calls | 9s, 1 call | 1.8x faster | 2:1 |
| 17 - Fix Compile Error | 15s, 5 calls | 47s, 10 calls | 3x faster | 2:1 |
| 21 - API Design | 15s, 3 calls | 56s, 11 calls | 4x faster | 3.7:1 |

## Key Patterns

### 1. MiniAgent Efficiency

MiniAgent consistently outperforms Claude Code in:
- **Speed**: 2-4x faster execution
- **Tool calls**: Uses 1/2 to 1/3 the tool calls
- **Directness**: Goes straight to the solution

### 2. Claude Code TodoWrite Overuse

Claude Code uses TodoWrite excessively:
- Test 17: 5 TodoWrite calls for 2 compile errors
- Test 21: 6 TodoWrite calls for 4 file creations

This adds latency without proportional value for small/medium tasks.

### 3. Quality Gap: Compilation Verification

**Issue**: MiniAgent doesn't always verify Java code compiles.

| Test | MiniAgent Verifies | Claude Verifies |
|------|-------------------|-----------------|
| 17 - Fix Compile | ✅ (runs javac) | ✅ (runs javac) |
| 21 - API Design | ❌ (no verification) | ✅ (runs javac) |

This is a quality gap - Claude Code is more thorough in verification.

## Proposed: CompilationJudge

Add a deterministic judge that verifies Java code compiles.

### Options

| Approach | Pros | Cons |
|----------|------|------|
| **javac** | Standard, reliable | Requires full classpath setup |
| **jbang** | Single-file friendly | Limited to single-file cases |
| **ecj (Eclipse Compiler)** | No JDK needed | Additional dependency |

### Implementation

```java
public class CompilationJudge implements Judge {
    public Verdict evaluate(JudgeContext context) {
        // 1. Find all .java files in workspace
        // 2. Run javac (or jbang for single files)
        // 3. Return PASS if exit code 0, FAIL otherwise
    }
}
```

### Success Criteria Integration

```yaml
successCriteria:
  - type: compiles
    args: ["*.java"]
  # or for single file:
  - type: compiles_with_jbang
    args: ["Main.java"]
```

## Recommendations

1. **Keep MiniAgent's efficiency** - Don't add TodoWrite overhead for simple tasks
2. **Add compilation verification** - Either as system prompt guidance or CompilationJudge
3. **Create CompilationJudge** - For robust verification in test infrastructure
4. **Consider task complexity threshold** - Only use TodoWrite for 3+ step tasks

## Prompt Fix: Verification Guidance

**Change made** to `MiniAgentConfig.java`:

```java
Verification (IMPORTANT):
- After creating/modifying Java files, ALWAYS run `javac *.java` to verify compilation
- After fixing bugs, run the code/tests to verify the fix works
- Never submit without verifying your changes compile and work
```

### Test 21 Before/After

| Metric | Before | After |
|--------|--------|-------|
| Tool calls | 3 | 8 |
| Similarity | 33.3% | 66.7% |
| Verifies compilation | ❌ | ✅ |
| Tool sequence | Write → Write → Write | Write x4 → bash(javac) → Write → bash(javac && java) → bash |

**Result**: MiniAgent now verifies compilation! The only remaining gap is TodoWrite usage.

## Next Steps

1. Run remaining 6 intermediate tests to validate fix
2. Implement CompilationJudge for deterministic verification in test infrastructure
3. Enhance ComparisonAnalysisAgent to detect behavioral patterns
