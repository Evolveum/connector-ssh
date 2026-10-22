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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * Removes blank lines and optionally indented comments from known_hosts content.
 * SSHJ otherwise classifies whitespace-only lines and indented comments as bad host entries.
 * Retained lines are preserved verbatim and terminated with LF. Key validation belongs to the parser.
 */
final class KnownHostsContentFilter {
    private KnownHostsContentFilter() {
    }

    static String removeIgnorableLines(String content) throws IOException {
        final StringBuilder keyLines = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    keyLines.append(line).append('\n');
                }
            }
        }
        return keyLines.toString();
    }
}
