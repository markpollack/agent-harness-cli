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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agents.harness.test.usecase.SuccessCriterion;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.judge.Judge;
import org.springaicommunity.judge.context.ExecutionStatus;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.jury.Jury;
import org.springaicommunity.judge.jury.Verdict;
import org.springaicommunity.judge.result.Judgment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JuryFactory.
 */
class JuryFactoryTest {

    private JuryFactory factory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        factory = new JuryFactory();
    }

    @Test
    void buildJuryWithFileExistsCriterion() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "content");

        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .successCriteria(List.of(SuccessCriterion.fileExists("test.txt")))
                .build();

        Jury jury = factory.buildJury(useCase, tempDir);
        JudgmentContext context = createContext("output");

        Verdict verdict = jury.vote(context);

        assertThat(verdict.aggregated().pass()).isTrue();
    }

    @Test
    void buildJuryWithFileContainsCriterion() throws IOException {
        Files.writeString(tempDir.resolve("code.java"), "if (s == null) return;");

        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .successCriteria(List.of(SuccessCriterion.fileContains("code.java", "== null")))
                .build();

        Jury jury = factory.buildJury(useCase, tempDir);
        JudgmentContext context = createContext("output");

        Verdict verdict = jury.vote(context);

        assertThat(verdict.aggregated().pass()).isTrue();
    }

    @Test
    void buildJuryWithNoExceptionsCriterion() {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .successCriteria(List.of(SuccessCriterion.noExceptions()))
                .build();

        Jury jury = factory.buildJury(useCase, tempDir);

        // Test with clean output
        JudgmentContext cleanContext = createContext("Task completed successfully");
        Verdict cleanVerdict = jury.vote(cleanContext);
        assertThat(cleanVerdict.aggregated().pass()).isTrue();

        // Test with exception output
        JudgmentContext exceptionContext = createContext(
                "Exception in thread main java.lang.NullPointerException at Main.main(Main.java:5)");
        Verdict exceptionVerdict = jury.vote(exceptionContext);
        assertThat(exceptionVerdict.aggregated().pass()).isFalse();
    }

    @Test
    void buildJuryWithOutputContainsCriterion() {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .successCriteria(List.of(SuccessCriterion.outputContains("success")))
                .build();

        Jury jury = factory.buildJury(useCase, tempDir);

        // Test with matching output
        JudgmentContext matchContext = createContext("Task completed with success");
        Verdict matchVerdict = jury.vote(matchContext);
        assertThat(matchVerdict.aggregated().pass()).isTrue();

        // Test with non-matching output
        JudgmentContext noMatchContext = createContext("Task failed");
        Verdict noMatchVerdict = jury.vote(noMatchContext);
        assertThat(noMatchVerdict.aggregated().pass()).isFalse();
    }

    @Test
    void buildJuryWithNoCriteria() {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .build();

        Jury jury = factory.buildJury(useCase, tempDir);
        JudgmentContext context = createContext("output");

        Verdict verdict = jury.vote(context);

        // Should pass by default when no criteria specified
        assertThat(verdict.aggregated().pass()).isTrue();
    }

    @Test
    void buildJuryWithMultipleCriteria() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "expected content");

        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .successCriteria(List.of(
                        SuccessCriterion.fileExists("test.txt"),
                        SuccessCriterion.fileContains("test.txt", "expected"),
                        SuccessCriterion.noExceptions()
                ))
                .build();

        Jury jury = factory.buildJury(useCase, tempDir);
        JudgmentContext context = createContext("Task completed");

        Verdict verdict = jury.vote(context);

        assertThat(verdict.aggregated().pass()).isTrue();
        assertThat(verdict.individual()).hasSize(3);
    }

    @Test
    void createJudgeReturnsNullForUnknownType() {
        SuccessCriterion unknown = new SuccessCriterion("unknown_type", List.of());

        Judge judge = factory.createJudge(unknown, tempDir);

        assertThat(judge).isNull();
    }

    @Test
    void calculateWeightReturnsExpectedValues() {
        assertThat(factory.calculateWeight(SuccessCriterion.fileContains("a", "b"))).isEqualTo(0.3);
        assertThat(factory.calculateWeight(SuccessCriterion.fileExists("a"))).isEqualTo(0.2);
        assertThat(factory.calculateWeight(SuccessCriterion.noExceptions())).isEqualTo(0.2);
        assertThat(factory.calculateWeight(SuccessCriterion.outputContains("a"))).isEqualTo(0.2);
    }

    private JudgmentContext createContext(String output) {
        return JudgmentContext.builder()
                .goal("Test goal")
                .workspace(tempDir)
                .executionTime(Duration.ofSeconds(1))
                .startedAt(Instant.now())
                .agentOutput(output)
                .status(ExecutionStatus.SUCCESS)
                .build();
    }

}
