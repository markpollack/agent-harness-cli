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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default CLI executor implementation using zt-exec.
 *
 * <p>Provides robust process execution with:
 * <ul>
 *   <li>Proper timeout handling</li>
 *   <li>Input piping support</li>
 *   <li>Output capture</li>
 *   <li>Working directory configuration</li>
 * </ul>
 */
public class DefaultCliExecutor implements CliExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCliExecutor.class);

    @Override
    public ExecutionResult execute(ExecutionConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            ProcessExecutor executor = new ProcessExecutor()
                    .command(config.command())
                    .timeout(config.timeoutSeconds(), TimeUnit.SECONDS)
                    .readOutput(true)
                    .redirectErrorStream(true);  // Capture both stdout and stderr

            if (config.workingDirectory() != null) {
                executor.directory(config.workingDirectory().toFile());
            }

            if (config.input() != null && !config.input().isEmpty()) {
                executor.redirectInput(
                        new ByteArrayInputStream(config.input().getBytes(StandardCharsets.UTF_8))
                );
            }

            logger.debug("Executing: {} in {}", config.command(), config.workingDirectory());

            ProcessResult result = executor.execute();
            long duration = System.currentTimeMillis() - startTime;

            logger.debug("Execution completed: exitCode={}, duration={}ms",
                    result.getExitValue(), duration);

            return new ExecutionResult(
                    result.outputUTF8(),
                    result.getExitValue(),
                    duration,
                    false
            );

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warn("Execution timed out after {}ms", duration);
            return ExecutionResult.timeout("Execution timed out after " + duration + "ms", duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Execution failed: {}", e.getMessage(), e);
            return ExecutionResult.failed("Execution failed: " + e.getMessage(), -1, duration);
        }
    }

}
