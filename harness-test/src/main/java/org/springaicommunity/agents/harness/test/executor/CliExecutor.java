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

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for CLI process execution.
 *
 * <p>Implementations handle running external CLI processes with:
 * <ul>
 *   <li>Working directory configuration</li>
 *   <li>Input piping for interactive mode</li>
 *   <li>Timeout handling</li>
 *   <li>Output capture</li>
 * </ul>
 */
public interface CliExecutor {

    /**
     * Execute a CLI command with the given configuration.
     *
     * @param config execution configuration
     * @return execution result with output and status
     */
    ExecutionResult execute(ExecutionConfig config);

    /**
     * Get tool calls captured during the last execution.
     *
     * <p>For in-process executors like {@link InProcessExecutor}, this returns
     * structured tool call data captured via {@link org.springaicommunity.agents.harness.core.ToolCallListener}.
     * For subprocess executors like {@link DefaultCliExecutor}, this returns an empty list
     * (tool calls must be parsed from output).</p>
     *
     * @return list of tool calls from last execution, or empty list if not available
     */
    default List<ToolCallRecord> getToolCalls() {
        return List.of();
    }

    /**
     * Configuration for CLI execution.
     */
    record ExecutionConfig(
        List<String> command,
        Path workingDirectory,
        String input,
        int timeoutSeconds
    ) {
        public ExecutionConfig {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("command is required");
            }
            timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 120;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<String> command;
            private Path workingDirectory;
            private String input;
            private int timeoutSeconds = 120;

            public Builder command(List<String> command) {
                this.command = command;
                return this;
            }

            public Builder command(String... command) {
                this.command = List.of(command);
                return this;
            }

            public Builder workingDirectory(Path workingDirectory) {
                this.workingDirectory = workingDirectory;
                return this;
            }

            public Builder input(String input) {
                this.input = input;
                return this;
            }

            public Builder timeoutSeconds(int timeoutSeconds) {
                this.timeoutSeconds = timeoutSeconds;
                return this;
            }

            public ExecutionConfig build() {
                return new ExecutionConfig(command, workingDirectory, input, timeoutSeconds);
            }
        }
    }

}
