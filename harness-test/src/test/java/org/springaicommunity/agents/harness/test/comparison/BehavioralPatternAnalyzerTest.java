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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BehavioralPatternAnalyzerTest {

    private final BehavioralPatternAnalyzer analyzer = new BehavioralPatternAnalyzer();

    @Test
    void shouldDetectEndVerification() {
        // Sequence ending with bash (likely verification)
        List<String> withVerification = List.of("Read", "Edit", "bash");
        List<String> withoutVerification = List.of("Read", "Edit", "Write");

        assertThat(analyzer.hasEndVerification(withVerification)).isTrue();
        assertThat(analyzer.hasEndVerification(withoutVerification)).isFalse();
    }

    @Test
    void shouldDetectEndVerificationBeforeSubmit() {
        // bash followed by Submit
        List<String> sequence = List.of("Read", "Edit", "bash", "Submit");

        assertThat(analyzer.hasEndVerification(sequence)).isTrue();
    }

    @Test
    void shouldDetectEarlyVerification() {
        // bash in first third
        List<String> earlyBash = List.of("bash", "Read", "Read", "Edit", "Edit", "Write");
        List<String> lateBash = List.of("Read", "Read", "Edit", "Edit", "Write", "bash");

        assertThat(analyzer.hasEarlyVerification(earlyBash)).isTrue();
        assertThat(analyzer.hasEarlyVerification(lateBash)).isFalse();
    }

    @Test
    void shouldDetectPlanning() {
        List<String> withPlanning = List.of("TodoWrite", "Read", "Edit");
        List<String> withoutPlanning = List.of("Read", "Edit", "Write");

        assertThat(analyzer.startsWithPlanning(withPlanning)).isTrue();
        assertThat(analyzer.startsWithPlanning(withoutPlanning)).isFalse();
    }

    @Test
    void shouldClassifyToolPurpose() {
        // Exploration tools
        assertThat(analyzer.classifyToolPurpose("Read", null, 0, 5))
                .isEqualTo(BehavioralPatternAnalyzer.ToolPurpose.EXPLORATION);
        assertThat(analyzer.classifyToolPurpose("Glob", null, 0, 5))
                .isEqualTo(BehavioralPatternAnalyzer.ToolPurpose.EXPLORATION);

        // Modification tools
        assertThat(analyzer.classifyToolPurpose("Write", null, 2, 5))
                .isEqualTo(BehavioralPatternAnalyzer.ToolPurpose.MODIFICATION);
        assertThat(analyzer.classifyToolPurpose("Edit", null, 2, 5))
                .isEqualTo(BehavioralPatternAnalyzer.ToolPurpose.MODIFICATION);

        // Planning tools
        assertThat(analyzer.classifyToolPurpose("TodoWrite", null, 0, 5))
                .isEqualTo(BehavioralPatternAnalyzer.ToolPurpose.PLANNING);
    }

    @Test
    void shouldClassifyBashByPosition() {
        // Early bash with javac = exploration
        assertThat(analyzer.classifyToolPurpose("bash", "javac *.java", 0, 10))
                .isEqualTo(BehavioralPatternAnalyzer.ToolPurpose.EXPLORATION);

        // Late bash with javac = verification
        assertThat(analyzer.classifyToolPurpose("bash", "javac *.java", 8, 10))
                .isEqualTo(BehavioralPatternAnalyzer.ToolPurpose.VERIFICATION);
    }

    @Test
    void shouldHandleEmptySequences() {
        assertThat(analyzer.hasEndVerification(List.of())).isFalse();
        assertThat(analyzer.hasEarlyVerification(List.of())).isFalse();
        assertThat(analyzer.startsWithPlanning(List.of())).isFalse();
    }
}
