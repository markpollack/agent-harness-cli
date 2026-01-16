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

package org.springaicommunity.agents.harness.test.sandbox;

import org.springaicommunity.agents.harness.test.executor.ExecutionResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Abstraction for isolated execution environments.
 *
 * <p>Mirrors Harbor's BaseEnvironment interface for compatibility with Terminal-Bench.
 * This allows testing agents in various isolation levels:
 * <ul>
 *   <li>{@link LocalSandbox} - No isolation, uses local filesystem (fast, for bootstrapping)</li>
 *   <li>DockerSandbox (future) - Docker container isolation</li>
 *   <li>CloudSandbox (future) - Remote execution via Daytona, Modal, etc.</li>
 * </ul>
 *
 * <p>Design inspired by Harbor framework (github.com/laude-institute/harbor):
 * <pre>
 * class BaseEnvironment(ABC):
 *     async def start(self, force_build: bool) -> None
 *     async def stop(self, delete: bool)
 *     async def exec(self, command: str, cwd: str, env: dict, timeout_sec: int) -> ExecResult
 *     async def upload_file(self, source_path: Path, target_path: str)
 *     async def download_file(self, source_path: str, target_path: Path)
 * </pre>
 *
 * @see <a href="https://github.com/laude-institute/harbor">Harbor Framework</a>
 * @see <a href="https://www.tbench.ai/">Terminal-Bench</a>
 */
public interface Sandbox extends AutoCloseable {

    /**
     * Get the workspace directory for this sandbox.
     *
     * @return path to the workspace directory
     */
    Path workspace();

    /**
     * Start the sandbox environment.
     *
     * <p>For LocalSandbox, this creates the workspace directory.
     * For Docker-based sandboxes, this would start the container.
     *
     * @throws IOException if the sandbox cannot be started
     */
    void start() throws IOException;

    /**
     * Stop the sandbox environment.
     *
     * <p>For LocalSandbox, this is a no-op (cleanup happens in close()).
     * For Docker-based sandboxes, this would stop the container.
     */
    void stop();

    /**
     * Execute a command in the sandbox.
     *
     * @param command the command to execute
     * @param timeout maximum execution time
     * @return execution result with stdout, stderr, exit code
     * @throws IOException if execution fails
     */
    ExecutionResult exec(String command, Duration timeout) throws IOException;

    /**
     * Execute a command in a specific working directory within the sandbox.
     *
     * @param command the command to execute
     * @param workingDir working directory relative to sandbox workspace
     * @param timeout maximum execution time
     * @return execution result
     * @throws IOException if execution fails
     */
    ExecutionResult exec(String command, Path workingDir, Duration timeout) throws IOException;

    /**
     * Upload a file from local filesystem to the sandbox.
     *
     * @param source local source path
     * @param target target path within sandbox (relative to workspace)
     * @throws IOException if upload fails
     */
    void uploadFile(Path source, Path target) throws IOException;

    /**
     * Download a file from the sandbox to local filesystem.
     *
     * @param source source path within sandbox (relative to workspace)
     * @param target local target path
     * @throws IOException if download fails
     */
    void downloadFile(Path source, Path target) throws IOException;

    /**
     * Upload a directory from local filesystem to the sandbox.
     *
     * @param source local source directory
     * @param target target directory within sandbox (relative to workspace)
     * @throws IOException if upload fails
     */
    void uploadDirectory(Path source, Path target) throws IOException;

    /**
     * Download a directory from the sandbox to local filesystem.
     *
     * @param source source directory within sandbox (relative to workspace)
     * @param target local target directory
     * @throws IOException if download fails
     */
    void downloadDirectory(Path source, Path target) throws IOException;

    /**
     * Check if the sandbox is currently running.
     *
     * @return true if started and not stopped
     */
    boolean isRunning();

    /**
     * Clean up and close the sandbox.
     *
     * <p>This stops the sandbox if running and optionally deletes the workspace.
     */
    @Override
    void close();

}
