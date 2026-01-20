# Step 1 Learnings: Wire InProcessExecutor to RunAITest

> **Completed**: 2026-01-20
> **Duration**: ~30 minutes

## Summary

Successfully wired InProcessExecutor to RunAITest.java, enabling in-process MiniAgent execution with structured tool call capture.

## What Was Done

### 1. Updated RunAITest.java

**Changes:**
- Added JBang dependencies for `harness-agents`, `spring-ai-anthropic`, `spring-core`
- Created `getOrCreateExecutor()` method that:
  - Checks for `ANTHROPIC_API_KEY` environment variable
  - Falls back to `DefaultCliExecutor` (subprocess) if not set
  - Creates `InProcessExecutor` with ChatModel if API key available
- Created `createInProcessExecutor()` for ChatModel configuration
- Updated `runComparison()` to pass executor to `ComparisonRunner`

### 2. Fixed agent-judge Package Imports

**Issue:** harness-test imported from `org.springaicommunity.agents.judge.*` but agent-judge-core uses `org.springaicommunity.judge.*`

**Files Fixed:**
- `JuryFactory.java` - 7 imports
- `TestJudgmentAdapter.java` - 3 imports
- `TestHarness.java` - 4 imports
- `JuryFactoryTest.java` - 6 imports
- `TestJudgmentAdapterTest.java` - 2 imports

**Additional Fix:** `AgentExecutionStatus` → `ExecutionStatus` (class renamed in agent-judge)

### 3. Updated harness-test pom.xml

Changed dependency from:
```xml
<dependency>
    <groupId>org.springaicommunity.agents</groupId>
    <artifactId>spring-ai-agents-judge</artifactId>
    <version>${spring-ai-agents.version}</version>
</dependency>
```

To:
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>agent-judge-core</artifactId>
    <version>0.9.0-SNAPSHOT</version>
</dependency>
```

## Test Results

| Test | Status | Tool Calls | Duration |
|------|--------|------------|----------|
| 01-echo-response.yaml | PASSED | 1 (Submit) | 3154ms |
| 02-file-read.yaml | PASSED | 2 (Read, Submit) | 5711ms |

## Key Learnings

### 1. JBang Integration Works Well

JBang successfully resolves dependencies from:
- Local maven repository (mavenlocal)
- Spring milestones repository
- Spring snapshots repository

The `//REPOS` directive is essential for SNAPSHOT dependencies.

### 2. Independent Projects: agent-judge vs spring-ai-agents

The judge framework exists in two places:
- `agent-judge` - Independent project at `~/community/agent-judge` with package `org.springaicommunity.judge`
- `spring-ai-agents-judge` - Part of spring-ai-agents, wraps agent-judge with package `org.springaicommunity.agents.judge`

**Decision:** Use agent-judge-core directly to avoid the wrapper layer complexity.

### 3. Model Selection

Updated to use Claude Sonnet 4.5 (`claude-sonnet-4-5`) - the latest Sonnet model. Model tiers available for future experimentation:

| Tier | Model ID | Notes |
|------|----------|-------|
| Top | `claude-opus-4-5` | Highest capability |
| Mid | `claude-sonnet-4-5` | **Default** - balanced |
| Fast | `claude-haiku-4-5` | Lower cost |

**Future:** Model will be varied as an experimental parameter to measure loss differences across tiers.

### 4. Tool Call Capture via CapturingToolCallListener

InProcessExecutor captures tool calls automatically via `CapturingToolCallListener`:
- Tool name, input, output, and duration captured
- No log parsing required
- Type-safe access to tool call data
- Visible in logs: `MiniAgent completed: 2 tool calls in 5596ms`

### 4. Fallback Behavior

When `ANTHROPIC_API_KEY` is not set:
- Warning printed to stderr
- Falls back to `DefaultCliExecutor` (subprocess)
- Tool call capture not available in subprocess mode

## Issues Encountered

### 1. Test Discovery Failure

**Symptom:** `TestEngine with ID 'junit-jupiter' failed to discover tests`

**Root Cause:** `NoClassDefFoundError: org/springaicommunity/agents/judge/context/JudgmentContext` - wrong package name

**Fix:** Updated imports to use correct package from agent-judge-core

### 2. Missing Dependency

**Symptom:** `ClassNotFoundException` for agent-judge classes

**Fix:** Installed agent-judge to local maven repo: `cd ~/community/agent-judge && mvn install -DskipTests`

## Recommendations for Future Steps

1. **Consider adding agent-judge as a declared dependency** in the parent pom.xml to avoid version drift

2. **Document the dependency relationship** between agent-judge and spring-ai-agents-judge

3. **Add integration test** that verifies InProcessExecutor works correctly in CI environment

## Exit Criteria Verification

- [x] RunAITest executes MiniAgent in-process (not subprocess)
- [x] Tool calls captured via CapturingToolCallListener
- [x] Single bootstrap test case runs successfully
- [x] Output includes captured tool call sequence
- [x] Unit/integration tests pass (98 tests)
- [x] Learnings document created

## Next Step

Step 2: Establish Bootstrap Baseline - Run all 11 bootstrap tests and compute loss for each.
