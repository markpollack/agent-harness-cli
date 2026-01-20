# Step 3: LLM Judge Component - Learnings

**Date**: January 2026
**Status**: Complete (10/11 bootstrap tests passing)

## Summary

Implemented ExpectedBehaviorJudge using Claude Opus 4.5 for semantic evaluation of agent task completion. This provides 0-10 numerical scoring instead of binary pass/fail.

## Results

| Metric | Value |
|--------|-------|
| Tests Passed | 10/11 (91%) |
| Tests Failed | 1 |
| Average Loss | ~0.09 |
| Failed Test | Find and Fix Bug |

## Key Implementation Details

### Judge Architecture

- **ExpectedBehaviorJudge** extends `LLMJudge` from agent-judge-llm
- Uses Claude Opus 4.5 for evaluation (stronger model than agent)
- 0-10 scoring scale with pass threshold of 7.0
- Weighted 60% of total verdict score (deterministic judges: 40%)

### Weight Distribution

```java
LLM_JUDGE_WEIGHT = 0.6        // ExpectedBehaviorJudge
DETERMINISTIC_WEIGHT = 0.4    // file_contains, no_exceptions, etc.
```

### Loss Calculation

```
loss = 1 - (weighted_score)
where weighted_score = (deterministic_scores * 0.4) + (llm_score * 0.6)
```

## Learnings

### 1. Spring AI maxTokens is Required

**Issue**: When using `AnthropicChatOptions.builder().defaultOptions(options)`, it completely replaces defaults including maxTokens.

**Root Cause**: Line 1010-1012 in AnthropicChatModel:
```java
public Builder defaultOptions(AnthropicChatOptions defaultOptions) {
    this.defaultOptions = defaultOptions;  // Full replacement, not merge
    return this;
}
```

**Default**: `DEFAULT_MAX_TOKENS = 500` (quite low for many use cases)

**Solution**: Always explicitly set maxTokens. For judges, 8192 is a safe value.

```java
AnthropicChatOptions options = AnthropicChatOptions.builder()
    .model("claude-opus-4-5")
    .maxTokens(8192)  // Required! Default 500 is too low
    .build();
```

### 2. agent-judge Version Alignment

**Issue**: agent-judge 0.9.0-SNAPSHOT was using Spring AI 1.1.0 + Spring Boot 3.4.2, incompatible with Spring AI 2.0.

**Error**: `NoClassDefFoundError: org/springframework/core/retry/RetryException`

**Solution**: Updated agent-judge pom.xml:
```xml
<spring-framework.version>7.0.2</spring-framework.version>
<spring-ai.version>2.0.0-SNAPSHOT</spring-ai.version>
<spring-boot.version>4.0.1</spring-boot.version>
<reactor.version>2024.0.5</reactor.version>
```

### 3. Tool Working Directory Context (Bug Found)

**Issue**: Bootstrap test "Find and Fix Bug" failed because GlobTool searched from JVM's working directory, not the workspace.

**Root Cause**: GlobTool line 79:
```java
Path searchPath = StringUtils.hasText(path) ? Paths.get(path) : Paths.get(".");
```

When agent doesn't specify path, `Paths.get(".")` resolves to JVM's cwd, **not** the workspace the agent was told to use.

**Evidence**: Agent searched for `**/*.java` and found `./RunAITest.java` (JBang script) instead of workspace files.

**Impact**: Agent got stuck (detected same output 3 times), test failed with score 0.25.

**Required Fix**: Tools need workspace context injection. Options:
1. Pass workspace as constructor parameter to tools
2. Create workspace-aware tool wrappers
3. Use ThreadLocal for current workspace context

### 4. Stuck Detection Works

The `AgentLoopAdvisor` correctly detected the stuck condition:
```
INFO AgentLoopAdvisor -- Agent stuck for run ...: same output 3 times
WARN MiniAgent -- MiniAgent terminated: STUCK_DETECTED at turn 4
```

This safety mechanism prevents infinite loops when agent can't make progress.

### 5. JBang Dependency Management

**Issue**: Explicit Spring dependencies in JBang script caused version conflicts.

**Solution**: Let transitive dependencies flow from spring-ai-anthropic instead of specifying explicit versions.

Before (problematic):
```java
//DEPS org.springframework:spring-core:7.0.2
//DEPS org.springframework:spring-web:7.0.2
```

After (fixed):
```java
// No explicit Spring deps - let spring-ai-anthropic manage versions
```

## Files Modified

| File | Purpose |
|------|---------|
| `harness-test/pom.xml` | Added agent-judge-llm dependency |
| `ExpectedBehaviorJudge.java` | New LLM-powered judge |
| `JuryFactory.java` | Accept ChatClient.Builder, add LLM judge |
| `RunAITest.java` | Configure Opus 4.5 for judge |
| `agent-judge/pom.xml` | Updated to Spring AI 2.0 |

## Next Steps

1. **Fix Tool Working Directory**: Implement workspace context for GlobTool, GrepTool, etc.
2. **Track Tool Calls for Judge**: Pass tool call sequence to LLM judge for better evaluation
3. **Add output_not_contains Criterion**: Warning seen in logs - implement if needed
4. **Consider Extended Thinking**: Opus 4.5 thinking mode could improve judge reasoning

## Test Results Detail

```
[PASS] Echo Response (6462ms)
[PASS] Read File Content (12365ms)
[PASS] Create File (12006ms)
[PASS] Run Bash Command (8491ms)
[PASS] Read Multiple Files (12342ms)
[PASS] Edit File Content (13472ms)
[PASS] Fix Syntax Error (19329ms)
[PASS] Add Null Check (20252ms)
[FAIL] Find and Fix Bug (9961ms) - Tool working directory bug
[PASS] Implement Method (22122ms)
[PASS] List Home Directory (16943ms)
```

## Key Insight

The failing test revealed a fundamental architectural issue: **tools don't share workspace context with the agent**. This is a high-priority fix needed before intermediate tests.
