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

import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.bubbles.textinput.TextInput;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases.KeyAlias;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.QuitMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chat TUI model for Agent Harness CLI.
 * <p>
 * Provides a text input for user messages and displays conversation history.
 * For Phase 3, this echoes user input. Agent integration comes in Phase 4.
 */
public class ChatModel implements Model {

    private final TextInput input;
    private final List<ChatEntry> history;

    /**
     * Creates a new ChatModel with empty history.
     */
    public ChatModel() {
        this(new ArrayList<>());
    }

    /**
     * Creates a new ChatModel with the given history.
     * Used for immutable updates.
     */
    ChatModel(List<ChatEntry> history) {
        this.history = new ArrayList<>(history);
        this.input = new TextInput();
        this.input.setPrompt("> ");
        this.input.setPlaceholder("Type a message...");
        this.input.setCharLimit(4000);
        this.input.focus();
    }

    @Override
    public Command init() {
        return TextInput::blink;
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof KeyPressMessage keyPress) {
            // Ctrl+C always quits
            if (KeyAliases.getKeyType(KeyAlias.KeyCtrlC) == keyPress.type()) {
                return new UpdateResult<>(this, QuitMessage::new);
            }

            // Enter submits the input
            if (KeyAliases.getKeyType(KeyAlias.KeyEnter) == keyPress.type()) {
                String text = input.value().trim();
                if (!text.isEmpty()) {
                    return submitInput(text);
                }
                return new UpdateResult<>(this, null);
            }

            // 'q' quits when input is empty
            if ("q".equals(keyPress.key()) && input.isEmpty()) {
                return new UpdateResult<>(this, QuitMessage::new);
            }
        }

        // Delegate other messages to TextInput
        UpdateResult<? extends Model> inputResult = input.update(msg);
        return new UpdateResult<>(this, inputResult.command());
    }

    /**
     * Submits user input: adds to history and resets input.
     * For Phase 3, just echoes back. Phase 4 will integrate with agent.
     */
    private UpdateResult<ChatModel> submitInput(String text) {
        List<ChatEntry> newHistory = new ArrayList<>(history);
        newHistory.add(ChatEntry.user(text));
        // Echo the message back (placeholder for agent response in Phase 4)
        newHistory.add(ChatEntry.assistant("You said: " + text));

        ChatModel newModel = new ChatModel(newHistory);
        return new UpdateResult<>(newModel, null);
    }

    @Override
    public String view() {
        StringBuilder sb = new StringBuilder();

        // Title
        sb.append("Agent Harness CLI\n");
        sb.append("=================\n\n");

        // Conversation history
        if (history.isEmpty()) {
            sb.append("(No messages yet)\n");
        } else {
            for (ChatEntry entry : history) {
                String prefix = entry.role() == ChatEntry.Role.USER ? "You: " : "Assistant: ";
                sb.append(prefix).append(entry.content()).append("\n");
            }
        }
        sb.append("\n");

        // Input prompt
        sb.append(input.view()).append("\n\n");

        // Help text
        sb.append("Press Enter to send, 'q' (when empty) or Ctrl+C to quit.\n");

        return sb.toString();
    }

    /**
     * Returns an unmodifiable view of the conversation history.
     */
    public List<ChatEntry> history() {
        return Collections.unmodifiableList(history);
    }

    /**
     * Returns the current input value.
     */
    public String inputValue() {
        return input.value();
    }

    /**
     * Returns whether the input is empty.
     */
    public boolean isInputEmpty() {
        return input.isEmpty();
    }
}
