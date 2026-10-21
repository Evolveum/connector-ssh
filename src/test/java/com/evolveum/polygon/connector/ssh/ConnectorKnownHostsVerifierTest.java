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

import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.testng.Assert.*;

public class ConnectorKnownHostsVerifierTest {
    private static final String HOST = "target.example";
    private PublicKey trustedKey;
    private PublicKey otherKey;
    private String trustedEntry;

    @BeforeClass
    public void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKey = generator.generateKeyPair().getPublic();
        otherKey = generator.generateKeyPair().getPublic();
        trustedEntry = entry(HOST, trustedKey);
    }

    private String entry(String host, PublicKey key) throws Exception {
        return new OpenSSHKnownHosts.HostEntry(null, host, KeyType.RSA, key).getLine();
    }

    @DataProvider(name = "invalidKnownHosts")
    public Object[][] invalidKnownHosts() {
        return new Object[][] {
                {null},
                {new String[0]},
                {new String[]{""}},
                {new String[]{" \t\n"}},
                {new String[]{"# comment only"}},
                {new String[]{null}},
                {new String[]{"invalid entry"}},
                {new String[]{"target.example unsupported-key AAAA"}},
                {new String[]{"target.example ssh-rsa AAAA"}}
        };
    }

    @Test(dataProvider = "invalidKnownHosts", expectedExceptions = ConfigurationException.class)
    public void rejectsInvalidConfiguration(String[] knownHosts) {
        new ConnectorKnownHostsVerifier().parse(knownHosts);
    }

    @Test(dataProvider = "invalidKnownHosts", expectedExceptions = ConfigurationException.class)
    public void configurationValidationRejectsInvalidKnownHosts(String[] knownHosts) {
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHosts(knownHosts);
        configuration.validate();
    }

    @Test(dataProvider = "invalidKnownHosts", expectedExceptions = ConfigurationException.class)
    public void connectorInitializationRejectsInvalidKnownHosts(String[] knownHosts) {
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHosts(knownHosts);
        new SshConnector().init(configuration);
    }

    @Test
    public void unconfiguredVerifierRejectsHost() {
        assertFalse(new ConnectorKnownHostsVerifier().verify(HOST, 22, otherKey));
    }

    @Test
    public void acceptsOnlyMatchingHostAndKey() {
        ConnectorKnownHostsVerifier verifier = new ConnectorKnownHostsVerifier().parse(new String[]{trustedEntry});
        assertTrue(verifier.verify(HOST, 22, trustedKey));
        assertFalse(verifier.verify(HOST, 22, otherKey));
        assertFalse(verifier.verify("other.example", 22, trustedKey));
    }

    @Test
    public void acceptsValidConfiguration() {
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHosts(new String[]{trustedEntry});
        configuration.validate();
        SshConnector connector = new SshConnector();
        connector.init(configuration);
        connector.dispose();
    }

    @Test
    public void acceptsMultipleValuesAndMultilineEntries() throws Exception {
        String secondEntry = entry("other.example", otherKey);
        for (String[] entries : new String[][] {
                {trustedEntry, secondEntry},
                {"# trusted servers\n\n" + trustedEntry + "\n" + secondEntry + "\n"}
        }) {
            ConnectorKnownHostsVerifier verifier = new ConnectorKnownHostsVerifier().parse(entries);
            assertTrue(verifier.verify(HOST, 22, trustedKey));
            assertTrue(verifier.verify("other.example", 22, otherKey));
            assertFalse(verifier.verify(HOST, 22, otherKey));
        }
    }

    @Test
    public void rejectsRevokedKey() {
        ConnectorKnownHostsVerifier verifier = new ConnectorKnownHostsVerifier()
                .parse(new String[]{"@revoked " + trustedEntry});
        assertFalse(verifier.verify(HOST, 22, trustedKey));
    }

    @Test
    public void reparsingReplacesTrustedKeys() throws Exception {
        ConnectorKnownHostsVerifier verifier = new ConnectorKnownHostsVerifier().parse(new String[]{trustedEntry});
        verifier.parse(new String[]{entry(HOST, otherKey)});
        assertFalse(verifier.verify(HOST, 22, trustedKey));
        assertTrue(verifier.verify(HOST, 22, otherKey));
    }

    @Test
    public void failedParsingDoesNotLeaveTrustedKeys() {
        ConnectorKnownHostsVerifier verifier = new ConnectorKnownHostsVerifier().parse(new String[]{trustedEntry});
        try {
            verifier.parse(new String[]{trustedEntry, "invalid entry"});
            fail("Malformed configuration must be rejected");
        } catch (ConfigurationException expected) {
            assertFalse(verifier.verify(HOST, 22, trustedKey));
        }
    }
}
