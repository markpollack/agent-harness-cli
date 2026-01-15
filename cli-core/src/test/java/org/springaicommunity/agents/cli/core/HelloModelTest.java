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
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.QuitMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HelloModel")
class HelloModelTest {

    private HelloModel model;

    @BeforeEach
    void setUp() {
        model = new HelloModel();
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("should return null command")
        void shouldReturnNullCommand() {
            assertThat(model.init()).isNull();
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
        @DisplayName("should contain quit instruction")
        void shouldContainQuitInstruction() {
            String view = model.view();
            assertThat(view).contains("Press 'q' to quit");
        }

        @Test
        @DisplayName("should contain description")
        void shouldContainDescription() {
            String view = model.view();
            assertThat(view).contains("terminal interface");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should quit on 'q' key press")
        void shouldQuitOnQKeyPress() {
            KeyPressMessage msg = createKeyPressMessage('q');
            UpdateResult<?> result = model.update(msg);

            assertThat(result.model()).isEqualTo(model);
            assertThat(result.command()).isNotNull();
            // Execute command and verify it produces QuitMessage
            Message produced = result.command().execute();
            assertThat(produced).isInstanceOf(QuitMessage.class);
        }

        @Test
        @DisplayName("should quit on 'Q' key press")
        void shouldQuitOnUpperQKeyPress() {
            KeyPressMessage msg = createKeyPressMessage('Q');
            UpdateResult<?> result = model.update(msg);

            assertThat(result.command()).isNotNull();
            Message produced = result.command().execute();
            assertThat(produced).isInstanceOf(QuitMessage.class);
        }

        @Test
        @DisplayName("should not quit on other keys")
        void shouldNotQuitOnOtherKeys() {
            KeyPressMessage msg = createKeyPressMessage('x');
            UpdateResult<?> result = model.update(msg);

            assertThat(result.model()).isEqualTo(model);
            assertThat(result.command()).isNull();
        }

        @Test
        @DisplayName("should handle unknown message types")
        void shouldHandleUnknownMessageTypes() {
            // Create a simple message that isn't KeyPressMessage
            Message unknownMsg = new TestMessage();
            UpdateResult<?> result = model.update(unknownMsg);

            assertThat(result.model()).isEqualTo(model);
            assertThat(result.command()).isNull();
        }

        private KeyPressMessage createKeyPressMessage(char keyChar) {
            Key key = new Key(KeyType.KeyRunes, new char[]{keyChar});
            return new KeyPressMessage(key);
        }
    }

    /**
     * Simple test message implementation.
     */
    private static class TestMessage implements Message {
    }
}
