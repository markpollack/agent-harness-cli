# Key Learnings - Iterative MiniAgent Development

> Compacted summary of learnings. Detailed documents in `archive/`.

## Step 1: InProcessExecutor (Complete)
- **JBang** resolves SNAPSHOT deps via `//REPOS mavenlocal,spring-snapshots=...`
- **agent-judge packages**: Use `org.springaicommunity.judge.*` (not `agents.judge`)
- **Model tiers**: opus-4-5 (judge), sonnet-4-5 (agent), haiku-4-5 (fast)
- **Tool capture**: `CapturingToolCallListener` provides structured tool call data

## Step 2: Bootstrap Baseline (Complete)
- **Result**: 11/11 tests pass (100%)
- **Tool usage**: Read (9), Submit (11), Edit (5), Bash (5), Glob/Grep (0)
- **Complexity scaling**: Duration/turns scale linearly with task level

## Step 3: LLM Judge (Complete)
- **ExpectedBehaviorJudge**: Opus 4.5, 0-10 scoring, 60% weight in jury
- **maxTokens required**: Anthropic API requires explicit value (default 500 too low, use 8192)
- **Spring AI 2.0**: Requires Spring Framework 7.x, Spring Boot 4.x
- **Stuck detection**: AgentLoopAdvisor detects 3x same output → terminates

## Tool Working Directory Fix (Complete)
- **Issue**: GlobTool/GrepTool used JVM cwd, not agent workspace
- **Fix**: Added `workingDirectory(Path)` builder method, MiniAgent passes config.workingDirectory()
- **Result**: 11/11 bootstrap tests now pass

## Step 4: Tool Tracking Venn Sets (Complete)
- **ToolUsageComparison record**: Computes A, B, A∩B, A-B, B-A sets
- **Key insight**: `claudeOnly` (B-A) = onboarding candidates
- **Jaccard similarity**: Measures tool set overlap (0.0-1.0)
- **ComparisonReport integration**: Venn diagram in format(), tool gap in analyzeDifferences()
- **Unit tests**: 12 tests covering all edge cases (empty sets, identical, disjoint)

## Design Decisions

### SpringAiJuryAdapter Pattern
Adapter in agent-harness converts LoopState → JudgmentContext. This is correct:
- agent-judge stays independent (no harness dependency)
- Adapter lives in consuming code (agent-harness)
- No circular dependencies

### JBang Script Size
RunAITest.java is 391 lines - acceptable. Could extract ChatModel config to harness-test if it grows.

## Version Alignment Required
| Project | Spring AI | Spring Boot | Spring Framework |
|---------|-----------|-------------|------------------|
| agent-harness | 2.0.0-SNAPSHOT | 4.0.1 | 7.0.2 |
| agent-judge | 2.0.0-SNAPSHOT | 4.0.1 | 7.0.2 |
| spring-ai-agent-utils | 0.5.0-SNAPSHOT | - | 7.0.2 |

## Next Steps
1. **Step 5**: First Comparison Run (MiniAgent vs Claude Code)
2. **Intermediate tests**: Progress when bootstrap loss < 0.2
