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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.executor.ExecutionResult;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Local filesystem sandbox with no isolation.
 *
 * <p>Uses temporary directories for workspace isolation. Suitable for:
 * <ul>
 *   <li>Fast bootstrapping and development iteration</li>
 *   <li>Unit tests without Docker overhead</li>
 *   <li>CI environments where Docker may not be available</li>
 * </ul>
 *
 * <p>For production benchmarking with proper isolation, use DockerSandbox (future).
 */
public class LocalSandbox implements Sandbox {

    private static final Logger logger = LoggerFactory.getLogger(LocalSandbox.class);

    private final String name;
    private final boolean deleteOnClose;
    private Path workspace;
    private boolean running;

    /**
     * Create a local sandbox with a generated name.
     */
    public LocalSandbox() {
        this("sandbox", true);
    }

    /**
     * Create a local sandbox with the given name.
     *
     * @param name sandbox name (used as directory prefix)
     * @param deleteOnClose whether to delete workspace on close
     */
    public LocalSandbox(String name, boolean deleteOnClose) {
        this.name = name;
        this.deleteOnClose = deleteOnClose;
        this.running = false;
    }

    /**
     * Create a local sandbox using an existing directory.
     *
     * @param existingWorkspace path to existing workspace directory
     * @param deleteOnClose whether to delete workspace on close
     */
    public LocalSandbox(Path existingWorkspace, boolean deleteOnClose) {
        this.name = existingWorkspace.getFileName().toString();
        this.workspace = existingWorkspace;
        this.deleteOnClose = deleteOnClose;
        this.running = Files.exists(existingWorkspace);
    }

    @Override
    public Path workspace() {
        return workspace;
    }

    @Override
    public void start() throws IOException {
        if (running) {
            logger.debug("Sandbox already running: {}", workspace);
            return;
        }

        if (workspace == null) {
            workspace = Files.createTempDirectory(name + "-");
            logger.debug("Created workspace: {}", workspace);
        } else if (!Files.exists(workspace)) {
            Files.createDirectories(workspace);
            logger.debug("Created workspace at existing path: {}", workspace);
        }

        running = true;
    }

    @Override
    public void stop() {
        running = false;
        logger.debug("Sandbox stopped: {}", workspace);
    }

    @Override
    public ExecutionResult exec(String command, Duration timeout) throws IOException {
        return exec(command, workspace, timeout);
    }

    @Override
    public ExecutionResult exec(String command, Path workingDir, Duration timeout) throws IOException {
        if (!running) {
            throw new IllegalStateException("Sandbox not running");
        }

        Path cwd = workingDir.isAbsolute() ? workingDir : workspace.resolve(workingDir);

        try {
            long startTime = System.currentTimeMillis();

            ProcessResult result = new ProcessExecutor()
                    .command("/bin/sh", "-c", command)
                    .directory(cwd.toFile())
                    .readOutput(true)
                    .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .execute();

            long durationMs = System.currentTimeMillis() - startTime;

            return new ExecutionResult(
                    result.outputUTF8(),
                    result.getExitValue(),
                    durationMs,
                    false
            );

        } catch (TimeoutException e) {
            return ExecutionResult.timeout("Command timed out: " + command, timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command interrupted", e);
        }
    }

    @Override
    public void uploadFile(Path source, Path target) throws IOException {
        if (!running) {
            throw new IllegalStateException("Sandbox not running");
        }

        Path targetPath = target.isAbsolute() ? target : workspace.resolve(target);
        Files.createDirectories(targetPath.getParent());
        Files.copy(source, targetPath, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Uploaded file: {} -> {}", source, targetPath);
    }

    @Override
    public void downloadFile(Path source, Path target) throws IOException {
        if (!running) {
            throw new IllegalStateException("Sandbox not running");
        }

        Path sourcePath = source.isAbsolute() ? source : workspace.resolve(source);
        Files.createDirectories(target.getParent());
        Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Downloaded file: {} -> {}", sourcePath, target);
    }

    @Override
    public void uploadDirectory(Path source, Path target) throws IOException {
        if (!running) {
            throw new IllegalStateException("Sandbox not running");
        }

        Path targetPath = target.isAbsolute() ? target : workspace.resolve(target);
        copyDirectory(source, targetPath);
        logger.debug("Uploaded directory: {} -> {}", source, targetPath);
    }

    @Override
    public void downloadDirectory(Path source, Path target) throws IOException {
        if (!running) {
            throw new IllegalStateException("Sandbox not running");
        }

        Path sourcePath = source.isAbsolute() ? source : workspace.resolve(source);
        copyDirectory(sourcePath, target);
        logger.debug("Downloaded directory: {} -> {}", sourcePath, target);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();

        if (deleteOnClose && workspace != null && Files.exists(workspace)) {
            try {
                deleteDirectory(workspace);
                logger.debug("Deleted workspace: {}", workspace);
            } catch (IOException e) {
                logger.warn("Failed to delete workspace: {}", workspace, e);
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Builder for LocalSandbox.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "sandbox";
        private boolean deleteOnClose = true;
        private Path existingWorkspace;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder deleteOnClose(boolean deleteOnClose) {
            this.deleteOnClose = deleteOnClose;
            return this;
        }

        public Builder workspace(Path workspace) {
            this.existingWorkspace = workspace;
            return this;
        }

        public LocalSandbox build() {
            if (existingWorkspace != null) {
                return new LocalSandbox(existingWorkspace, deleteOnClose);
            }
            return new LocalSandbox(name, deleteOnClose);
        }
    }

}
