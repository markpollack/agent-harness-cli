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

package org.springaicommunity.agents.harness.test.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.Judge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Judge that verifies Java source files compile successfully.
 *
 * <p>Uses javac to compile all .java files in the workspace.
 * Returns PASS if compilation succeeds (exit code 0), FAIL otherwise.
 *
 * <p>This is a deterministic judge - it provides objective verification
 * that code changes result in valid, compilable code.
 *
 * <p>Usage in YAML:
 * <pre>
 * successCriteria:
 *   - type: compiles
 *     args: ["*.java"]  # optional glob pattern, defaults to all .java files
 * </pre>
 */
public class CompilationJudge implements Judge {

    private static final Logger logger = LoggerFactory.getLogger(CompilationJudge.class);
    private static final int TIMEOUT_SECONDS = 30;

    private final String globPattern;

    /**
     * Create judge that compiles all Java files.
     */
    public CompilationJudge() {
        this("**/*.java");
    }

    /**
     * Create judge with specific glob pattern.
     *
     * @param globPattern glob pattern for Java files to compile
     */
    public CompilationJudge(String globPattern) {
        this.globPattern = globPattern != null ? globPattern : "**/*.java";
    }

    @Override
    public Judgment judge(JudgmentContext context) {
        Path workspace = context.workspace();
        if (workspace == null) {
            return Judgment.fail("No workspace path provided");
        }

        try {
            // Find Java files to compile
            List<Path> javaFiles = findJavaFiles(workspace);

            if (javaFiles.isEmpty()) {
                logger.debug("No Java files found in {}", workspace);
                return Judgment.pass("No Java files to compile");
            }

            logger.debug("Found {} Java files to compile in {}", javaFiles.size(), workspace);

            // Run javac
            ProcessResult result = new ProcessExecutor()
                    .command("javac", "-d", workspace.toString())
                    .directory(workspace.toFile())
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readOutput(true)
                    .redirectErrorStream(true)
                    .environment("JAVA_HOME", System.getProperty("java.home"))
                    .command(buildJavacCommand(javaFiles, workspace))
                    .execute();

            int exitCode = result.getExitValue();
            String output = result.outputUTF8();

            if (exitCode == 0) {
                logger.debug("Compilation successful for {} files", javaFiles.size());
                return Judgment.pass("Compilation successful: " + javaFiles.size() + " files compiled");
            } else {
                logger.debug("Compilation failed with exit code {}: {}", exitCode, output);
                return Judgment.fail("Compilation failed: " + truncate(output, 500));
            }

        } catch (Exception e) {
            logger.error("Compilation check failed: {}", e.getMessage());
            return Judgment.fail("Compilation check error: " + e.getMessage());
        }
    }

    private List<Path> findJavaFiles(Path workspace) throws Exception {
        try (Stream<Path> paths = Files.walk(workspace)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }
    }

    private String[] buildJavacCommand(List<Path> javaFiles, Path workspace) {
        // Build command: javac -d <workspace> <file1> <file2> ...
        String[] command = new String[javaFiles.size() + 3];
        command[0] = "javac";
        command[1] = "-d";
        command[2] = workspace.toString();

        for (int i = 0; i < javaFiles.size(); i++) {
            command[i + 3] = javaFiles.get(i).toString();
        }

        return command;
    }

    private String truncate(String s, int maxLength) {
        if (s == null || s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength) + "... [truncated]";
    }
}
