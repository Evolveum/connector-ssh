/*
 * Copyright (c) 2020 Evolveum
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
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class ConnectorKnownHostsVerifier implements HostKeyVerifier  {
    private final List<OpenSSHKnownHosts.KnownHostEntry> entries = new ArrayList<>();

    public ConnectorKnownHostsVerifier parse(final String[] knownHosts) {
        if (knownHosts == null) {
            return this;
        }
        for (String knownHost : knownHosts) {
            try {
                final OpenSSHKnownHosts hosts = new OpenSSHKnownHosts(new StringReader(knownHost));
                for ( OpenSSHKnownHosts.KnownHostEntry entry : hosts.entries() ) {
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            } catch (IOException e) {
                throw new ConfigurationException("Error parsing known hosts entry "+knownHost+": "+e.getMessage(), e);
            }
        }

        return this;
    }


    @Override
    public boolean verify(String hostname, int port, PublicKey key) {
        if (entries.isEmpty()) {
            return true;
        }

        final KeyType type = KeyType.fromKey(key);
        if (type == KeyType.UNKNOWN) {
            return false;
        }

        for (OpenSSHKnownHosts.KnownHostEntry entry : entries) {
            try {
                if (entry.appliesTo(type, hostname) && entry.verify(key)) {
                    return true;
                }
            } catch (IOException e) {
                throw new ConfigurationException("Error verifying known hosts entry "+e+": "+e.getMessage(), e);
            }
        }
        return false;
    }
}
