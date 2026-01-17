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
import org.springaicommunity.sandbox.LocalSandbox;
import org.springaicommunity.sandbox.Sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages test workspace setup and cleanup using the Sandbox API.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create workspace directories via {@link LocalSandbox}</li>
 *   <li>Create setup files within workspace using {@link org.springaicommunity.sandbox.SandboxFiles}</li>
 *   <li>Handle timestamp placeholders in paths</li>
 *   <li>Clean up temporary workspaces via {@link Sandbox#close()}</li>
 * </ul>
 *
 * <p>The underlying {@link Sandbox} is stored in the {@link WorkspaceContext} and
 * closed during {@link #cleanup(WorkspaceContext)}.</p>
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
     * <p>Creates a {@link LocalSandbox} with either a temporary directory (auto-cleanup)
     * or a specified workspace path (no auto-cleanup).</p>
     *
     * @param useCase the use case to setup workspace for
     * @return workspace context with sandbox and cleanup info
     * @throws IOException if workspace cannot be created
     */
    public WorkspaceContext setup(UseCase useCase) throws IOException {
        LocalSandbox sandbox = createSandbox(useCase);

        // Ensure the workspace directory exists
        Files.createDirectories(sandbox.workDir());
        logger.debug("Created workspace: {}", sandbox.workDir());

        List<Path> createdFiles = createSetupFiles(sandbox, useCase);

        return new WorkspaceContext(sandbox, createdFiles, useCase);
    }

    /**
     * Clean up a workspace after test execution.
     *
     * <p>Closes the underlying {@link Sandbox}, which will delete the working
     * directory if it was created as a temp directory.</p>
     *
     * @param context the workspace context to clean up
     */
    public void cleanup(WorkspaceContext context) {
        if (context == null) {
            return;
        }

        Sandbox sandbox = context.sandbox();
        if (sandbox.isClosed()) {
            return;
        }

        logger.debug("Cleaning up workspace: {}", context.workspacePath());
        sandbox.close();
    }

    /**
     * Create a sandbox for the given use case.
     *
     * <p>If the use case specifies a workspace path, creates a sandbox with that
     * fixed directory (no auto-cleanup). Otherwise, creates a temp directory
     * sandbox (auto-cleanup on close).</p>
     */
    LocalSandbox createSandbox(UseCase useCase) throws IOException {
        Path workspacePath = resolveWorkspacePath(useCase);

        if (workspacePath != null) {
            // Fixed workspace path - no auto-cleanup
            return LocalSandbox.builder()
                    .workingDirectory(workspacePath)
                    .build();
        } else {
            // Temp directory - auto-cleanup on close
            return LocalSandbox.builder()
                    .tempDirectory(tempPrefix)
                    .build();
        }
    }

    /**
     * Resolve the workspace path from use case configuration.
     *
     * @return workspace path if specified, null for temp directory
     */
    Path resolveWorkspacePath(UseCase useCase) {
        if (useCase.setup() == null || useCase.setup().workspace() == null) {
            return null;
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
     * Create all setup files in the workspace using the Sandbox files API.
     */
    List<Path> createSetupFiles(Sandbox sandbox, UseCase useCase) {
        List<Path> created = new ArrayList<>();

        if (useCase.setup() == null || useCase.setup().files() == null) {
            return created;
        }

        for (SetupFile file : useCase.setup().files()) {
            sandbox.files().create(file.path(), file.content());
            created.add(sandbox.workDir().resolve(file.path()));
            logger.debug("Created file: {}", sandbox.workDir().resolve(file.path()));
        }

        return created;
    }

}
