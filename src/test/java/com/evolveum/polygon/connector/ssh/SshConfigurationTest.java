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

import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class SshConfigurationTest {
    @Test
    public void testVerificationMetadata() {
        // Given: Default connector configuration
        SshConfiguration configuration = new SshConfiguration();

        // When: Discover connector configuration properties
        ConfigurationProperties properties = TestHelpers.createTestConfiguration(SshConnector.class, configuration).getConfigurationProperties();
        ConfigurationProperty verification = properties.getProperty("knownHostsVerification");
        List<String> names = properties.getPropertyNames();

        // Then: Expose enabled switch before keys
        assertEquals(verification.getType(), boolean.class);
        assertEquals(verification.getValue(), Boolean.TRUE);
        assertTrue(names.indexOf("knownHostsVerification") >= 0);
        assertEquals(names.indexOf("knownHosts"), names.indexOf("knownHostsVerification") + 1);
        assertFalse(properties.getProperty("knownHosts").isRequired(), "Known hosts are required only when verification is enabled");
    }

    @DataProvider(name = "ignoredHosts")
    public Object[][] ignoredHosts() {
        return new Object[][]{{null}, {new String[0]}, {new String[]{" \t\n # comment"}}, {new String[]{"invalid entry"}}, {new String[]{null}}};
    }

    @Test(dataProvider = "ignoredHosts")
    public void testDisabledValidation(String[] knownHosts) {
        // Given: Disabled verification and unusable keys
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.setKnownHosts(knownHosts);
        SshConnector connector = new SshConnector();
        try {
            // When: Validate and initialize the connector
            configuration.validate();
            connector.init(configuration);

            // Then: Accept configuration without usable keys
            assertSame(connector.getConfiguration(), configuration);
            assertFalse(configuration.getKnownHostsVerification());
        } finally {
            connector.dispose();
        }
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testRestoredValidation() {
        // Given: Disabled verification without keys
        SshConfiguration configuration = new SshConfiguration();
        configuration.setKnownHostsVerification(false);
        configuration.validate();

        // When: Enable verification again
        configuration.setKnownHostsVerification(true);

        // Then: Reject the missing host keys
        configuration.validate();
    }
}
