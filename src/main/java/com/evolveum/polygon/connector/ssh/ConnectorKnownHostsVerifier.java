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

import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class ConnectorKnownHostsVerifier implements HostKeyVerifier  {
    private final List<OpenSSHKnownHosts.KnownHostEntry> entries = new ArrayList<>();

    public ConnectorKnownHostsVerifier parse(final String[] knownHosts) {
        entries.clear();
        if (knownHosts == null || knownHosts.length == 0) {
            throw new ConfigurationException("knownHosts must contain at least one SSH host key entry");
        }
        final List<OpenSSHKnownHosts.KnownHostEntry> parsedEntries = new ArrayList<>();
        for (String knownHost : knownHosts) {
            if (knownHost == null) {
                throw new ConfigurationException("knownHosts must not contain null values");
            }
            try {
                final OpenSSHKnownHosts hosts = new OpenSSHKnownHosts(new StringReader(knownHost));
                for ( OpenSSHKnownHosts.KnownHostEntry entry : hosts.entries() ) {
                    if (entry instanceof OpenSSHKnownHosts.BadHostEntry) {
                        throw new ConfigurationException("knownHosts contains an invalid or unsupported entry");
                    }
                    if (entry instanceof OpenSSHKnownHosts.HostEntry) {
                        parsedEntries.add(entry);
                    }
                }
            } catch (IOException e) {
                throw new ConfigurationException("Error parsing knownHosts entry: " + e.getMessage(), e);
            }
        }
        if (parsedEntries.isEmpty()) {
            throw new ConfigurationException("knownHosts must contain at least one SSH host key entry");
        }
        entries.addAll(parsedEntries);
        return this;
    }


    @Override
    public boolean verify(String hostname, int port, PublicKey key) {
        if (entries.isEmpty()) {
            return false;
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
