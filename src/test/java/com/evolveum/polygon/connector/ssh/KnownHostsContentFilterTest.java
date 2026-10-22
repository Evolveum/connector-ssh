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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

public class KnownHostsContentFilterTest {
    @DataProvider(name = "lineSeparators")
    public Object[][] lineSeparators() {
        return new Object[][]{{"\n"}, {"\r\n"}, {"\r"}};
    }

    @Test(dataProvider = "lineSeparators")
    public void preservesRetainedLinesAndNormalizesLineEndings(String newline) throws IOException {
        String first = " \tserver.example ssh-ed25519 AAAA inline # comment  ";
        String second = "other.example ssh-rsa BBBB\t";
        String content = " \t" + newline + " \t# header" + newline + first + newline
                + "# between entries" + newline + second;
        assertEquals(KnownHostsContentFilter.removeIgnorableLines(content), first + "\n" + second + "\n");
    }

    @Test
    public void returnsEmptyContentWhenAllLinesAreIgnorable() throws IOException {
        assertEquals(KnownHostsContentFilter.removeIgnorableLines(""), "");
        assertEquals(KnownHostsContentFilter.removeIgnorableLines(" \t\r\n # comment\n\t# another\r\n"), "");
    }

    @Test
    public void preservesMalformedEntriesForParserRejection() throws IOException {
        String content = "# comment\ninvalid entry\n \t\nserver.example unsupported-key AAAA\n";
        assertEquals(KnownHostsContentFilter.removeIgnorableLines(content),
                "invalid entry\nserver.example unsupported-key AAAA\n");
    }
}
