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

package org.springaicommunity.agents.harness.test.comparison;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Parameterized comparison tests for intermediate use cases.
 *
 * <p>Runs MiniAgent vs Claude Code for each intermediate test and validates both succeed.
 * Also captures behavioral patterns (verification, planning) for analysis.</p>
 *
 * <p>Generates a summary report in plans/learnings/ after all tests complete.</p>
 *
 * <p>Run with: {@code ./mvnw verify -pl harness-test -Dit.test=IntermediateComparisonIT}</p>
 *
 * <p>Run single test: {@code ./mvnw verify -pl harness-test -Dit.test="IntermediateComparisonIT#compareAgents[11-create-java-class]"}</p>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class IntermediateComparisonIT extends AbstractComparisonIT {

    private static final String SUITE_NAME = "Intermediate";
    private static final Path INTERMEDIATE_DIR = USE_CASES_DIR.resolve("intermediate");
    private static final int MAX_TURNS = 15;  // Intermediate tests may need more turns
    private static final int TIMEOUT_SECONDS = 180;  // Longer timeout

    @BeforeAll
    static void setUp() {
        initRunner(SUITE_NAME, MAX_TURNS, TIMEOUT_SECONDS);
    }

    @AfterAll
    static void tearDown() {
        generateAndSaveSummary(SUITE_NAME, LEARNINGS_DIR);
    }

    static Stream<String> intermediateUseCases() throws Exception {
        return findUseCases(INTERMEDIATE_DIR);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intermediateUseCases")
    @DisplayName("Compare MiniAgent vs Claude Code (Intermediate)")
    void compareAgents(String useCaseName) throws Exception {
        runComparison(useCaseName, INTERMEDIATE_DIR);
    }
}
