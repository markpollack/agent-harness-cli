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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.usecase.SetupFile;
import org.springaicommunity.agents.harness.test.usecase.UseCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages test workspace setup and cleanup.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create workspace directories</li>
 *   <li>Create setup files within workspace</li>
 *   <li>Handle timestamp placeholders in paths</li>
 *   <li>Clean up temporary workspaces</li>
 * </ul>
 */
public class WorkspaceManager {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);
    private static final String TIMESTAMP_PLACEHOLDER = "{{timestamp}}";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String tempPrefix;

    /**
     * Create a workspace manager with default settings.
     */
    public WorkspaceManager() {
        this("ai-test-");
    }

    /**
     * Create a workspace manager with custom temp directory prefix.
     *
     * @param tempPrefix prefix for temp directories
     */
    public WorkspaceManager(String tempPrefix) {
        this.tempPrefix = tempPrefix != null ? tempPrefix : "ai-test-";
    }

    /**
     * Setup a workspace for the given use case.
     *
     * @param useCase the use case to setup workspace for
     * @return workspace context with paths and cleanup info
     * @throws IOException if workspace cannot be created
     */
    public WorkspaceContext setup(UseCase useCase) throws IOException {
        Path workspacePath = resolveWorkspacePath(useCase);
        boolean isTemp = isTemporaryWorkspace(workspacePath);

        Files.createDirectories(workspacePath);
        logger.debug("Created workspace: {}", workspacePath);

        List<Path> createdFiles = createSetupFiles(workspacePath, useCase);

        return new WorkspaceContext(workspacePath, isTemp, createdFiles, useCase);
    }

    /**
     * Clean up a workspace after test execution.
     *
     * @param context the workspace context to clean up
     */
    public void cleanup(WorkspaceContext context) {
        if (context == null || !context.shouldCleanup()) {
            return;
        }

        logger.debug("Cleaning up workspace: {}", context.workspacePath());
        deleteDirectory(context.workspacePath());
    }

    /**
     * Resolve the workspace path from use case configuration.
     */
    Path resolveWorkspacePath(UseCase useCase) throws IOException {
        if (useCase.setup() == null || useCase.setup().workspace() == null) {
            return Files.createTempDirectory(tempPrefix);
        }

        String workspacePath = useCase.setup().workspace();

        // Replace timestamp placeholder
        if (workspacePath.contains(TIMESTAMP_PLACEHOLDER)) {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            workspacePath = workspacePath.replace(TIMESTAMP_PLACEHOLDER, timestamp);
        }

        return Path.of(workspacePath);
    }

    /**
     * Check if a path represents a temporary workspace.
     * Only considers paths that START with /tmp/ to be temporary.
     */
    boolean isTemporaryWorkspace(Path path) {
        String pathStr = path.toAbsolutePath().toString();
        return pathStr.startsWith("/tmp/");
    }

    /**
     * Create all setup files in the workspace.
     */
    List<Path> createSetupFiles(Path workspace, UseCase useCase) throws IOException {
        List<Path> created = new ArrayList<>();

        if (useCase.setup() == null || useCase.setup().files() == null) {
            return created;
        }

        for (SetupFile file : useCase.setup().files()) {
            Path filePath = workspace.resolve(file.path());

            // Create parent directories
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }

            Files.writeString(filePath, file.content());
            created.add(filePath);
            logger.debug("Created file: {}", filePath);
        }

        return created;
    }

    /**
     * Delete a directory and all its contents.
     */
    void deleteDirectory(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return;
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            logger.warn("Failed to clean up workspace: {}", directory, e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            logger.trace("Failed to delete: {}", path, e);
        }
    }

}
