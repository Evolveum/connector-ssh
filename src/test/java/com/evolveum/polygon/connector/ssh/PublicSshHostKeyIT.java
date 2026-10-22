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

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.DisconnectReason;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPairGenerator;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Explicitly selected live SSH handshake tests. No authentication or command execution.
 * The IT suffix keeps this class out of the default Surefire test selection.
 */
public class PublicSshHostKeyIT {
    private String[] knownHosts;

    @BeforeClass
    public void configure() throws Exception {
        knownHosts = new String[]{new String(Files.readAllBytes(
                Paths.get(requiredProperty("ssh.test.knownHostsFile"))), StandardCharsets.UTF_8)};
        new ConnectorKnownHostsVerifier().parse(knownHosts);
    }

    @DataProvider(name = "endpoints")
    public Object[][] endpoints() {
        if (System.getProperty("ssh.test.host") == null) {
            if (System.getProperty("ssh.test.port") != null) {
                throw new IllegalArgumentException("ssh.test.port requires ssh.test.host");
            }
            return new Object[][]{{"github.com", 22}};
        }
        int port = Integer.parseInt(System.getProperty("ssh.test.port", "22"));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("ssh.test.port must be between 1 and 65535");
        }
        return new Object[][]{{requiredProperty("ssh.test.host"), port}};
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("This live test requires -D" + name);
        }
        return value;
    }

    private SSHClient client(HostKeyVerifier verifier) {
        SSHClient client = new SSHClient();
        client.setConnectTimeout(10000);
        client.setTimeout(10000);
        client.addHostKeyVerifier(verifier);
        return client;
    }

    @Test(dataProvider = "endpoints", timeOut = 30000)
    public void acceptsPublishedHostKeyWithoutAuthentication(String host, int port) throws Exception {
        ConnectorKnownHostsVerifier verifier = new ConnectorKnownHostsVerifier().parse(knownHosts);
        AtomicBoolean verified = new AtomicBoolean();
        try (SSHClient client = client((hostname, serverPort, key) -> {
            boolean accepted = verifier.verify(hostname, serverPort, key);
            verified.set(accepted);
            return accepted;
        })) {
            client.connect(host, port);
            assertTrue(verified.get(), "The connector must verify the actual server key");
            assertTrue(client.isConnected());
            assertFalse(client.isAuthenticated(), "This test must not authenticate");
        }
    }

    @Test(dataProvider = "endpoints", timeOut = 30000)
    public void rejectsHostWhenNoKeysAreConfigured(String host, int port) throws Exception {
        assertRejected(new ConnectorKnownHostsVerifier(), host, port);
    }

    @Test(dataProvider = "endpoints", timeOut = 30000)
    public void rejectsHostWithDifferentKey(String host, int port) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String hostname = port == 22 ? host : "[" + host + "]:" + port;
        String wrongKey = new OpenSSHKnownHosts.HostEntry(null, hostname, KeyType.RSA,
                generator.generateKeyPair().getPublic()).getLine();
        assertRejected(new ConnectorKnownHostsVerifier().parse(new String[]{wrongKey}), host, port);
    }

    @Test(dataProvider = "endpoints", timeOut = 30000)
    public void rejectsKeyForDifferentHostname(String host, int port) throws Exception {
        ConnectorKnownHostsVerifier verifier = new ConnectorKnownHostsVerifier().parse(knownHosts);
        assertRejected((hostname, serverPort, key) -> verifier.verify("untrusted.invalid", serverPort, key), host, port);
    }

    private void assertRejected(HostKeyVerifier verifier, String host, int port) throws Exception {
        AtomicBoolean rejected = new AtomicBoolean();
        try (SSHClient client = client((hostname, serverPort, key) -> {
            boolean accepted = verifier.verify(hostname, serverPort, key);
            rejected.set(!accepted);
            return accepted;
        })) {
            try {
                client.connect(host, port);
                fail("The SSH handshake must reject the server key");
            } catch (TransportException expected) {
                assertEquals(expected.getDisconnectReason(), DisconnectReason.HOST_KEY_NOT_VERIFIABLE,
                        "A timeout or unrelated network failure must not pass this test");
                assertTrue(rejected.get(), "The connector verifier must have rejected the server key");
            }
        }
    }
}
