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

package org.springaicommunity.agents.harness.test.validation;

import org.springaicommunity.agents.harness.test.executor.ExecutionResult;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceContext;

/**
 * Context for test validation containing all information needed for evaluation.
 *
 * @param useCase the use case being validated
 * @param workspace the workspace where the test ran
 * @param execution the execution result from running the CLI
 */
public record ValidationContext(
    UseCase useCase,
    WorkspaceContext workspace,
    ExecutionResult execution
) {

    public ValidationContext {
        if (useCase == null) {
            throw new IllegalArgumentException("useCase is required");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("workspace is required");
        }
        if (execution == null) {
            throw new IllegalArgumentException("execution is required");
        }
    }

    /**
     * Get the transcript (output) from execution.
     */
    public String transcript() {
        return execution.output();
    }

    /**
     * Get the expected behavior description.
     */
    public String expectedBehavior() {
        return useCase.expectedBehavior();
    }

}
