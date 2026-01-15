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
package org.springaicommunity.agents.cli.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatEntry")
class ChatEntryTest {

    @Test
    @DisplayName("user() should create entry with USER role")
    void userShouldCreateEntryWithUserRole() {
        ChatEntry entry = ChatEntry.user("Hello");

        assertThat(entry.role()).isEqualTo(ChatEntry.Role.USER);
        assertThat(entry.content()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("assistant() should create entry with ASSISTANT role")
    void assistantShouldCreateEntryWithAssistantRole() {
        ChatEntry entry = ChatEntry.assistant("Hi there");

        assertThat(entry.role()).isEqualTo(ChatEntry.Role.ASSISTANT);
        assertThat(entry.content()).isEqualTo("Hi there");
    }

    @Test
    @DisplayName("record constructor should work directly")
    void recordConstructorShouldWorkDirectly() {
        ChatEntry entry = new ChatEntry(ChatEntry.Role.USER, "Test");

        assertThat(entry.role()).isEqualTo(ChatEntry.Role.USER);
        assertThat(entry.content()).isEqualTo("Test");
    }
}
