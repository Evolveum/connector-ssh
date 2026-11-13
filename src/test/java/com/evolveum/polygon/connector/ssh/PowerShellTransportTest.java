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

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PowerShellTransportTest {

    @DataProvider
    public Object[][] transportedValues() {
        return new Object[][] {{"synthetic-secret"}, {"space ' \" $ ` ; \\ *\nnext"}};
    }

    @DataProvider
    public Object[][] emptyValues() {
        return new Object[][] {{""}, {null}};
    }

    @Test(dataProvider = "transportedValues")
    public void testTransportValues(String value) {
        // Given: Environment transport and sensitive argument
        SshConfiguration configuration = configuration();
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", value);

        // When: Prepare the command and environment
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", "provision", arguments));

        // Then: Transport values and clear their environment
        Assert.assertEquals(command.commandString(), "$password = $env:CONNID_ARG_password; $env:CONNID_ARG_password = $null; \nprovision");
        Assert.assertEquals(command.envs(), Collections.singletonMap("CONNID_ARG_password", value));
        Assert.assertFalse(command.toString().contains(value));
    }

    @Test(dataProvider = "emptyValues")
    public void testEmptyValues(String value) {
        // Given: Empty argument or converted null
        SshConfiguration configuration = configuration();
        configuration.setHandleNullValues("asEmptyString");
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", value);

        // When: Prepare the empty argument
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", "provision", arguments));

        // Then: Assign literal without environment transport
        Assert.assertEquals(command.commandString(), "$password=''; provision");
        Assert.assertEquals(command.envs(), Collections.emptyMap());
    }

    @Test
    public void testMixedValues() {
        // Given: Empty and nonempty script arguments
        SshConfiguration configuration = configuration();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("emptyValue", "");
        arguments.put("password", "synthetic-secret");

        // When: Prepare both argument transports
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", "provision", arguments));

        // Then: Transport only the nonempty value
        String transport = "$password = $env:CONNID_ARG_password; $env:CONNID_ARG_password = $null; \n";
        Assert.assertTrue(Arrays.asList("$emptyValue=''; " + transport + "provision", transport + "$emptyValue=''; provision").contains(command.commandString()),
                command.commandString());
        Assert.assertEquals(command.envs(), Collections.singletonMap("CONNID_ARG_password", "synthetic-secret"));
    }

    @Test
    public void testNullOmitted() {
        // Given: Null argument configured as absent
        SshConfiguration configuration = configuration();
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", null);

        // When: Prepare the omitted argument
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", "provision", arguments));

        // Then: Preserve script without argument assignment
        Assert.assertEquals(configuration.getHandleNullValues(), "asGone");
        Assert.assertEquals(command.commandString(), "provision");
        Assert.assertEquals(command.envs(), Collections.emptyMap());
    }

    @Test
    public void testInlineDefaults() {
        // Given: Default transport and quoted value
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-powershell");
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", "quoted ' value");

        // When: Prepare the default inline assignment
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", "provision", arguments));

        // Then: Preserve escaping and default transport
        Assert.assertEquals(configuration.getVariableTransport(), "inplace");
        Assert.assertEquals(command.commandString(), "$password='quoted '' value'; provision");
        Assert.assertEquals(command.envs(), Collections.emptyMap());
    }

    private SshConfiguration configuration() {
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-powershell");
        configuration.setVariableTransport("env");
        return configuration;
    }
}
