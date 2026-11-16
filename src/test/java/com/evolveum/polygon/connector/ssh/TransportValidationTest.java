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

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public class TransportValidationTest {

    private static final String STYLE_ERROR = "variableTransport set to 'env' is only supported for argument styles 'variables-bash' and 'variables-powershell'";

    @DataProvider
    public Object[][] invalidSettings() {
        return new Object[][] {
                {null, "dash", "Unknown value of variable transport: null"},
                {"", "variables-bash", "Unknown value of variable transport: "},
                {"ENV", "variables-powershell", "Unknown value of variable transport: ENV"},
                {"unknown", "slash", "Unknown value of variable transport: unknown"},
                {"env", null, STYLE_ERROR}, {"env", "dash", STYLE_ERROR}, {"env", "slash", STYLE_ERROR}, {"env", "unknown", STYLE_ERROR}
        };
    }

    @DataProvider
    public Object[][] inlineStyles() {
        return new Object[][] {
                {null, "execute -password synthetic-value"}, {"dash", "execute -password synthetic-value"}, {"slash", "execute /password synthetic-value"},
                {"variables-bash", "password='synthetic-value'; execute"}, {"variables-powershell", "$password='synthetic-value'; execute"}
        };
    }

    @DataProvider
    public Object[][] variableStyles() {
        return new Object[][] {{"variables-bash"}, {"variables-powershell"}};
    }

    @DataProvider
    public Object[][] scriptArguments() {
        return new Object[][] {{null}, {Collections.emptyMap()}, {Collections.singletonMap("password", "synthetic-value")}};
    }

    @Test(dataProvider = "invalidSettings")
    public void testInvalidSettings(String transport, String style, String message) {
        // Given: Unsupported transport configuration
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.setVariableTransport(transport);
        configuration.setArgumentStyle(style);

        // When: Validate configuration explicitly
        ConfigurationException exception = reject(configuration::validate);

        // Then: Explain the rejected configuration
        assertEquals(exception.getMessage(), message);
    }

    @Test(dataProvider = "invalidSettings")
    public void testInvalidInitialization(String transport, String style, String message) {
        // Given: Connector with unsupported transport settings
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.setVariableTransport(transport);
        configuration.setArgumentStyle(style);
        SshConnector connector = new SshConnector();

        // When: Initialize without explicit validation
        ConfigurationException exception = reject(() -> connector.init(configuration));

        // Then: Reject before changing connector state
        assertEquals(exception.getMessage(), message);
        assertNull(connector.getConfiguration());
    }

    @Test(dataProvider = "scriptArguments")
    public void testInvalidProcessing(Map<String, Object> arguments) {
        // Given: Environment transport with default style
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.setVariableTransport("env");
        CommandProcessor processor = new CommandProcessor(configuration);
        ScriptContext context = new ScriptContext("bash", "execute", arguments);

        // When: Process without explicit validation
        ConfigurationException exception = reject(() -> processor.process(context));

        // Then: Reject before returning inline arguments
        assertEquals(exception.getMessage(), STYLE_ERROR);
    }

    @Test(dataProvider = "inlineStyles")
    public void testDefaultTransport(String style, String command) {
        // Given: Existing style with default transport
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.setArgumentStyle(style);
        SshConnector connector = new SshConnector();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("password", "synthetic-value");

        // When: Initialize and format existing arguments
        connector.init(configuration);
        ProcessedCommand processed = new CommandProcessor(configuration).process(new ScriptContext("shell", "execute", arguments));

        // Then: Preserve existing inline behavior
        assertSame(connector.getConfiguration(), configuration);
        assertEquals(configuration.getVariableTransport(), "inplace");
        assertEquals(processed.commandString(), command);
        assertEquals(processed.envs(), Collections.emptyMap());
    }

    @Test(dataProvider = "variableStyles")
    public void testSupportedTransport(String style) {
        // Given: Supported environment transport and style
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.setVariableTransport("env");
        configuration.setArgumentStyle(style);
        SshConnector connector = new SshConnector();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("password", "synthetic-value");

        // When: Initialize and format transported arguments
        connector.init(configuration);
        ProcessedCommand processed = new CommandProcessor(configuration).process(new ScriptContext("shell", "execute", arguments));

        // Then: Retain environment transport without inlining
        assertSame(connector.getConfiguration(), configuration);
        assertEquals(processed.envs(), Collections.singletonMap("CONNID_ARG_password", "synthetic-value"));
        assertFalse(processed.commandString().contains("synthetic-value"));
    }

    @Test
    public void testChangedTransport() {
        // Given: Initialized connector with changed transport
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        SshConnector connector = new SshConnector();
        connector.init(configuration);
        configuration.setVariableTransport("env");
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("password", "synthetic-value");
        ScriptContext context = new ScriptContext("bash", "execute", arguments);

        // When: Execute using modified configuration
        ConfigurationException exception = reject(() -> connector.runScriptOnResource(context, null));

        // Then: Reject before logging or connecting
        assertEquals(exception.getMessage(), STYLE_ERROR);
    }

    @Test
    public void testChangedStyle() {
        // Given: Initialized environment transport with changed style
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.setVariableTransport("env");
        configuration.setArgumentStyle("variables-bash");
        SshConnector connector = new SshConnector();
        connector.init(configuration);
        configuration.setArgumentStyle("slash");
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("password", "synthetic-value");
        ScriptContext context = new ScriptContext("bash", "execute", arguments);

        // When: Execute using modified argument style
        ConfigurationException exception = reject(() -> connector.runScriptOnResource(context, null));

        // Then: Reject before logging or connecting
        assertEquals(exception.getMessage(), STYLE_ERROR);
    }

    private ConfigurationException reject(Runnable action) {
        try {
            action.run();
        } catch (ConfigurationException exception) {
            return exception;
        }
        fail("Expected configuration rejection");
        return null;
    }
}
