package com.evolveum.polygon.connector.ssh;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class EnvNamesTest {

    @DataProvider
    public Object[][] styles() {
        return new Object[][] {{"variables-bash"}, {"variables-powershell"}};
    }

    @DataProvider
    public Object[][] invalidPrefixes() {
        return new Object[][] {{null}, {""}, {"9START_"}, {"ACME-"}, {"MY PREFIX_"}, {"ENV:"}, {"VAR\n"}};
    }

    @DataProvider
    public Object[][] prefixCases() {
        return combine(new String[] {null, "", "9START_", "ACME-", "MY PREFIX_", "ENV:", "VAR\n"});
    }

    @DataProvider
    public Object[][] validPrefixes() {
        return combine(new String[] {"X", "_", "Tenant9_"});
    }

    @DataProvider
    public Object[][] invalidNames() {
        return combine(new String[] {"", "9name", "bad-name", "has space", "name;execute", "\u00e4"});
    }

    @DataProvider
    public Object[][] collisions() {
        return new Object[][] {{false, "CONNID_ARG_"}, {true, "CONNID_ARG_"}, {false, "CUSTOM_"}, {true, "CUSTOM_"}};
    }

    @DataProvider
    public Object[][] nullPositions() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "prefixCases")
    public void testInvalidPrefix(String prefix, String style) {
        // Given: Environment transport with invalid prefix
        SshConfiguration configuration = configuration(style);
        configuration.setVariableTransportEnvPrefix(prefix);

        // When: Validate transport configuration
        ConfigurationException exception = reject(configuration::validate);

        // Then: Explain the required prefix format
        assertEquals(exception.getMessage(), "variableTransportEnvPrefix must match [A-Za-z_][A-Za-z0-9_]* when variableTransport is 'env'");
    }

    @Test(dataProvider = "styles")
    public void testDefaultPrefix(String style) {
        // Given: Default prefix and portable argument
        SshConfiguration configuration = configuration(style);
        Map<String, Object> arguments = Collections.singletonMap("_name9", "synthetic-value");

        // When: Process transported argument
        ProcessedCommand command = process(configuration, arguments);

        // Then: Preserve the documented default prefix
        assertEquals(configuration.getVariableTransportEnvPrefix(), "CONNID_ARG_");
        assertEquals(command.envs(), Collections.singletonMap("CONNID_ARG__name9", "synthetic-value"));
    }

    @Test(dataProvider = "validPrefixes")
    public void testCustomPrefix(String prefix, String style) {
        // Given: Portable custom prefix and argument
        SshConfiguration configuration = configuration(style);
        configuration.setVariableTransportEnvPrefix(prefix);
        Map<String, Object> arguments = Collections.singletonMap("name_2", "synthetic-value");

        // When: Process transported argument
        ProcessedCommand command = process(configuration, arguments);

        // Then: Use the configured transport name
        assertEquals(command.envs(), Collections.singletonMap(prefix + "name_2", "synthetic-value"));
        assertFalse(command.commandString().contains("synthetic-value"));
    }

    @Test(dataProvider = "invalidNames")
    public void testInvalidName(String name, String style) {
        // Given: Nonportable transported argument name
        SshConfiguration configuration = configuration(style);
        Map<String, Object> arguments = Collections.singletonMap(name, "synthetic-value");

        // When: Process transported argument
        ConfigurationException exception = reject(() -> process(configuration, arguments));

        // Then: Identify the name without its value
        assertEquals(exception.getMessage(), "Invalid script argument name for environment transport: '" + name + "'");
    }

    @Test(dataProvider = "styles")
    public void testOmittedName(String style) {
        // Given: Invalid null name omitted by policy
        SshConfiguration configuration = configuration(style);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("bad-name", null);
        arguments.put("valid", "synthetic-value");

        // When: Process remaining effective arguments
        ProcessedCommand command = process(configuration, arguments);

        // Then: Transport only the retained argument
        assertEquals(command.envs(), Collections.singletonMap("CONNID_ARG_valid", "synthetic-value"));
        assertFalse(command.commandString().contains("bad-name"));
    }

    @Test(dataProvider = "styles")
    public void testEmptyName(String style) {
        // Given: Invalid null name retained as empty
        SshConfiguration configuration = configuration(style);
        configuration.setHandleNullValues("asEmptyString");
        Map<String, Object> arguments = Collections.singletonMap("bad-name", null);

        // When: Process retained empty argument
        ConfigurationException exception = reject(() -> process(configuration, arguments));

        // Then: Validate the retained argument name
        assertEquals(exception.getMessage(), "Invalid script argument name for environment transport: 'bad-name'");
    }

    @Test(dataProvider = "collisions")
    public void testNameCollision(boolean reversed, String prefix) {
        // Given: Argument overlaps another transport name
        SshConfiguration configuration = configuration("variables-bash");
        configuration.setVariableTransportEnvPrefix(prefix);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(reversed ? prefix + "foo" : "foo", "synthetic-first");
        arguments.put(reversed ? "foo" : prefix + "foo", "synthetic-second");

        // When: Process overlapping names in either order
        ConfigurationException exception = reject(() -> process(configuration, arguments));

        // Then: Reject both collision orientations
        assertEquals(exception.getMessage(), "Script argument '" + prefix + "foo' conflicts with environment transport for 'foo'");
    }

    @Test(dataProvider = "nullPositions")
    public void testOmittedCollision(boolean omitBase) {
        // Given: One overlapping argument omitted by policy
        SshConfiguration configuration = configuration("variables-bash");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("foo", omitBase ? null : "synthetic-value");
        arguments.put("CONNID_ARG_foo", omitBase ? "synthetic-value" : null);

        // When: Process remaining effective argument
        ProcessedCommand command = process(configuration, arguments);

        // Then: Allow names without an effective collision
        String environment = omitBase ? "CONNID_ARG_CONNID_ARG_foo" : "CONNID_ARG_foo";
        assertEquals(command.envs(), Collections.singletonMap(environment, "synthetic-value"));
    }

    @Test(dataProvider = "nullPositions")
    public void testEmptyCollision(boolean emptyBase) {
        // Given: Overlapping null argument retained as empty
        SshConfiguration configuration = configuration("variables-bash");
        configuration.setHandleNullValues("asEmptyString");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("foo", emptyBase ? null : "synthetic-value");
        arguments.put("CONNID_ARG_foo", emptyBase ? "synthetic-value" : null);

        // When: Process retained overlapping arguments
        ConfigurationException exception = reject(() -> process(configuration, arguments));

        // Then: Include empty arguments in collision checks
        assertEquals(exception.getMessage(), "Script argument 'CONNID_ARG_foo' conflicts with environment transport for 'foo'");
    }

    @Test(dataProvider = "styles")
    public void testPrefixName(String style) {
        // Given: Prefixed name without another matching argument
        SshConfiguration configuration = configuration(style);
        Map<String, Object> arguments = Collections.singletonMap("CONNID_ARG_foo", "synthetic-value");

        // When: Process the standalone prefixed name
        ProcessedCommand command = process(configuration, arguments);

        // Then: Allow the noncolliding transport name
        assertEquals(command.envs(), Collections.singletonMap("CONNID_ARG_CONNID_ARG_foo", "synthetic-value"));
    }

    @Test
    public void testDistinctCase() {
        // Given: Bash names differing from transport case
        SshConfiguration configuration = configuration("variables-bash");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("foo", "synthetic-first");
        arguments.put("connid_arg_foo", "synthetic-second");
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("CONNID_ARG_foo", "synthetic-first");
        environment.put("CONNID_ARG_connid_arg_foo", "synthetic-second");

        // When: Process case-sensitive Bash argument names
        ProcessedCommand command = process(configuration, arguments);

        // Then: Preserve distinct variable identities
        assertEquals(command.envs(), environment);
    }

    @Test
    public void testPowerShellOverlap() {
        // Given: Separate PowerShell variable and environment namespaces
        SshConfiguration configuration = configuration("variables-powershell");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("foo", "synthetic-first");
        arguments.put("CONNID_ARG_foo", "synthetic-second");
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("CONNID_ARG_foo", "synthetic-first");
        environment.put("CONNID_ARG_CONNID_ARG_foo", "synthetic-second");

        // When: Process overlapping names across namespaces
        ProcessedCommand command = process(configuration, arguments);

        // Then: Allow independent PowerShell variable names
        assertEquals(command.envs(), environment);
    }

    @Test(dataProvider = "styles")
    public void testRawTail(String style) {
        // Given: Named argument and positional raw tail
        SshConfiguration configuration = configuration(style);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("foo", "synthetic-value");
        arguments.put(null, "literal; fragment");

        // When: Process named and positional arguments
        ProcessedCommand command = process(configuration, arguments);

        // Then: Preserve the raw positional tail
        assertEquals(command.envs(), Collections.singletonMap("CONNID_ARG_foo", "synthetic-value"));
        assertTrue(command.commandString().endsWith("execute literal; fragment"));
    }

    @Test(dataProvider = "invalidPrefixes")
    public void testInlinePrefix(String prefix) {
        // Given: Inline transport with unused invalid prefix
        SshConfiguration configuration = new SshConfiguration();
        configuration.setVariableTransportEnvPrefix(prefix);
        Map<String, Object> arguments = Collections.singletonMap("foo", "synthetic-value");

        // When: Process existing inline arguments
        ProcessedCommand command = process(configuration, arguments);

        // Then: Preserve the existing inline command
        assertEquals(command.commandString(), "execute -foo synthetic-value");
        assertEquals(command.envs(), Collections.emptyMap());
    }

    @Test
    public void testInlineName() {
        // Given: Existing inline argument with punctuation
        SshConfiguration configuration = new SshConfiguration();
        Map<String, Object> arguments = Collections.singletonMap("legacy-name", "synthetic-value");

        // When: Process existing inline argument
        ProcessedCommand command = process(configuration, arguments);

        // Then: Preserve existing argument name handling
        assertEquals(command.commandString(), "execute -legacy-name synthetic-value");
        assertEquals(command.envs(), Collections.emptyMap());
    }

    @Test
    public void testInlineOverlap() {
        // Given: Inline Bash arguments with overlapping names
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-bash");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("foo", "synthetic-first");
        arguments.put("CONNID_ARG_foo", "synthetic-second");

        // When: Process inline Bash variables
        ProcessedCommand command = process(configuration, arguments);

        // Then: Preserve both independent inline assignments
        assertTrue(command.commandString().contains("foo='synthetic-first';"));
        assertTrue(command.commandString().contains("CONNID_ARG_foo='synthetic-second';"));
        assertEquals(command.envs(), Collections.emptyMap());
    }

    private SshConfiguration configuration(String style) {
        SshConfiguration configuration = new SshConfiguration();
        configuration.setVariableTransport("env");
        configuration.setArgumentStyle(style);
        return configuration;
    }

    private ProcessedCommand process(SshConfiguration configuration, Map<String, Object> arguments) {
        return new CommandProcessor(configuration).process(new ScriptContext("shell", "execute", arguments));
    }

    private Object[][] combine(String[] values) {
        List<Object[]> cases = new ArrayList<>();
        for (String value : values) {
            cases.add(new Object[] {value, "variables-bash"});
            cases.add(new Object[] {value, "variables-powershell"});
        }
        return cases.toArray(new Object[cases.size()][]);
    }

    private ConfigurationException reject(Runnable action) {
        try {
            action.run();
        } catch (ConfigurationException exception) {
            return exception;
        }
        fail("Expected invalid environment name rejection");
        return null;
    }
}
