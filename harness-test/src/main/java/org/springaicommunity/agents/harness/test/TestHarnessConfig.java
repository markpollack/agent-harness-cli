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

package org.springaicommunity.agents.harness.test;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for the TestHarness.
 *
 * <p>Defines CLI commands, directories, and execution parameters for test runs.
 *
 * @param cliCommand the CLI command to execute (e.g., ["claude", "--print"])
 * @param useCasesDir directory containing use case YAML files
 * @param transcriptsDir directory for saving test transcripts
 * @param saveTranscripts whether to save transcripts after each test
 * @param cleanupWorkspaces whether to clean up temporary workspaces after tests
 * @param defaultTimeoutSeconds default timeout for test execution
 */
public record TestHarnessConfig(
    List<String> cliCommand,
    Path useCasesDir,
    Path transcriptsDir,
    boolean saveTranscripts,
    boolean cleanupWorkspaces,
    int defaultTimeoutSeconds
) {

    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    public TestHarnessConfig {
        if (cliCommand == null || cliCommand.isEmpty()) {
            throw new IllegalArgumentException("cliCommand is required");
        }
        cliCommand = List.copyOf(cliCommand);
        defaultTimeoutSeconds = defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> cliCommand;
        private Path useCasesDir;
        private Path transcriptsDir;
        private boolean saveTranscripts = true;
        private boolean cleanupWorkspaces = true;
        private int defaultTimeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

        public Builder cliCommand(List<String> cliCommand) {
            this.cliCommand = cliCommand;
            return this;
        }

        public Builder cliCommand(String... cliCommand) {
            this.cliCommand = List.of(cliCommand);
            return this;
        }

        public Builder useCasesDir(Path useCasesDir) {
            this.useCasesDir = useCasesDir;
            return this;
        }

        public Builder transcriptsDir(Path transcriptsDir) {
            this.transcriptsDir = transcriptsDir;
            return this;
        }

        public Builder saveTranscripts(boolean saveTranscripts) {
            this.saveTranscripts = saveTranscripts;
            return this;
        }

        public Builder cleanupWorkspaces(boolean cleanupWorkspaces) {
            this.cleanupWorkspaces = cleanupWorkspaces;
            return this;
        }

        public Builder defaultTimeoutSeconds(int defaultTimeoutSeconds) {
            this.defaultTimeoutSeconds = defaultTimeoutSeconds;
            return this;
        }

        public TestHarnessConfig build() {
            return new TestHarnessConfig(
                    cliCommand,
                    useCasesDir,
                    transcriptsDir,
                    saveTranscripts,
                    cleanupWorkspaces,
                    defaultTimeoutSeconds
            );
        }
    }

}
