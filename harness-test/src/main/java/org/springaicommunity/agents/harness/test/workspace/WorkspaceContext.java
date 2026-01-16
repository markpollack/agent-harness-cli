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

package org.springaicommunity.agents.harness.test.workspace;

import org.springaicommunity.agents.harness.test.usecase.UseCase;

import java.nio.file.Path;
import java.util.List;

/**
 * Context representing a prepared test workspace.
 *
 * @param workspacePath absolute path to the workspace directory
 * @param isTemp whether this is a temporary workspace that should be cleaned up
 * @param createdFiles list of files created during setup
 * @param useCase the use case this workspace was created for
 */
public record WorkspaceContext(
    Path workspacePath,
    boolean isTemp,
    List<Path> createdFiles,
    UseCase useCase
) {

    public WorkspaceContext {
        if (workspacePath == null) {
            throw new IllegalArgumentException("workspacePath is required");
        }
        createdFiles = createdFiles != null ? List.copyOf(createdFiles) : List.of();
    }

    /**
     * Resolve a relative path within this workspace.
     *
     * @param relativePath path relative to workspace root
     * @return absolute path
     */
    public Path resolve(String relativePath) {
        return workspacePath.resolve(relativePath);
    }

    /**
     * Check if this workspace should be cleaned up after test.
     */
    public boolean shouldCleanup() {
        return isTemp;
    }

}
