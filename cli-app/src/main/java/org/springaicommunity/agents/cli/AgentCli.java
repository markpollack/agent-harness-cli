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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Agent Harness CLI - A terminal interface for AI-powered coding assistance.
 * <p>
 * Built with TUI4J (Bubble Tea for Java) for rich terminal UI.
 */
@Command(
        name = "agent-harness-cli",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "Terminal interface for AI-powered coding assistance"
)
public class AgentCli implements Callable<Integer> {

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

    @Override
    public Integer call() {
        if (printMode) {
            // Print mode: run once and exit (Phase 4)
            System.out.println("Print mode not yet implemented. Use TUI mode.");
            return 1;
        }

        if (linearMode) {
            // Linear mode: interactive with plain I/O (Phase 4)
            System.out.println("Linear mode not yet implemented. Use TUI mode.");
            return 1;
        }

        // TUI mode (default)
        Program program = new Program(new ChatModel());
        program.run();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AgentCli()).execute(args);
        System.exit(exitCode);
    }
}
