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

import net.schmizz.sshj.common.DisconnectReason;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import net.schmizz.sshj.userauth.UserAuthException;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
Exercises connector initialization, SSH host verification, authentication, and command execution against a loopback server.
Server keys are generated in memory; tests require no external service or key files.
*/
public class SshVerificationTest {
    private static final String HOST = "127.0.0.1";
    private static final String USERNAME = "connector-test";
    private static final String PASSWORD = "test-password";
    private static final String COMMAND = "verification-probe";
    private static final String RESPONSE = "host-verification-ok\n";
    private final AtomicInteger authentications = new AtomicInteger();
    private final List<String> commands = Collections.synchronizedList(new ArrayList<>());
    private KeyPair serverKey;
    private PublicKey otherKey;
    private SshServer server;

    @BeforeClass
    public void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        serverKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair().getPublic();
    }

    @BeforeMethod
    public void startServer() throws IOException {
        authentications.set(0);
        commands.clear();
        server = SshServer.setUpDefaultServer();
        server.setHost(HOST);
        server.setPort(0);
        server.setKeyPairProvider(KeyPairProvider.wrap(serverKey));
        server.setPasswordAuthenticator((username, password, session) -> {
            authentications.incrementAndGet();
            return USERNAME.equals(username) && PASSWORD.equals(password);
        });
        server.setCommandFactory((channel, command) -> new AbstractCommandSupport(command, null) {
            @Override
            public void run() {
                commands.add(command);
                try {
                    getOutputStream().write(RESPONSE.getBytes(StandardCharsets.UTF_8));
                    getOutputStream().flush();
                    onExit(0);
                } catch (IOException exception) {
                    onExit(1, exception.getMessage());
                }
            }
        });
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    public void stopServer() throws IOException {
        if (server != null) {
            server.stop(true);
        }
    }

    @Test(timeOut = 15000)
    public void testTrustedHost() throws Exception {
        // Given: Default verification and trusted key
        SshConfiguration configuration = configuration(new String[]{entry(HOST, serverKey.getPublic())});

        // When: Execute a connector command
        Object output = execute(configuration);

        // Then: Authenticate and execute successfully
        assertEquals(output, RESPONSE);
        assertEquals(authentications.get(), 1);
        assertEquals(commands, Collections.singletonList(COMMAND));
    }

    @DataProvider(name = "untrustedHosts")
    public Object[][] untrustedHosts() throws IOException {
        return new Object[][]{{entry(HOST, otherKey)}, {entry("untrusted.invalid", serverKey.getPublic())}};
    }

    @Test(dataProvider = "untrustedHosts", timeOut = 15000)
    public void testUntrustedHost(String knownHost) {
        // Given: Enabled verification and untrusted server
        SshConfiguration configuration = configuration(new String[]{knownHost});
        configuration.setKnownHostsVerification(true);

        // When: Attempt a connector command
        try {
            execute(configuration);
            fail("An untrusted server must be rejected");
        } catch (ConnectorException exception) {
            // Then: Reject before sending credentials
            TransportException failure = cause(exception, TransportException.class);
            assertEquals(failure.getDisconnectReason(), DisconnectReason.HOST_KEY_NOT_VERIFIABLE);
            assertEquals(authentications.get(), 0);
            assertTrue(commands.isEmpty());
        }
    }

    @DataProvider(name = "ignoredHosts")
    public Object[][] ignoredHosts() throws IOException {
        return new Object[][]{{null}, {new String[0]}, {new String[]{"invalid entry"}}, {new String[]{entry(HOST, otherKey)}},
                {new String[]{entry("untrusted.invalid", serverKey.getPublic())}}};
    }

    @Test(dataProvider = "ignoredHosts", timeOut = 15000)
    public void testDisabledVerification(String[] knownHosts) {
        // Given: Disabled verification with unusable keys
        SshConfiguration configuration = configuration(knownHosts);
        configuration.setKnownHostsVerification(false);

        // When: Execute a connector command
        Object output = execute(configuration);

        // Then: Accept server and authenticate normally
        assertEquals(output, RESPONSE);
        assertEquals(authentications.get(), 1);
        assertEquals(commands, Collections.singletonList(COMMAND));
    }

    @Test(timeOut = 15000)
    public void testAuthenticationRequired() {
        // Given: Disabled verification and invalid credentials
        SshConfiguration configuration = configuration(null);
        configuration.setKnownHostsVerification(false);
        configuration.setPassword(new GuardedString("wrong-password".toCharArray()));

        // When: Attempt a connector command
        try {
            execute(configuration);
            fail("Disabling host verification must not bypass authentication");
        } catch (ConnectorException exception) {
            // Then: Reject authentication without executing commands
            cause(exception, UserAuthException.class);
            assertTrue(authentications.get() > 0);
            assertTrue(commands.isEmpty());
        }
    }

    private SshConfiguration configuration(String[] knownHosts) {
        SshConfiguration configuration = new SshConfiguration();
        configuration.setHost(HOST);
        configuration.setPort(server.getPort());
        configuration.setUsername(USERNAME);
        configuration.setPassword(new GuardedString(PASSWORD.toCharArray()));
        configuration.setKnownHosts(knownHosts);
        return configuration;
    }

    private Object execute(SshConfiguration configuration) {
        SshConnector connector = new SshConnector();
        try {
            connector.init(configuration);
            return connector.runScriptOnResource(new ScriptContext("sh", COMMAND, null), null);
        } finally {
            connector.dispose();
        }
    }

    private String entry(String host, PublicKey key) throws IOException {
        return new OpenSSHKnownHosts.HostEntry(null, host, KeyType.RSA, key).getLine();
    }

    private <Failure extends Throwable> Failure cause(Throwable exception, Class<Failure> type) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        throw new AssertionError("Expected exception cause: " + type.getName(), exception);
    }
}
