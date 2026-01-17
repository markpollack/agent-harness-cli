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
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InProcessExecutor.
 */
class InProcessExecutorTest {

    @Test
    void constructorRequiresChatModel() {
        assertThatThrownBy(() -> new InProcessExecutor(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatModel is required");
    }

    @Test
    void constructorAcceptsValidChatModel() {
        ChatModel mockModel = mock(ChatModel.class);

        InProcessExecutor executor = new InProcessExecutor(mockModel);

        assertThat(executor).isNotNull();
    }

    @Test
    void constructorWithCustomMaxTurns() {
        ChatModel mockModel = mock(ChatModel.class);

        InProcessExecutor executor = new InProcessExecutor(mockModel, 50);

        assertThat(executor).isNotNull();
    }

    @Test
    void builderCreatesExecutor() {
        ChatModel mockModel = mock(ChatModel.class);

        InProcessExecutor executor = InProcessExecutor.builder()
                .chatModel(mockModel)
                .maxTurns(30)
                .build();

        assertThat(executor).isNotNull();
    }

    @Test
    void builderRequiresChatModel() {
        assertThatThrownBy(() -> InProcessExecutor.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatModel is required");
    }

    @Test
    void getToolCallsReturnsEmptyListBeforeExecution() {
        ChatModel mockModel = mock(ChatModel.class);
        InProcessExecutor executor = new InProcessExecutor(mockModel);

        assertThat(executor.getToolCalls()).isEmpty();
    }

    @Test
    void getListenerReturnsNullBeforeExecution() {
        ChatModel mockModel = mock(ChatModel.class);
        InProcessExecutor executor = new InProcessExecutor(mockModel);

        assertThat(executor.getListener()).isNull();
    }

}
