package com.evolveum.polygon.connector.ssh;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Map;

public class BashTransportTest {

    @DataProvider
    public Object[][] transportedValues() {
        return new Object[][] {{"synthetic-secret"}, {"space ' \" $ ` ; \\ *\nnext"}, {""}};
    }

    @Test(dataProvider = "transportedValues")
    public void testBashCleanup(String value) {
        // Given: Bash transport and one argument
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-bash");
        configuration.setVariableTransport("env");
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("password", value);

        // When: Prepare the command and environment
        ProcessedCommand command = new CommandProcessor(configuration).process(new ScriptContext("bash", "provision", arguments));

        // Then: Copy before removing the environment
        Assert.assertEquals(command.commandString(), "password=$CONNID_ARG_password; unset CONNID_ARG_password; \nprovision");
        Assert.assertEquals(command.envs(), Collections.singletonMap("CONNID_ARG_password", value));
        Assert.assertEquals(command.toString(), "ProcessedCommand{commandString='" + command.commandString() + "', envs={CONNID_ARG_password=****}}");
    }

    @Test
    public void testCustomPrefix() {
        // Given: A custom transport namespace
        SshConfiguration configuration = new SshConfiguration();
        configuration.setArgumentStyle("variables-bash");
        configuration.setVariableTransport("env");
        configuration.setVariableTransportEnvPrefix("CUSTOM_");

        // When: Prepare a transported argument
        ScriptContext context = new ScriptContext("bash", "provision", Collections.<String, Object>singletonMap("account", "alice"));
        ProcessedCommand command = new CommandProcessor(configuration).process(context);

        // Then: Remove the configured environment name
        Assert.assertEquals(command.commandString(), "account=$CUSTOM_account; unset CUSTOM_account; \nprovision");
        Assert.assertEquals(command.envs(), Collections.singletonMap("CUSTOM_account", "alice"));
    }
}
