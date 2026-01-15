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

/**
 * Represents a single entry in the chat history.
 *
 * @param role the role (USER or ASSISTANT)
 * @param content the message content
 */
public record ChatEntry(Role role, String content) {

    /**
     * Role of the chat participant.
     */
    public enum Role {
        USER,
        ASSISTANT
    }

    /**
     * Creates a user message.
     */
    public static ChatEntry user(String content) {
        return new ChatEntry(Role.USER, content);
    }

    /**
     * Creates an assistant message.
     */
    public static ChatEntry assistant(String content) {
        return new ChatEntry(Role.ASSISTANT, content);
    }
}
