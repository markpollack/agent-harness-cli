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
 * Parameterized comparison tests for advanced use cases.
 *
 * <p>Runs MiniAgent vs Claude Code for each advanced test covering:
 * <ul>
 *   <li>Multi-file refactoring with test preservation</li>
 *   <li>Implementation from specifications</li>
 *   <li>Debugging complex issues (race conditions, log analysis)</li>
 *   <li>Security vulnerability detection and fixes</li>
 *   <li>API design and implementation</li>
 *   <li>Configuration parsing edge cases</li>
 * </ul>
 *
 * <p>Advanced tests are inspired by terminal-bench software-engineering tasks,
 * targeting expert time of 7-15 minutes to allow iterative development.</p>
 *
 * <p>Generates a summary report in plans/learnings/ after all tests complete.</p>
 *
 * <p>Run with: {@code ./mvnw verify -pl harness-test -Dit.test=AdvancedComparisonIT}</p>
 *
 * <p>Run single test: {@code ./mvnw verify -pl harness-test -Dit.test="AdvancedComparisonIT#compareAgents[31-multi-file-refactor-with-tests]"}</p>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AdvancedComparisonIT extends AbstractComparisonIT {

    private static final String SUITE_NAME = "Advanced";
    private static final Path ADVANCED_DIR = USE_CASES_DIR.resolve("advanced");
    private static final int MAX_TURNS = 25;  // Advanced tests need more turns
    private static final int TIMEOUT_SECONDS = 2400;  // 40 minute timeout per test (each test ~15 min max)

    @BeforeAll
    static void setUp() {
        initRunner(SUITE_NAME, MAX_TURNS, TIMEOUT_SECONDS);
    }

    @AfterAll
    static void tearDown() {
        generateAndSaveSummary(SUITE_NAME, LEARNINGS_DIR);
    }

    static Stream<String> advancedUseCases() throws Exception {
        return findUseCases(ADVANCED_DIR);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("advancedUseCases")
    @DisplayName("Compare MiniAgent vs Claude Code (Advanced)")
    void compareAgents(String useCaseName) throws Exception {
        runComparison(useCaseName, ADVANCED_DIR);
    }
}
