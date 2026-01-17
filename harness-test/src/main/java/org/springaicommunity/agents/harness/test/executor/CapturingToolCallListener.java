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

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springaicommunity.agents.harness.core.ToolCallListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ToolCallListener} that captures tool call events as {@link ToolCallRecord}s.
 *
 * <p>This listener provides structured access to tool execution data without
 * needing to parse log output. It collects all tool calls during an agent run
 * for later inspection and validation.</p>
 */
public class CapturingToolCallListener implements ToolCallListener {

    private final List<ToolCallRecord> records = new ArrayList<>();

    @Override
    public void onToolExecutionCompleted(
            String runId,
            int turn,
            AssistantMessage.ToolCall toolCall,
            String result,
            Duration duration) {
        records.add(ToolCallRecord.success(
                toolCall.name(),
                toolCall.arguments(),
                result,
                duration));
    }

    @Override
    public void onToolExecutionFailed(
            String runId,
            int turn,
            AssistantMessage.ToolCall toolCall,
            Throwable error,
            Duration duration) {
        records.add(ToolCallRecord.failure(
                toolCall.name(),
                toolCall.arguments(),
                duration));
    }

    /**
     * Get all captured tool call records.
     *
     * @return immutable copy of the records
     */
    public List<ToolCallRecord> getRecords() {
        return List.copyOf(records);
    }

    /**
     * Get the number of tool calls captured.
     */
    public int getToolCallCount() {
        return records.size();
    }

    /**
     * Get the total duration of all tool executions.
     */
    public Duration getTotalDuration() {
        return records.stream()
                .map(ToolCallRecord::duration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    /**
     * Clear all captured records.
     */
    public void clear() {
        records.clear();
    }

}
