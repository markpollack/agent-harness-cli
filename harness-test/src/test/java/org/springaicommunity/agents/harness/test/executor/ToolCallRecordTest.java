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

package org.springaicommunity.agents.harness.test.executor;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ToolCallRecord.
 */
class ToolCallRecordTest {

    @Test
    void successFactoryCreatesSuccessfulRecord() {
        ToolCallRecord record = ToolCallRecord.success(
                "ReadTool",
                "{\"path\": \"test.txt\"}",
                "file contents",
                Duration.ofMillis(50)
        );

        assertThat(record.toolName()).isEqualTo("ReadTool");
        assertThat(record.input()).isEqualTo("{\"path\": \"test.txt\"}");
        assertThat(record.output()).isEqualTo("file contents");
        assertThat(record.duration()).isEqualTo(Duration.ofMillis(50));
        assertThat(record.success()).isTrue();
    }

    @Test
    void failureFactoryCreatesFailedRecord() {
        ToolCallRecord record = ToolCallRecord.failure(
                "WriteTool",
                "{\"path\": \"/root/test.txt\"}",
                Duration.ofMillis(10)
        );

        assertThat(record.toolName()).isEqualTo("WriteTool");
        assertThat(record.input()).isEqualTo("{\"path\": \"/root/test.txt\"}");
        assertThat(record.output()).isNull();
        assertThat(record.duration()).isEqualTo(Duration.ofMillis(10));
        assertThat(record.success()).isFalse();
    }

    @Test
    void recordsAreEqualWhenAllFieldsMatch() {
        ToolCallRecord r1 = ToolCallRecord.success("Tool", "input", "output", Duration.ofMillis(100));
        ToolCallRecord r2 = ToolCallRecord.success("Tool", "input", "output", Duration.ofMillis(100));

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void recordsAreNotEqualWhenFieldsDiffer() {
        ToolCallRecord r1 = ToolCallRecord.success("Tool", "input", "output", Duration.ofMillis(100));
        ToolCallRecord r2 = ToolCallRecord.success("OtherTool", "input", "output", Duration.ofMillis(100));

        assertThat(r1).isNotEqualTo(r2);
    }

}
