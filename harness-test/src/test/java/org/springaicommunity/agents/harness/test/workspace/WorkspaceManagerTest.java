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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agents.harness.test.usecase.Setup;
import org.springaicommunity.agents.harness.test.usecase.SetupFile;
import org.springaicommunity.agents.harness.test.usecase.UseCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for WorkspaceManager.
 */
class WorkspaceManagerTest {

    private WorkspaceManager manager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        manager = new WorkspaceManager("test-");
    }

    @Test
    void setupCreatesWorkspaceDirectory() throws IOException {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .build();

        WorkspaceContext context = manager.setup(useCase);

        assertThat(context.workspacePath()).exists();
        assertThat(context.isTemp()).isTrue();
    }

    @Test
    void setupCreatesConfiguredWorkspace() throws IOException {
        Path workspacePath = tempDir.resolve("custom-workspace");
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .setup(new Setup(workspacePath.toString(), null))
                .build();

        WorkspaceContext context = manager.setup(useCase);

        assertThat(context.workspacePath()).isEqualTo(workspacePath);
        assertThat(context.workspacePath()).exists();
        // Note: isTemp may be true since JUnit TempDir uses /tmp
    }

    @Test
    void setupCreatesSetupFiles() throws IOException {
        Path workspacePath = tempDir.resolve("files-workspace");
        List<SetupFile> files = List.of(
                new SetupFile("src/Main.java", "public class Main {}"),
                new SetupFile("config/app.yaml", "key: value")
        );
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .setup(new Setup(workspacePath.toString(), files))
                .build();

        WorkspaceContext context = manager.setup(useCase);

        assertThat(context.createdFiles()).hasSize(2);
        assertThat(workspacePath.resolve("src/Main.java")).exists();
        assertThat(workspacePath.resolve("config/app.yaml")).exists();
        assertThat(Files.readString(workspacePath.resolve("src/Main.java")))
                .isEqualTo("public class Main {}");
    }

    @Test
    void setupReplacesTimestampPlaceholder() throws IOException {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .setup(new Setup("/tmp/test-{{timestamp}}", null))
                .build();

        Path resolved = manager.resolveWorkspacePath(useCase);

        assertThat(resolved.toString())
                .startsWith("/tmp/test-")
                .doesNotContain("{{timestamp}}")
                .matches("/tmp/test-\\d{8}-\\d{6}");
    }

    @Test
    void cleanupRemovesTemporaryWorkspace() throws IOException {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .build();

        WorkspaceContext context = manager.setup(useCase);
        Path workspacePath = context.workspacePath();

        // Create some files
        Files.writeString(workspacePath.resolve("test.txt"), "content");

        manager.cleanup(context);

        assertThat(workspacePath).doesNotExist();
    }

    @Test
    void cleanupRemovesWorkspaceWhenIsTemp() throws IOException {
        // Create a workspace that's detected as temp
        Path workspacePath = Path.of("/tmp/test-cleanup-" + System.currentTimeMillis());
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .setup(new Setup(workspacePath.toString(), null))
                .build();

        WorkspaceContext context = manager.setup(useCase);
        assertThat(workspacePath).exists();

        manager.cleanup(context);

        // Should be removed because it's in /tmp
        assertThat(workspacePath).doesNotExist();
    }

    @Test
    void isTemporaryWorkspaceDetectsTmpPath() {
        assertThat(manager.isTemporaryWorkspace(Path.of("/tmp/test"))).isTrue();
        assertThat(manager.isTemporaryWorkspace(Path.of("/tmp/deep/nested/path"))).isTrue();
        assertThat(manager.isTemporaryWorkspace(Path.of("/var/tmp/test"))).isFalse();
        assertThat(manager.isTemporaryWorkspace(Path.of("/home/user/project"))).isFalse();
        assertThat(manager.isTemporaryWorkspace(Path.of("/home/user/mytmp/test"))).isFalse();
    }

    @Test
    void workspaceContextResolvesCombinesPaths() throws IOException {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .build();

        WorkspaceContext context = manager.setup(useCase);

        Path resolved = context.resolve("src/Main.java");

        assertThat(resolved).isEqualTo(context.workspacePath().resolve("src/Main.java"));
    }

    @Test
    void setupHandlesNestedDirectories() throws IOException {
        Path workspacePath = tempDir.resolve("nested-workspace");
        List<SetupFile> files = List.of(
                new SetupFile("a/b/c/deep.txt", "deep content")
        );
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .setup(new Setup(workspacePath.toString(), files))
                .build();

        WorkspaceContext context = manager.setup(useCase);

        Path deepFile = workspacePath.resolve("a/b/c/deep.txt");
        assertThat(deepFile).exists();
        assertThat(Files.readString(deepFile)).isEqualTo("deep content");
    }

}
