package com.evolveum.polygon.connector.ssh;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;

public class BashShellTest {

    private String executable;

    @BeforeClass
    public void configureShell() {
        executable = System.getProperty("ssh.test.bash");
        if (executable == null || executable.isEmpty()) {
            throw new SkipException("Set ssh.test.bash to run Bash execution tests");
        }
    }

    @DataProvider
    public Object[][] transportedValues() {
        return new Object[][] {{"synthetic-secret"}, {"space ' \" $ ` ; \\ *\nnext"}, {""}};
    }

    @Test(dataProvider = "transportedValues")
    public void testBashExecution(String value) throws Exception {
        // Given: Generated command with a transported value
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-bash");
        configuration.setVariableTransport("env");
        String script = "if [[ -v CONNID_ARG_password ]]; then exit 51; fi; printf '%s' \"$password\"";
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("bash", script, Collections.<String, Object>singletonMap("password", value)));

        // When: Execute with the actual Bash interpreter
        ShellRunner.Result result = ShellRunner.runBash(executable, command);

        // Then: Preserve values and clear transport variables
        Assert.assertEquals(result.status, 0, result.error);
        Assert.assertEquals(result.error, "");
        Assert.assertEquals(result.output, value);
    }

    @Test
    public void testChildEnvironment() throws Exception {
        // Given: A script that launches another shell
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-bash");
        configuration.setVariableTransport("env");
        String script = "\"$BASH\" --noprofile --norc -c 'if [[ -v CONNID_ARG_password ]]; then exit 52; fi; printf cleared'";
        ScriptContext context = new ScriptContext("bash", script, Collections.<String, Object>singletonMap("password", "synthetic-secret"));
        ProcessedCommand command = new CommandProcessor(configuration).process(context);

        // When: Execute the command and child shell
        ShellRunner.Result result = ShellRunner.runBash(executable, command);

        // Then: Prevent child inheritance of transported secrets
        Assert.assertEquals(result.status, 0, result.error);
        Assert.assertEquals(result.error, "");
        Assert.assertEquals(result.output, "cleared");
    }
}
