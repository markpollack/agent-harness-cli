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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agents.harness.test.executor.ExecutionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for LocalSandbox.
 */
class LocalSandboxTest {

    @TempDir
    Path tempDir;

    private LocalSandbox sandbox;

    @AfterEach
    void tearDown() {
        if (sandbox != null) {
            sandbox.close();
        }
    }

    @Test
    void startCreatesWorkspace() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        assertThat(sandbox.isRunning()).isTrue();
        assertThat(sandbox.workspace()).isNotNull();
        assertThat(Files.exists(sandbox.workspace())).isTrue();
    }

    @Test
    void startWithExistingWorkspace() throws IOException {
        Path existingDir = tempDir.resolve("existing");
        Files.createDirectories(existingDir);

        sandbox = new LocalSandbox(existingDir, false);
        sandbox.start();

        assertThat(sandbox.isRunning()).isTrue();
        assertThat(sandbox.workspace()).isEqualTo(existingDir);
    }

    @Test
    void execRunsCommand() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        ExecutionResult result = sandbox.exec("echo hello", Duration.ofSeconds(5));

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("hello");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void execReturnsExitCode() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        ExecutionResult result = sandbox.exec("exit 42", Duration.ofSeconds(5));

        assertThat(result.exitCode()).isEqualTo(42);
    }

    @Test
    void execTimesOut() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        ExecutionResult result = sandbox.exec("sleep 10", Duration.ofMillis(100));

        assertThat(result.timedOut()).isTrue();
    }

    @Test
    void execThrowsWhenNotRunning() {
        sandbox = new LocalSandbox();

        assertThatThrownBy(() -> sandbox.exec("echo test", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    @Test
    void uploadFile() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        Path sourceFile = tempDir.resolve("source.txt");
        Files.writeString(sourceFile, "test content");

        sandbox.uploadFile(sourceFile, Path.of("target.txt"));

        Path targetPath = sandbox.workspace().resolve("target.txt");
        assertThat(Files.exists(targetPath)).isTrue();
        assertThat(Files.readString(targetPath)).isEqualTo("test content");
    }

    @Test
    void downloadFile() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        Path sandboxFile = sandbox.workspace().resolve("sandbox-file.txt");
        Files.writeString(sandboxFile, "sandbox content");

        Path targetFile = tempDir.resolve("downloaded.txt");
        sandbox.downloadFile(Path.of("sandbox-file.txt"), targetFile);

        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.readString(targetFile)).isEqualTo("sandbox content");
    }

    @Test
    void uploadDirectory() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        Path sourceDir = tempDir.resolve("source-dir");
        Files.createDirectories(sourceDir.resolve("subdir"));
        Files.writeString(sourceDir.resolve("file1.txt"), "content1");
        Files.writeString(sourceDir.resolve("subdir/file2.txt"), "content2");

        sandbox.uploadDirectory(sourceDir, Path.of("target-dir"));

        Path targetDir = sandbox.workspace().resolve("target-dir");
        assertThat(Files.exists(targetDir.resolve("file1.txt"))).isTrue();
        assertThat(Files.exists(targetDir.resolve("subdir/file2.txt"))).isTrue();
        assertThat(Files.readString(targetDir.resolve("file1.txt"))).isEqualTo("content1");
    }

    @Test
    void downloadDirectory() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        Path sandboxDir = sandbox.workspace().resolve("sandbox-dir/nested");
        Files.createDirectories(sandboxDir);
        Files.writeString(sandbox.workspace().resolve("sandbox-dir/a.txt"), "aaa");
        Files.writeString(sandboxDir.resolve("b.txt"), "bbb");

        Path targetDir = tempDir.resolve("downloaded-dir");
        sandbox.downloadDirectory(Path.of("sandbox-dir"), targetDir);

        assertThat(Files.exists(targetDir.resolve("a.txt"))).isTrue();
        assertThat(Files.exists(targetDir.resolve("nested/b.txt"))).isTrue();
    }

    @Test
    void closeDeletesWorkspaceByDefault() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();
        Path workspace = sandbox.workspace();

        assertThat(Files.exists(workspace)).isTrue();

        sandbox.close();

        assertThat(Files.exists(workspace)).isFalse();
    }

    @Test
    void closePreservesWorkspaceWhenConfigured() throws IOException {
        sandbox = LocalSandbox.builder()
                .deleteOnClose(false)
                .build();
        sandbox.start();
        Path workspace = sandbox.workspace();

        sandbox.close();

        assertThat(Files.exists(workspace)).isTrue();

        // Manual cleanup
        Files.delete(workspace);
    }

    @Test
    void builderWithCustomName() throws IOException {
        sandbox = LocalSandbox.builder()
                .name("custom-sandbox")
                .build();
        sandbox.start();

        assertThat(sandbox.workspace().getFileName().toString()).startsWith("custom-sandbox");
    }

    @Test
    void builderWithExistingWorkspace() throws IOException {
        Path existingDir = tempDir.resolve("my-workspace");
        Files.createDirectories(existingDir);

        sandbox = LocalSandbox.builder()
                .workspace(existingDir)
                .deleteOnClose(false)
                .build();
        sandbox.start();

        assertThat(sandbox.workspace()).isEqualTo(existingDir);
    }

    @Test
    void stopMarksAsNotRunning() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        assertThat(sandbox.isRunning()).isTrue();

        sandbox.stop();

        assertThat(sandbox.isRunning()).isFalse();
    }

    @Test
    void execInWorkingDirectory() throws IOException {
        sandbox = new LocalSandbox();
        sandbox.start();

        Path subdir = sandbox.workspace().resolve("subdir");
        Files.createDirectories(subdir);
        Files.writeString(subdir.resolve("test.txt"), "in subdir");

        ExecutionResult result = sandbox.exec("cat test.txt", Path.of("subdir"), Duration.ofSeconds(5));

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("in subdir");
    }

}
