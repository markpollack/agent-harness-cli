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
package org.springaicommunity.agents.cli;

import com.williamcallahan.tui4j.compat.bubbletea.Program;
import org.springaicommunity.agents.cli.core.ChatModel;
import org.springaicommunity.agents.harness.agents.mini.MiniAgent;
import org.springaicommunity.agents.harness.agents.mini.MiniAgentConfig;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Agent Harness CLI - A terminal interface for AI-powered coding assistance.
 * <p>
 * Built with TUI4J (Bubble Tea for Java) for rich terminal UI.
 * Supports three execution modes:
 * <ul>
 *   <li><b>TUI mode</b> (default) - Interactive terminal UI</li>
 *   <li><b>Linear mode</b> (--linear) - Plain I/O for expect testing</li>
 *   <li><b>Print mode</b> (-p) - Single-task execution for CI</li>
 * </ul>
 */
@Command(
        name = "agent-harness-cli",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "Terminal interface for AI-powered coding assistance"
)
public class AgentCli implements Callable<Integer> {

    // ANSI color codes
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";

    private static final String BANNER = """
                  🖥️
                   \\
                    💥%s════►%s ✨
                   /
                  👤

            agent-harness-cli v0.1.0
            """.formatted(GREEN, RESET);

    private void printBanner() {
        System.out.println(BANNER);
    }

    @Option(names = {"-d", "--directory"},
            description = "Working directory (default: current directory)",
            defaultValue = ".")
    private Path directory;

    @Option(names = {"-m", "--model"},
            description = "Model name (default: claude-sonnet-4-20250514)",
            defaultValue = "claude-sonnet-4-20250514")
    private String model;

    @Option(names = {"-t", "--max-turns"},
            description = "Maximum turns per request (default: 20)",
            defaultValue = "20")
    private int maxTurns;

    @Option(names = {"-p", "--print"},
            description = "Non-interactive mode: run once and exit")
    private boolean printMode;

    @Option(names = {"--linear"},
            description = "Interactive mode with plain I/O (for testing with expect)")
    private boolean linearMode;

    @Parameters(index = "0", arity = "0..1",
            description = "Prompt for -p mode (or read from stdin)")
    private String prompt;

    @Override
    public Integer call() {
        if (printMode) {
            return runPrintMode();
        }

        if (linearMode) {
            return runLinearMode();
        }

        // TUI mode (default)
        printBanner();
        Program program = new Program(new ChatModel());
        program.run();
        return 0;
    }

    /**
     * Run in print mode: single-task execution, then exit.
     */
    private Integer runPrintMode() {
        String input = getInputForPrintMode();
        if (input == null || input.isBlank()) {
            System.err.println("Error: -p/--print mode requires input (argument or stdin)");
            return 1;
        }

        try {
            MiniAgent agent = createAgent(false);
            var result = agent.run(input);
            System.out.println(result.output());
            return result.isSuccess() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Run in linear mode: interactive loop with plain I/O.
     */
    private Integer runLinearMode() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter writer = new PrintWriter(System.out, true)) {

            LinearAgentCallback callback = new LinearAgentCallback(writer, reader);
            MiniAgent agent = null; // Lazy initialization

            printBanner();
            writer.println("Type 'q' or '/quit' to exit");
            writer.println();

            while (true) {
                writer.print("> ");
                writer.flush();

                String line = reader.readLine();
                if (line == null) {
                    break; // EOF
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if ("q".equalsIgnoreCase(line) || "/quit".equalsIgnoreCase(line)) {
                    break;
                }

                if ("/clear".equalsIgnoreCase(line)) {
                    if (agent != null) {
                        agent.clearSession();
                        writer.println("Session cleared.");
                    } else {
                        writer.println("No session to clear.");
                    }
                    continue;
                }

                // Lazy agent creation - only when first message is sent
                if (agent == null) {
                    try {
                        agent = createAgent(true, callback);
                    } catch (Exception e) {
                        writer.println("Error creating agent: " + e.getMessage());
                        continue;
                    }
                }

                try {
                    var result = agent.chat(line, callback);
                    if (!result.isSuccess()) {
                        writer.printf("[%s]%n", result.status());
                    }
                } catch (Exception e) {
                    writer.println("Error: " + e.getMessage());
                }
                writer.println();
            }

            writer.println("Goodbye!");
            return 0;
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Get input for print mode: from argument or stdin.
     */
    private String getInputForPrintMode() {
        if (prompt != null && !prompt.isBlank()) {
            return prompt;
        }

        // Try reading from stdin (for piped input)
        try {
            if (System.in.available() > 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString().trim();
            }
        } catch (IOException e) {
            // Ignore
        }

        return null;
    }

    /**
     * Create MiniAgent for autonomous mode (no session, no callback).
     */
    private MiniAgent createAgent(boolean withSession) {
        return createAgent(withSession, null);
    }

    /**
     * Create MiniAgent with optional session and callback.
     */
    private MiniAgent createAgent(boolean withSession, LinearAgentCallback callback) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable not set");
        }

        var anthropicApi = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();
        var chatModel = AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(org.springframework.ai.anthropic.AnthropicChatOptions.builder()
                        .model(model)
                        .maxTokens(4096)
                        .build())
                .build();

        var config = MiniAgentConfig.builder()
                .workingDirectory(directory)
                .maxTurns(maxTurns)
                .commandTimeout(Duration.ofSeconds(120))
                .build();

        var builder = MiniAgent.builder()
                .config(config)
                .model(chatModel);

        if (withSession) {
            builder.sessionMemory();
        }

        if (callback != null) {
            builder.agentCallback(callback)
                    .interactive(true);
        }

        return builder.build();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AgentCli()).execute(args);
        System.exit(exitCode);
    }
}
