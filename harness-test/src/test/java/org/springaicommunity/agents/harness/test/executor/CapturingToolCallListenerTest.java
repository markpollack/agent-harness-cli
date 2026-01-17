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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CapturingToolCallListener.
 */
class CapturingToolCallListenerTest {

    private CapturingToolCallListener listener;

    @BeforeEach
    void setUp() {
        listener = new CapturingToolCallListener();
    }

    @Test
    void initiallyHasNoRecords() {
        assertThat(listener.getRecords()).isEmpty();
        assertThat(listener.getToolCallCount()).isZero();
        assertThat(listener.getTotalDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void capturesSuccessfulToolCall() {
        AssistantMessage.ToolCall toolCall = mockToolCall("ReadTool", "{\"path\": \"file.txt\"}");

        listener.onToolExecutionCompleted("run1", 1, toolCall, "file contents", Duration.ofMillis(42));

        List<ToolCallRecord> records = listener.getRecords();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).toolName()).isEqualTo("ReadTool");
        assertThat(records.get(0).input()).isEqualTo("{\"path\": \"file.txt\"}");
        assertThat(records.get(0).output()).isEqualTo("file contents");
        assertThat(records.get(0).duration()).isEqualTo(Duration.ofMillis(42));
        assertThat(records.get(0).success()).isTrue();
    }

    @Test
    void capturesFailedToolCall() {
        AssistantMessage.ToolCall toolCall = mockToolCall("WriteTool", "{\"path\": \"/etc/passwd\"}");

        listener.onToolExecutionFailed("run1", 1, toolCall,
                new SecurityException("Access denied"), Duration.ofMillis(5));

        List<ToolCallRecord> records = listener.getRecords();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).toolName()).isEqualTo("WriteTool");
        assertThat(records.get(0).output()).isNull();
        assertThat(records.get(0).success()).isFalse();
    }

    @Test
    void capturesMultipleToolCalls() {
        listener.onToolExecutionCompleted("run1", 1,
                mockToolCall("Tool1", "input1"), "output1", Duration.ofMillis(10));
        listener.onToolExecutionCompleted("run1", 1,
                mockToolCall("Tool2", "input2"), "output2", Duration.ofMillis(20));
        listener.onToolExecutionCompleted("run1", 2,
                mockToolCall("Tool3", "input3"), "output3", Duration.ofMillis(30));

        assertThat(listener.getToolCallCount()).isEqualTo(3);
        assertThat(listener.getTotalDuration()).isEqualTo(Duration.ofMillis(60));
    }

    @Test
    void clearRemovesAllRecords() {
        listener.onToolExecutionCompleted("run1", 1,
                mockToolCall("Tool", "input"), "output", Duration.ofMillis(100));

        assertThat(listener.getRecords()).hasSize(1);

        listener.clear();

        assertThat(listener.getRecords()).isEmpty();
        assertThat(listener.getToolCallCount()).isZero();
    }

    @Test
    void getRecordsReturnsImmutableCopy() {
        listener.onToolExecutionCompleted("run1", 1,
                mockToolCall("Tool", "input"), "output", Duration.ofMillis(50));

        List<ToolCallRecord> records = listener.getRecords();

        assertThatThrownBy(() -> records.add(ToolCallRecord.success("x", "y", "z", Duration.ZERO)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AssistantMessage.ToolCall mockToolCall(String name, String arguments) {
        AssistantMessage.ToolCall toolCall = mock(AssistantMessage.ToolCall.class);
        when(toolCall.name()).thenReturn(name);
        when(toolCall.arguments()).thenReturn(arguments);
        return toolCall;
    }

}
