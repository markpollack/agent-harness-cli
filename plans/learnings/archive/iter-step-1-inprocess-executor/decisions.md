# Step 1 Decisions: Wire InProcessExecutor to RunAITest

> **Date**: 2026-01-20

## Decision 1: Use agent-judge-core Directly

**Context:** Two judge implementations exist:
- `agent-judge-core` from org.springaicommunity (package: `org.springaicommunity.judge`)
- `spring-ai-agents-judge` from org.springaicommunity.agents (package: `org.springaicommunity.agents.judge`)

**Decision:** Use `agent-judge-core` directly in harness-test.

**Rationale:**
- Simpler dependency chain (no wrapper layer)
- Independent project with its own release cycle
- Clearer package naming
- Already installed in local maven repo at version 0.9.0-SNAPSHOT

**Consequences:**
- Need to update imports in any code previously using `org.springaicommunity.agents.judge`
- Version synchronization with agent-judge project required

## Decision 2: Executor Selection Based on Environment Variable

**Context:** RunAITest needs to work both locally (with API key) and in CI (possibly without).

**Decision:** Check `ANTHROPIC_API_KEY` environment variable to select executor:
- If set: Use InProcessExecutor with structured tool call capture
- If not set: Fall back to DefaultCliExecutor (subprocess)

**Rationale:**
- Graceful degradation without requiring API key
- Clear warning message when falling back
- Preserves existing subprocess functionality

**Consequences:**
- Tool call capture only available with InProcessExecutor
- Comparison tests may not work without API key

## Decision 3: Cached Executor for Test Reuse

**Context:** RunAITest may run multiple tests in sequence.

**Decision:** Cache the executor instance in a static field and reuse across tests.

**Rationale:**
- Avoid recreating ChatModel for each test
- Reduces initialization overhead
- Single configuration point

**Consequences:**
- ChatModel settings apply to all tests in a run
- Cannot change model mid-run (acceptable for consistency)

## Decision 4: Model Configuration

**Context:** Need to select model and settings for MiniAgent execution.

**Decision:** Use:
- Model: `claude-sonnet-4-5` (Claude Sonnet 4.5)
- Max tokens: 4096
- Max turns: 10

**Rationale:**
- Sonnet 4.5 is the latest and most capable Sonnet model
- Provides good balance of capability and cost
- 4096 tokens sufficient for bootstrap tests
- 10 turns provides safety limit while allowing multi-step tasks

**Available Model Tiers (for future experimentation):**

| Tier | Model ID | Use Case |
|------|----------|----------|
| Top | `claude-opus-4-5` | Complex tasks, highest capability |
| Mid | `claude-sonnet-4-5` | Default - balanced capability/cost |
| Fast | `claude-haiku-4-5` | Quick tasks, lower cost |

**Future:** Model should be varied as an experimental parameter once more steps are implemented. This will allow:
- Measuring loss differences across model tiers
- Cost/capability tradeoff analysis
- Identifying tasks that require higher-tier models

**Consequences:**
- May need adjustment for more complex intermediate tests
- Cost considerations for running full test suites
- Future: Add `--model` flag to RunAITest for experimentation
