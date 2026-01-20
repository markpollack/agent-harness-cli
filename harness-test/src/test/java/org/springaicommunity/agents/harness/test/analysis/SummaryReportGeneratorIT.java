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

package org.springaicommunity.agents.harness.test.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for SummaryReportGenerator.
 *
 * <p>Run with: {@code mvn verify -pl harness-test -Dit.test=SummaryReportGeneratorIT}</p>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class SummaryReportGeneratorIT {

    private static final Path LEARNINGS_DIR = Path.of("../plans/learnings");

    @Test
    void shouldGenerateIntermediateSummary() throws Exception {
        // Given
        SummaryReportGenerator generator = new SummaryReportGenerator();

        // When
        String summary = generator.generate(LEARNINGS_DIR, "Intermediate");

        // Then
        System.out.println("=".repeat(80));
        System.out.println("GENERATED SUMMARY REPORT");
        System.out.println("=".repeat(80));
        System.out.println(summary);
        System.out.println("=".repeat(80));

        assertThat(summary).contains("Intermediate");
        assertThat(summary).contains("MiniAgent");
        assertThat(summary).contains("Claude");
    }

    @Test
    void shouldGenerateAndSaveIntermediateSummary() throws Exception {
        // Given
        SummaryReportGenerator generator = new SummaryReportGenerator();

        // When
        Path outputPath = generator.generateAndSave(LEARNINGS_DIR, "Intermediate-Claude-Generated");

        // Then
        assertThat(outputPath).exists();
        System.out.println("Summary saved to: " + outputPath.toAbsolutePath());
    }
}
