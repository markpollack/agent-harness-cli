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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads and parses use case YAML files for AI-driven testing.
 *
 * <p>Use cases are defined in YAML format and loaded from:</p>
 * <ul>
 *   <li>File system paths</li>
 *   <li>Classpath resources</li>
 *   <li>YAML strings</li>
 * </ul>
 */
public class UseCaseLoader {

    private static final Logger logger = LoggerFactory.getLogger(UseCaseLoader.class);

    private final ObjectMapper yamlMapper;

    /**
     * Create a new loader with default settings.
     */
    public UseCaseLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Create a loader with custom ObjectMapper.
     *
     * @param yamlMapper pre-configured YAML ObjectMapper
     */
    public UseCaseLoader(ObjectMapper yamlMapper) {
        this.yamlMapper = Objects.requireNonNull(yamlMapper, "yamlMapper is required");
    }

    /**
     * Load a use case from a YAML file path.
     *
     * @param yamlPath path to the YAML file
     * @return loaded use case
     * @throws IOException if the file cannot be read or parsed
     */
    public UseCase load(Path yamlPath) throws IOException {
        if (!Files.exists(yamlPath)) {
            throw new IOException("Use case file not found: " + yamlPath);
        }
        logger.debug("Loading use case from: {}", yamlPath);
        return yamlMapper.readValue(yamlPath.toFile(), UseCase.class);
    }

    /**
     * Load a use case from a YAML string.
     *
     * @param yamlContent YAML content as string
     * @return loaded use case
     * @throws IOException if the content cannot be parsed
     */
    public UseCase parse(String yamlContent) throws IOException {
        Objects.requireNonNull(yamlContent, "yamlContent is required");
        return yamlMapper.readValue(yamlContent, UseCase.class);
    }

    /**
     * Load a use case from a classpath resource.
     *
     * @param resourcePath classpath resource path (e.g., "use-cases/basic/quit.yaml")
     * @return loaded use case
     * @throws IOException if the resource cannot be found or parsed
     */
    public UseCase loadResource(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            logger.debug("Loading use case from resource: {}", resourcePath);
            return yamlMapper.readValue(is, UseCase.class);
        }
    }

    /**
     * Find all use case files in a directory (recursively).
     *
     * @param directory base directory to search
     * @return list of YAML file paths, sorted alphabetically
     * @throws IOException if directory cannot be traversed
     */
    public List<Path> findUseCases(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            logger.warn("Use cases directory does not exist: {}", directory);
            return List.of();
        }
        try (var stream = Files.walk(directory)) {
            List<Path> paths = stream
                    .filter(this::isUseCaseFile)
                    .sorted()
                    .toList();
            logger.debug("Found {} use cases in {}", paths.size(), directory);
            return paths;
        }
    }

    /**
     * Find use cases in a specific category subdirectory.
     *
     * @param baseDir base use cases directory
     * @param category category name (subdirectory)
     * @return list of YAML file paths in the category
     * @throws IOException if directory cannot be traversed
     */
    public List<Path> findUseCasesByCategory(Path baseDir, String category) throws IOException {
        Path categoryDir = baseDir.resolve(category);
        return findUseCases(categoryDir);
    }

    /**
     * Load all use cases from a directory.
     *
     * @param directory base directory to search
     * @return list of loaded use cases
     * @throws IOException if any file cannot be loaded
     */
    public List<UseCase> loadAll(Path directory) throws IOException {
        return findUseCases(directory).stream()
                .map(this::loadUnchecked)
                .toList();
    }

    private boolean isUseCaseFile(Path path) {
        String fileName = path.getFileName().toString();
        return (fileName.endsWith(".yaml") || fileName.endsWith(".yml"))
                && !fileName.startsWith("_"); // Skip templates/helpers
    }

    private UseCase loadUnchecked(Path path) {
        try {
            return load(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load use case: " + path, e);
        }
    }

}
