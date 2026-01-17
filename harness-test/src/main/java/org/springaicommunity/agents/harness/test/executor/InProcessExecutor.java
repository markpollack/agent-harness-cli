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
import org.springframework.ai.chat.model.ChatModel;
import org.springaicommunity.agents.harness.agents.mini.MiniAgent;
import org.springaicommunity.agents.harness.agents.mini.MiniAgentConfig;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Executor that runs MiniAgent directly in-process.
 *
 * <p>Unlike {@link DefaultCliExecutor} which runs the CLI as a subprocess and parses
 * stdout, this executor instantiates MiniAgent directly in the same JVM. This provides
 * direct access to structured tool call events via {@link CapturingToolCallListener},
 * eliminating the need for brittle regex parsing.</p>
 *
 * <p>Benefits over subprocess execution:</p>
 * <ul>
 *   <li>Structured tool call data (name, input, output, duration)</li>
 *   <li>No log parsing required</li>
 *   <li>Faster execution (no process startup overhead)</li>
 *   <li>Type-safe access to execution results</li>
 * </ul>
 *
 * <p>This executor provides a hook for commercial tuvium integration - the
 * {@link ToolCallRecord} instances can be converted to tuvium TrackingEvents
 * for richer tracking (tokens, costs, metrics).</p>
 */
public class InProcessExecutor implements CliExecutor {

    private static final Logger logger = LoggerFactory.getLogger(InProcessExecutor.class);

    private final ChatModel chatModel;
    private final int maxTurns;

    private CapturingToolCallListener listener;

    /**
     * Create an in-process executor with the given chat model.
     *
     * @param chatModel the chat model to use for MiniAgent
     */
    public InProcessExecutor(ChatModel chatModel) {
        this(chatModel, 20);
    }

    /**
     * Create an in-process executor with custom max turns.
     *
     * @param chatModel the chat model to use for MiniAgent
     * @param maxTurns maximum turns for agent execution
     */
    public InProcessExecutor(ChatModel chatModel, int maxTurns) {
        if (chatModel == null) {
            throw new IllegalArgumentException("chatModel is required");
        }
        this.chatModel = chatModel;
        this.maxTurns = maxTurns;
    }

    @Override
    public ExecutionResult execute(ExecutionConfig config) {
        Path workingDirectory = config.workingDirectory() != null
                ? config.workingDirectory()
                : Path.of(System.getProperty("user.dir"));

        logger.debug("Running MiniAgent in-process in {}", workingDirectory);

        // Create capturing listener for this execution
        this.listener = new CapturingToolCallListener();

        // Build MiniAgent config
        MiniAgentConfig agentConfig = MiniAgentConfig.builder()
                .workingDirectory(workingDirectory)
                .maxTurns(maxTurns)
                .commandTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .build();

        // Build and run MiniAgent
        MiniAgent agent = MiniAgent.builder()
                .config(agentConfig)
                .model(chatModel)
                .toolCallListener(listener)
                .build();

        long startTime = System.currentTimeMillis();
        try {
            var result = agent.run(config.input());
            long durationMs = System.currentTimeMillis() - startTime;

            logger.debug("MiniAgent completed: {} tool calls in {}ms",
                    listener.getToolCallCount(), durationMs);

            if (result.isTurnLimitReached()) {
                return ExecutionResult.timeout(result.output(), durationMs);
            }

            return ExecutionResult.success(result.output(), durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            logger.error("MiniAgent execution failed", e);
            return ExecutionResult.failed(e.getMessage(), 1, durationMs);
        }
    }

    /**
     * Get the tool call records from the last execution.
     *
     * @return list of tool calls, or empty list if no execution has occurred
     */
    public List<ToolCallRecord> getToolCalls() {
        return listener != null ? listener.getRecords() : List.of();
    }

    /**
     * Get the capturing listener from the last execution.
     *
     * @return the listener, or null if no execution has occurred
     */
    public CapturingToolCallListener getListener() {
        return listener;
    }

    /**
     * Create a builder for more complex configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChatModel chatModel;
        private int maxTurns = 20;

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public InProcessExecutor build() {
            return new InProcessExecutor(chatModel, maxTurns);
        }
    }

}
