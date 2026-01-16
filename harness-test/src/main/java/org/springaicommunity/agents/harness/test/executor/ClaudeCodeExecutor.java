/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springaicommunity.agents.harness.test.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.tracking.ExecutionSummary;
import org.springaicommunity.agents.harness.test.tracking.ToolCallEvent;
import org.springaicommunity.claude.agent.sdk.ClaudeClient;
import org.springaicommunity.claude.agent.sdk.ClaudeSyncClient;
import org.springaicommunity.claude.agent.sdk.config.PermissionMode;
import org.springaicommunity.claude.agent.sdk.hooks.HookRegistry;
import org.springaicommunity.claude.agent.sdk.types.AssistantMessage;
import org.springaicommunity.claude.agent.sdk.types.Message;
import org.springaicommunity.claude.agent.sdk.types.ResultMessage;
import org.springaicommunity.claude.agent.sdk.types.ToolUseBlock;
import org.springaicommunity.claude.agent.sdk.types.control.HookInput;
import org.springaicommunity.claude.agent.sdk.types.control.HookOutput;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executor that runs prompts against Claude Code CLI using the SDK.
 *
 * <p>This executor captures tool calls and token usage for comparison
 * with other agents (like MiniAgent).
 *
 * <p>Usage:
 * <pre>{@code
 * ClaudeCodeExecutor executor = new ClaudeCodeExecutor();
 * ExecutionResult result = executor.execute(config);
 * ExecutionSummary summary = executor.getLastSummary();
 * }</pre>
 */
public class ClaudeCodeExecutor implements CliExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeCodeExecutor.class);

    private final String model;
    private ExecutionSummary lastSummary;

    /**
     * Creates executor with default model (claude-sonnet-4-20250514).
     */
    public ClaudeCodeExecutor() {
        this("claude-sonnet-4-20250514");
    }

    /**
     * Creates executor with specified model.
     */
    public ClaudeCodeExecutor(String model) {
        this.model = model;
    }

    @Override
    public ExecutionResult execute(ExecutionConfig config) {
        long startTime = System.currentTimeMillis();

        // Track tool calls via hooks
        List<ToolCallEvent> toolCalls = Collections.synchronizedList(new ArrayList<>());
        Map<String, ToolCallEvent> pendingCalls = new ConcurrentHashMap<>();

        HookRegistry hooks = new HookRegistry();

        // Pre-tool hook: capture tool name and input
        hooks.registerPreToolUse(input -> {
            String toolName = extractToolName(input);
            Map<String, Object> toolInput = extractToolInput(input);
            logger.debug("PreToolUse: {} with input {}", toolName, toolInput);

            // Store pending call to match with post-tool
            String callId = toolName + "_" + System.nanoTime();
            pendingCalls.put(callId, ToolCallEvent.success(toolName, toolInput, null));

            return HookOutput.allow();
        });

        // Post-tool hook: capture output and complete the event
        hooks.registerPostToolUse(input -> {
            String toolName = extractToolName(input);
            Object toolOutput = extractToolOutput(input);
            logger.debug("PostToolUse: {} with output {}", toolName,
                toolOutput != null ? toolOutput.toString().substring(0, Math.min(100, toolOutput.toString().length())) : "null");

            // Find matching pending call and complete it
            String matchKey = pendingCalls.keySet().stream()
                .filter(k -> k.startsWith(toolName + "_"))
                .findFirst()
                .orElse(null);

            if (matchKey != null) {
                ToolCallEvent pending = pendingCalls.remove(matchKey);
                toolCalls.add(ToolCallEvent.success(toolName, pending.input(), toolOutput));
            } else {
                // No pending match, create standalone event
                toolCalls.add(ToolCallEvent.success(toolName, Map.of(), toolOutput));
            }

            return HookOutput.allow();
        });

        StringBuilder output = new StringBuilder();
        int inputTokens = 0;
        int outputTokens = 0;
        int thinkingTokens = 0;
        int numTurns = 0;
        boolean success = false;

        try (ClaudeSyncClient client = ClaudeClient.sync()
                .workingDirectory(config.workingDirectory())
                .model(model)
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .permissionMode(PermissionMode.DANGEROUSLY_SKIP_PERMISSIONS)  // Allow all operations for testing
                .hookRegistry(hooks)
                .build()) {

            client.connect(config.input());

            for (Message msg : client.messages()) {
                if (msg instanceof AssistantMessage am) {
                    String text = am.text();
                    if (!text.isEmpty()) {
                        output.append(text).append("\n");
                    }

                    // Extract tool uses from message (backup if hooks miss them)
                    for (ToolUseBlock toolUse : am.getToolUses()) {
                        logger.debug("ToolUseBlock from message: {}", toolUse.name());
                    }
                }
                else if (msg instanceof ResultMessage rm) {
                    numTurns = rm.numTurns();
                    success = !rm.isError();

                    // Extract usage from result
                    Map<String, Object> usage = rm.usage();
                    if (usage != null) {
                        inputTokens = getInt(usage, "input_tokens", 0);
                        outputTokens = getInt(usage, "output_tokens", 0);
                        thinkingTokens = getInt(usage, "thinking_tokens", 0);
                    }

                    logger.info("Claude Code completed: turns={}, tokens={}+{}+{}, success={}",
                        numTurns, inputTokens, outputTokens, thinkingTokens, success);
                }
            }
        }
        catch (Exception e) {
            logger.error("Claude Code execution failed", e);
            output.append("ERROR: ").append(e.getMessage());
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // Build execution summary
        lastSummary = ExecutionSummary.builder()
            .agentId("ClaudeCode")
            .toolCalls(new ArrayList<>(toolCalls))
            .inputTokens(inputTokens)
            .outputTokens(outputTokens)
            .thinkingTokens(thinkingTokens)
            .numTurns(numTurns)
            .success(success)
            .durationMs(durationMs)
            .build();

        return new ExecutionResult(
            output.toString(),
            success ? 0 : 1,
            durationMs,
            false
        );
    }

    /**
     * Gets the execution summary from the last run.
     */
    public ExecutionSummary getLastSummary() {
        return lastSummary;
    }

    @SuppressWarnings("unchecked")
    private String extractToolName(HookInput input) {
        if (input instanceof HookInput.PreToolUseInput pre) {
            return pre.toolName();
        }
        if (input instanceof HookInput.PostToolUseInput post) {
            return post.toolName();
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractToolInput(HookInput input) {
        if (input instanceof HookInput.PreToolUseInput pre) {
            return pre.toolInput();
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Object extractToolOutput(HookInput input) {
        if (input instanceof HookInput.PostToolUseInput post) {
            return post.toolResponse();
        }
        return null;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
