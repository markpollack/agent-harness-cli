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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for UseCase record.
 */
class UseCaseTest {

    @Test
    void builderCreatesValidUseCase() {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .category("basic")
                .difficulty("easy")
                .maxTurns(5)
                .timeoutSeconds(30)
                .requiresApi(true)
                .build();

        assertThat(useCase.name()).isEqualTo("Test");
        assertThat(useCase.prompt()).isEqualTo("Do something");
        assertThat(useCase.category()).isEqualTo("basic");
        assertThat(useCase.difficulty()).isEqualTo("easy");
        assertThat(useCase.maxTurns()).isEqualTo(5);
        assertThat(useCase.timeoutSeconds()).isEqualTo(30);
        assertThat(useCase.requiresApi()).isTrue();
    }

    @Test
    void builderAppliesDefaults() {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .build();

        assertThat(useCase.category()).isEqualTo("unknown");
        assertThat(useCase.difficulty()).isEqualTo("unknown");
        assertThat(useCase.maxTurns()).isEqualTo(UseCase.DEFAULT_MAX_TURNS);
        assertThat(useCase.timeoutSeconds()).isEqualTo(UseCase.DEFAULT_TIMEOUT_SECONDS);
        assertThat(useCase.successCriteria()).isEmpty();
    }

    @Test
    void constructorThrowsOnNullName() {
        assertThatThrownBy(() -> UseCase.builder()
                .prompt("Do something")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void constructorThrowsOnBlankName() {
        assertThatThrownBy(() -> UseCase.builder()
                .name("   ")
                .prompt("Do something")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void constructorThrowsOnNullPrompt() {
        assertThatThrownBy(() -> UseCase.builder()
                .name("Test")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
    }

    @Test
    void isInteractiveWhenQuestionStrategyPresent() {
        UseCase interactive = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .questionStrategy(QuestionStrategy.first())
                .build();

        UseCase nonInteractive = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .build();

        assertThat(interactive.isInteractive()).isTrue();
        assertThat(nonInteractive.isInteractive()).isFalse();
    }

    @Test
    void hasDeterministicCriteriaWhenPresent() {
        UseCase withCriteria = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .successCriteria(List.of(SuccessCriterion.noExceptions()))
                .build();

        UseCase withoutCriteria = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .build();

        assertThat(withCriteria.hasDeterministicCriteria()).isTrue();
        assertThat(withoutCriteria.hasDeterministicCriteria()).isFalse();
    }

    @Test
    void successCriteriaIsImmutable() {
        List<SuccessCriterion> criteria = List.of(SuccessCriterion.noExceptions());
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Do something")
                .successCriteria(criteria)
                .build();

        assertThatThrownBy(() -> useCase.successCriteria().add(SuccessCriterion.fileExists("test")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
