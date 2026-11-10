package com.evolveum.polygon.connector.ssh;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class ProcessedCommandTest {

    @Test
    public void testJavaBaseline() throws IOException {
        // Given: Classes loaded from the connector
        Class<?>[] classes = {SshConnector.class, SshConfiguration.class, CommandProcessor.class, ProcessedCommand.class};

        for (Class<?> type : classes) {
            // When: Read the compiled class header
            try (DataInputStream input = new DataInputStream(type.getResourceAsStream(type.getSimpleName() + ".class"))) {
                int magic = input.readInt();
                input.readUnsignedShort();
                int version = input.readUnsignedShort();

                // Then: Verify Java seventeen compatible bytecode
                Assert.assertEquals(magic, 0xCAFEBABE);
                Assert.assertEquals(version, 61, type.getName());
            }
        }
    }

    @Test
    public void testCommandRedaction() {
        // Given: A command with sensitive environment
        Map<String, String> values = Collections.singletonMap("CONNID_ARG_password", "synthetic-secret");
        ProcessedCommand command = new ProcessedCommand("provision", values);

        // When: Format the diagnostic representation
        String description = command.toString();

        // Then: Mask the environment value
        Assert.assertEquals(description, "ProcessedCommand{commandString='provision', envs={CONNID_ARG_password=****}}");
    }

    @Test
    public void testDefaultTransport() {
        // Given: Default settings and one argument
        SshConfiguration configuration = new SshConfiguration();
        ScriptContext context = new ScriptContext("bash", "provision", Collections.<String, Object>singletonMap("account", "alice"));

        // When: Prepare the existing command style
        ProcessedCommand command = new CommandProcessor(configuration).process(context);

        // Then: Preserve inline defaults without environments
        Assert.assertEquals(configuration.getVariableTransport(), "inplace");
        Assert.assertEquals(command.commandString(), "provision -account alice");
        Assert.assertEquals(command.envs(), Collections.emptyMap());
    }

    @Test
    public void testNoArguments() {
        // Given: A script without supplied arguments
        ScriptContext context = new ScriptContext("bash", "provision", null);

        // When: Prepare the command
        ProcessedCommand command = new CommandProcessor(new SshConfiguration()).process(context);

        // Then: Preserve script and empty environment
        Assert.assertEquals(command.commandString(), "provision");
        Assert.assertEquals(command.envs(), Collections.emptyMap());
    }
}
