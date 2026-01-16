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

package org.springaicommunity.agents.harness.test.usecase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * File to create during test workspace setup.
 *
 * @param path relative path within workspace
 * @param content file content to write
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SetupFile(
    String path,
    String content
) {

    public SetupFile {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        content = content != null ? content : "";
    }

}
