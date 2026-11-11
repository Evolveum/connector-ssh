package com.evolveum.polygon.connector.ssh;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PowerShellShellTest {

    private String executable;

    @BeforeClass
    public void configureShell() {
        executable = System.getProperty("ssh.test.powershell");
        if (executable == null || executable.isEmpty()) {
            throw new SkipException("Set ssh.test.powershell to run PowerShell execution tests");
        }
    }

    @DataProvider
    public Object[][] transportedValues() {
        return new Object[][] {{"synthetic-secret"}, {"space ' \" $ ` ; \\ *\nnext"}, {""}, {null}};
    }

    @DataProvider
    public Object[][] emptyValues() {
        return new Object[][] {{""}, {null}};
    }

    @Test(dataProvider = "transportedValues")
    public void testShellValues(String value) throws Exception {
        // Given: Generated argument with empty null handling
        SshConfiguration configuration = configuration();
        configuration.setHandleNullValues("asEmptyString");
        String script = "if ($password -isnot [string]) { exit 51 }; "
                + "if ([Environment]::GetEnvironmentVariables().Contains('CONNID_ARG_password')) { exit 52 }; [Console]::Write($password)";
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", value);
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", script, arguments));

        // When: Execute the actual generated command
        ShellRunner.Result result = execute(command);

        // Then: Preserve string values and clear environments
        Assert.assertEquals(result.status, 0, result.error);
        Assert.assertEquals(result.error, "");
        Assert.assertEquals(result.output, value == null ? "" : value);
    }

    @Test(dataProvider = "emptyValues")
    public void testMixedValues(String value) throws Exception {
        // Given: Empty or null beside nonempty secret
        SshConfiguration configuration = configuration();
        configuration.setHandleNullValues("asEmptyString");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("emptyValue", value);
        arguments.put("password", "synthetic-secret");
        String script = "if ($emptyValue -isnot [string] -or $emptyValue.Length -ne 0) { exit 53 }; "
                + "if ([Environment]::GetEnvironmentVariables().Contains('CONNID_ARG_password')) { exit 54 }; [Console]::Write($password)";
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", script, arguments));

        // When: Execute both generated assignments
        ShellRunner.Result result = execute(command);

        // Then: Preserve both values regardless of order
        Assert.assertEquals(result.status, 0, result.error);
        Assert.assertEquals(result.error, "");
        Assert.assertEquals(result.output, "synthetic-secret");
    }

    @Test
    public void testNullOmitted() throws Exception {
        // Given: Null argument configured as absent
        String script = "if ($null -ne $ExecutionContext.SessionState.PSVariable.Get('password')) { exit 55 }; "
                + "if ([Environment]::GetEnvironmentVariables().Contains('CONNID_ARG_password')) { exit 56 }; [Console]::Write('omitted')";
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", null);
        ProcessedCommand command = new CommandProcessor(configuration()).process(new ScriptContext("powershell", script, arguments));

        // When: Execute the script without assignment
        ShellRunner.Result result = execute(command);

        // Then: Keep omitted argument and environment absent
        Assert.assertEquals(result.status, 0, result.error);
        Assert.assertEquals(result.error, "");
        Assert.assertEquals(result.output, "omitted");
    }

    @Test
    public void testInlineDefaults() throws Exception {
        // Given: Default inline transport with special characters
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-powershell");
        String value = "space ' \" $ ` ; \\ *\nnext";
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", value);
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("powershell", "[Console]::Write($password)", arguments));

        // When: Execute the default inline command
        ShellRunner.Result result = execute(command);

        // Then: Preserve the existing inline value
        Assert.assertEquals(result.status, 0, result.error);
        Assert.assertEquals(result.error, "");
        Assert.assertEquals(result.output, value);
    }

    private SshConfiguration configuration() {
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-powershell");
        configuration.setVariableTransport("env");
        return configuration;
    }

    private ShellRunner.Result execute(ProcessedCommand command) throws Exception {
        String encoded = Base64.getEncoder().encodeToString(command.commandString().getBytes(StandardCharsets.UTF_16LE));
        return ShellRunner.run(Arrays.asList(executable, "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded), command);
    }
}
