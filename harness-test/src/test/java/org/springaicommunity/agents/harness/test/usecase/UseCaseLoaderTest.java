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

package org.springaicommunity.agents.harness.test.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for UseCaseLoader.
 */
class UseCaseLoaderTest {

    private UseCaseLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new UseCaseLoader();
    }

    @Test
    void parseMinimalYaml() throws IOException {
        String yaml = """
            name: Test Case
            prompt: Do something
            """;

        UseCase useCase = loader.parse(yaml);

        assertThat(useCase.name()).isEqualTo("Test Case");
        assertThat(useCase.prompt()).isEqualTo("Do something");
        assertThat(useCase.maxTurns()).isEqualTo(UseCase.DEFAULT_MAX_TURNS);
        assertThat(useCase.timeoutSeconds()).isEqualTo(UseCase.DEFAULT_TIMEOUT_SECONDS);
    }

    @Test
    void parseFullYaml() throws IOException {
        String yaml = """
            name: Fix Bug
            category: bug-fix
            difficulty: easy
            requiresApi: true
            setup:
              workspace: /tmp/test
              files:
                - path: src/Main.java
                  content: "public class Main {}"
            prompt: Fix the bug
            questionStrategy:
              defaultStrategy: first
              overrides:
                approach: defensive
            expectedBehavior: Agent should fix the bug
            successCriteria:
              - type: file_contains
                args: ["src/Main.java", "== null"]
            maxTurns: 5
            timeoutSeconds: 60
            """;

        UseCase useCase = loader.parse(yaml);

        assertThat(useCase.name()).isEqualTo("Fix Bug");
        assertThat(useCase.category()).isEqualTo("bug-fix");
        assertThat(useCase.difficulty()).isEqualTo("easy");
        assertThat(useCase.requiresApi()).isTrue();
        assertThat(useCase.setup()).isNotNull();
        assertThat(useCase.setup().workspace()).isEqualTo("/tmp/test");
        assertThat(useCase.setup().files()).hasSize(1);
        assertThat(useCase.prompt()).isEqualTo("Fix the bug");
        assertThat(useCase.questionStrategy()).isNotNull();
        assertThat(useCase.questionStrategy().defaultStrategy()).isEqualTo("first");
        assertThat(useCase.expectedBehavior()).isEqualTo("Agent should fix the bug");
        assertThat(useCase.successCriteria()).hasSize(1);
        assertThat(useCase.successCriteria().get(0).type()).isEqualTo("file_contains");
        assertThat(useCase.maxTurns()).isEqualTo(5);
        assertThat(useCase.timeoutSeconds()).isEqualTo(60);
    }

    @Test
    void loadFromFile() throws IOException {
        String yaml = """
            name: File Test
            prompt: Test prompt
            """;
        Path yamlPath = tempDir.resolve("test.yaml");
        Files.writeString(yamlPath, yaml);

        UseCase useCase = loader.load(yamlPath);

        assertThat(useCase.name()).isEqualTo("File Test");
    }

    @Test
    void loadThrowsOnMissingFile() {
        Path missingPath = tempDir.resolve("missing.yaml");

        assertThatThrownBy(() -> loader.load(missingPath))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void parseThrowsOnMissingName() {
        String yaml = """
            prompt: Do something
            """;

        assertThatThrownBy(() -> loader.parse(yaml))
                .isInstanceOf(Exception.class);
    }

    @Test
    void parseThrowsOnMissingPrompt() {
        String yaml = """
            name: Test
            """;

        assertThatThrownBy(() -> loader.parse(yaml))
                .isInstanceOf(Exception.class);
    }

    @Test
    void findUseCasesInDirectory() throws IOException {
        // Create test files
        Path basic = tempDir.resolve("basic");
        Files.createDirectories(basic);
        Files.writeString(basic.resolve("test1.yaml"), "name: T1\nprompt: P1");
        Files.writeString(basic.resolve("test2.yml"), "name: T2\nprompt: P2");
        Files.writeString(basic.resolve("_template.yaml"), "name: Template\nprompt: P");

        List<Path> found = loader.findUseCases(tempDir);

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(p -> !p.getFileName().toString().startsWith("_"));
    }

    @Test
    void findUseCasesReturnsEmptyForMissingDirectory() throws IOException {
        Path missingDir = tempDir.resolve("missing");

        List<Path> found = loader.findUseCases(missingDir);

        assertThat(found).isEmpty();
    }

    @Test
    void findUseCasesByCategory() throws IOException {
        Path bugfix = tempDir.resolve("bug-fix");
        Files.createDirectories(bugfix);
        Files.writeString(bugfix.resolve("bug1.yaml"), "name: Bug1\nprompt: P1");

        Path basic = tempDir.resolve("basic");
        Files.createDirectories(basic);
        Files.writeString(basic.resolve("basic1.yaml"), "name: Basic1\nprompt: P1");

        List<Path> bugfixCases = loader.findUseCasesByCategory(tempDir, "bug-fix");
        List<Path> basicCases = loader.findUseCasesByCategory(tempDir, "basic");

        assertThat(bugfixCases).hasSize(1);
        assertThat(basicCases).hasSize(1);
    }

    @Test
    void loadAllUseCases() throws IOException {
        Path dir = tempDir.resolve("cases");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("case1.yaml"), "name: Case1\nprompt: P1");
        Files.writeString(dir.resolve("case2.yaml"), "name: Case2\nprompt: P2");

        List<UseCase> useCases = loader.loadAll(dir);

        assertThat(useCases).hasSize(2);
        assertThat(useCases).extracting(UseCase::name)
                .containsExactlyInAnyOrder("Case1", "Case2");
    }

}
