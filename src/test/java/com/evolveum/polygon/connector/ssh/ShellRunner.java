/*
 * Copyright (c) 2026 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.polygon.connector.ssh;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
Executes generated shell commands with bounded waits and separate output files to avoid pipe deadlocks.
*/
final class ShellRunner {

    static Result runBash(String executable, ProcessedCommand command) throws Exception {
        Path scriptFile = Files.createTempFile("ssh-script-", ".sh");
        try {
            Files.write(scriptFile, command.commandString().getBytes(StandardCharsets.UTF_8));
            return run(Arrays.asList(executable, "--noprofile", "--norc", scriptFile.toString()), command);
        } finally {
            Files.deleteIfExists(scriptFile);
        }
    }

    static Result run(List<String> arguments, ProcessedCommand command) throws Exception {
        Path outputFile = Files.createTempFile("ssh-output-", ".txt");
        Path errorFile = Files.createTempFile("ssh-error-", ".txt");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(arguments);
            builder.environment().putAll(command.envs());
            builder.redirectOutput(outputFile.toFile());
            builder.redirectError(errorFile.toFile());
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                throw new IOException("Shell execution exceeded twenty seconds");
            }
            return new Result(process.exitValue(), new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(errorFile), StandardCharsets.UTF_8));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(errorFile);
        }
    }

    static final class Result {
        final int status;
        final String output;
        final String error;

        Result(int status, String output, String error) {
            this.status = status;
            this.output = output;
            this.error = error;
        }
    }
}
