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

import java.util.List;

/**
 * Test setup configuration for workspace preparation.
 *
 * @param workspace workspace directory path (may contain {{timestamp}} placeholder)
 * @param files files to create in workspace during setup
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Setup(
    String workspace,
    List<SetupFile> files
) {

    public Setup {
        files = files != null ? List.copyOf(files) : List.of();
    }

    /**
     * Check if workspace uses temp directory pattern.
     */
    public boolean usesTempWorkspace() {
        return workspace != null && workspace.contains("/tmp/");
    }

    /**
     * Check if workspace contains timestamp placeholder.
     */
    public boolean hasTimestampPlaceholder() {
        return workspace != null && workspace.contains("{{timestamp}}");
    }

}
