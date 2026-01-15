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

import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.Renderer;
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.color.ColorProfile;
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.color.NoColor;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.QuitMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatModel")
class ChatModelTest {

    private ChatModel model;

    @BeforeAll
    static void initTerminal() {
        // Set up no-color terminal info for consistent output in tests
        TerminalInfo.provide(() -> new TerminalInfo(false, new NoColor()));
        Renderer.defaultRenderer().setColorProfile(ColorProfile.Ascii);
    }

    @BeforeEach
    void setUp() {
        model = new ChatModel();
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("should return blink command for cursor")
        void shouldReturnBlinkCommand() {
            assertThat(model.init()).isNotNull();
        }
    }

    @Nested
    @DisplayName("view")
    class View {

        @Test
        @DisplayName("should contain title")
        void shouldContainTitle() {
            String view = model.view();
            assertThat(view).contains("Agent Harness CLI");
        }

        @Test
        @DisplayName("should contain prompt indicator")
        void shouldContainPromptIndicator() {
            String view = model.view();
            assertThat(view).contains(">");
        }

        @Test
        @DisplayName("should contain empty history message when no messages")
        void shouldContainEmptyHistoryMessage() {
            String view = model.view();
            assertThat(view).contains("No messages yet");
        }

        @Test
        @DisplayName("should contain quit instruction")
        void shouldContainQuitInstruction() {
            String view = model.view();
            assertThat(view).contains("Ctrl+C to quit");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should quit on Ctrl+C")
        void shouldQuitOnCtrlC() {
            KeyPressMessage msg = createCtrlCMessage();
            UpdateResult<?> result = model.update(msg);

            assertThat(result.command()).isNotNull();
            Message produced = result.command().execute();
            assertThat(produced).isInstanceOf(QuitMessage.class);
        }

        @Test
        @DisplayName("should quit on 'q' when input is empty")
        void shouldQuitOnQWhenInputEmpty() {
            KeyPressMessage msg = createKeyPressMessage('q');
            UpdateResult<?> result = model.update(msg);

            assertThat(result.command()).isNotNull();
            Message produced = result.command().execute();
            assertThat(produced).isInstanceOf(QuitMessage.class);
        }

        @Test
        @DisplayName("should not quit on 'q' after typing")
        void shouldNotQuitOnQAfterTyping() {
            // Type 'h' first
            model.update(createKeyPressMessage('h'));

            // Now 'q' should be added to input, not quit
            KeyPressMessage qMsg = createKeyPressMessage('q');
            UpdateResult<?> result = model.update(qMsg);

            // Should not produce quit message
            if (result.command() != null) {
                Message produced = result.command().execute();
                assertThat(produced).isNotInstanceOf(QuitMessage.class);
            }
        }

        @Test
        @DisplayName("should handle unknown message types")
        void shouldHandleUnknownMessageTypes() {
            Message unknownMsg = new TestMessage();
            UpdateResult<?> result = model.update(unknownMsg);

            assertThat(result.model()).isNotNull();
        }

        private KeyPressMessage createKeyPressMessage(char keyChar) {
            Key key = new Key(KeyType.KeyRunes, new char[]{keyChar});
            return new KeyPressMessage(key);
        }

        private KeyPressMessage createCtrlCMessage() {
            // Ctrl+C maps to keyETX (End of Text, ASCII 3)
            Key key = new Key(KeyType.keyETX, new char[]{});
            return new KeyPressMessage(key);
        }
    }

    @Nested
    @DisplayName("history")
    class History {

        @Test
        @DisplayName("should start with empty history")
        void shouldStartWithEmptyHistory() {
            assertThat(model.history()).isEmpty();
        }

        @Test
        @DisplayName("should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            assertThat(model.history().getClass().getName())
                    .contains("Unmodifiable");
        }
    }

    @Nested
    @DisplayName("input state")
    class InputState {

        @Test
        @DisplayName("should start with empty input")
        void shouldStartWithEmptyInput() {
            assertThat(model.isInputEmpty()).isTrue();
        }

        @Test
        @DisplayName("inputValue should return current input")
        void inputValueShouldReturnCurrentInput() {
            assertThat(model.inputValue()).isEmpty();
        }
    }

    /**
     * Simple test message implementation.
     */
    private static class TestMessage implements Message {
    }
}
