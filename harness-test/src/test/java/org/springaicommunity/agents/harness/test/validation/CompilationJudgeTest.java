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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationJudgeTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPassWhenNoJavaFiles() {
        // Given
        CompilationJudge judge = new CompilationJudge();
        JudgmentContext context = JudgmentContext.builder()
                .workspace(tempDir)
                .build();

        // When
        Judgment result = judge.judge(context);

        // Then
        assertThat(result.pass()).isTrue();
        assertThat(result.reasoning()).contains("No Java files");
    }

    @Test
    void shouldPassWhenValidJavaFile() throws Exception {
        // Given
        String validJava = """
            public class Hello {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            """;
        Files.writeString(tempDir.resolve("Hello.java"), validJava);

        CompilationJudge judge = new CompilationJudge();
        JudgmentContext context = JudgmentContext.builder()
                .workspace(tempDir)
                .build();

        // When
        Judgment result = judge.judge(context);

        // Then
        assertThat(result.pass()).isTrue();
        assertThat(result.reasoning()).contains("Compilation successful");
    }

    @Test
    void shouldFailWhenInvalidJavaFile() throws Exception {
        // Given - missing semicolon
        String invalidJava = """
            public class Broken {
                public static void main(String[] args) {
                    System.out.println("Missing semicolon")
                }
            }
            """;
        Files.writeString(tempDir.resolve("Broken.java"), invalidJava);

        CompilationJudge judge = new CompilationJudge();
        JudgmentContext context = JudgmentContext.builder()
                .workspace(tempDir)
                .build();

        // When
        Judgment result = judge.judge(context);

        // Then
        assertThat(result.pass()).isFalse();
        assertThat(result.reasoning()).contains("Compilation failed");
    }

    @Test
    void shouldPassWhenMultipleValidFiles() throws Exception {
        // Given
        String classA = """
            public class A {
                public String getValue() { return "A"; }
            }
            """;
        String classB = """
            public class B {
                public String getValue() { return new A().getValue(); }
            }
            """;
        Files.writeString(tempDir.resolve("A.java"), classA);
        Files.writeString(tempDir.resolve("B.java"), classB);

        CompilationJudge judge = new CompilationJudge();
        JudgmentContext context = JudgmentContext.builder()
                .workspace(tempDir)
                .build();

        // When
        Judgment result = judge.judge(context);

        // Then
        assertThat(result.pass()).isTrue();
        assertThat(result.reasoning()).contains("2 files compiled");
    }
}
