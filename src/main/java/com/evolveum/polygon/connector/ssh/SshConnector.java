/*
 * Copyright (c) 2015-2020 Evolveum
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

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.common.SchemaUtil;
import net.schmizz.sshj.Config;
import net.schmizz.sshj.ConfigImpl;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.userauth.UserAuthException;
//import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.TestOp;

import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ConnectorClass(displayNameKey = "connector.ssh.display", configurationClass = SshConfiguration.class)
public class SshConnector implements PoolableConnector, TestOp, ScriptOnResourceOp {

    private static final Log LOG = Log.getLog(SshConnector.class);

    private SshConfiguration configuration;
    private SSHClient ssh;
    private Session session = null;

    private String hostDesc;
    private String connectionDesc;

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration configuration) {
        LOG.info("Initializing {0} connector instance {1}", this.getClass().getSimpleName(), this);
        this.configuration = (SshConfiguration)configuration;
        connect();
    }

    private void connect() {
//        BouncyCastleProvider bcProvider = new BouncyCastleProvider();
//        Security.addProvider(bcProvider);
        LOG.ok("JCE providers: {0}", Arrays.toString(Security.getProviders()));
//        DefaultConfig sshConf = new DefaultConfig();
//        sshConf.
        ssh = new SSHClient();
        ssh.addHostKeyVerifier(new ConnectorKnownHostsVerifier().parse(configuration.getKnownHosts()));

        try {
            ssh.connect(configuration.getHost());
        } catch (IOException e) {
            LOG.error("Error creating SSH connection to {0}: {1}", getHostDesc(), e.getMessage());
            throw new ConnectionFailedException("Error creating SSH connection to " + getHostDesc() + ": " + e.getMessage(), e);
        }
        authenticate();
        try {
            session = ssh.startSession();
        } catch (ConnectionException | TransportException e) {
            LOG.error("Communication error while creating SSH session for {1} failed: {2}", getConnectionDesc(), e.getMessage());
            throw new ConnectionFailedException("Communication error while creating SSH session for "+getConnectionDesc()+" failed: " + e.getMessage(), e);
        }
    }

    private void authenticate() {
        switch (configuration.getAuthenticationScheme()) {
            case SshConfiguration.AUTHENTICATION_SCHEME_PASSWORD:
                authenticatePassword();
                break;
            case SshConfiguration.AUTHENTICATION_SCHEME_PUBLIC_KEY:
                authenticatePublicKey();
                break;
            default:
                throw new ConfigurationException("Unknown authentication scheme '"+configuration.getAuthenticationScheme()+"'");
        }
    }

    private void authenticatePassword() {
        GuardedString password = configuration.getPassword();
        if (password == null) {
            throw new ConfigurationException("No authentication password configured '"+configuration.getAuthenticationScheme()+"'");
        }
        password.access( passwordChars -> {
            try {
                ssh.authPassword(configuration.getUsername(), passwordChars);
            } catch (UserAuthException e) {
                LOG.error("SSH password authentication as {0} to {1} failed: {2}", configuration.getUsername(), getHostDesc(), e.getMessage());
                throw new ConnectionFailedException("SSH public key authentication as "+configuration.getUsername()+" to "+getHostDesc()+" failed: " + e.getMessage(), e);
            } catch (TransportException e) {
                LOG.error("Communication error during SSH password authentication as {0} to {1} failed: {2}", configuration.getUsername(), getHostDesc(), e.getMessage());
                throw new ConnectionFailedException("Communication error during SSH public key authentication as "+configuration.getUsername()+" to "+getHostDesc()+" failed: " + e.getMessage(), e);
            }
        });
    }

    private void authenticatePublicKey() {
        try {
            ssh.authPublickey(configuration.getUsername());
        } catch (UserAuthException e) {
            LOG.error("SSH public key authentication as {0} to {1} failed: {2}", configuration.getUsername(), getHostDesc(), e.getMessage());
            throw new ConnectionFailedException("SSH public key authentication as "+configuration.getUsername()+" to "+getHostDesc()+" failed: " + e.getMessage(), e);
        } catch (TransportException e) {
            LOG.error("Communication error during SSH public key authentication as {0} to {1} failed: {2}", configuration.getUsername(), getHostDesc(), e.getMessage());
            throw new ConnectionFailedException("Communication error during SSH public key authentication as "+configuration.getUsername()+" to "+getHostDesc()+" failed: " + e.getMessage(), e);
        }
    }

    private String getConnectionDesc() {
        if (connectionDesc == null) {
            connectionDesc = configuration.getUsername() + "@" + getHostDesc();
        }
        return connectionDesc;
    }

    private String getHostDesc() {
        if (hostDesc == null) {
            // TODO: port
            hostDesc = configuration.getHost();
        }
        return hostDesc;
    }

    @Override
    public void dispose() {
        if (session != null) {
            try {
                session.close();
            } catch (ConnectionException | TransportException e) {
                LOG.warn("Error closing SSH session for {0}: {1} (ignoring)", getConnectionDesc(), e.getMessage());
            }
        }
        try {
            ssh.disconnect();
        } catch (IOException e) {
            LOG.warn("Error disconnecting SSH session for {0}: {1} (ignoring)", getConnectionDesc(), e.getMessage());
        }
    }

    @Override
    public void test() {
        LOG.info("Test {0} connector instance {1}", this.getClass().getSimpleName(), this);
        checkAlive();
    }

    @Override
    public void checkAlive() {
//        TODO;
    }

    @Override
    public Object runScriptOnResource(ScriptContext scriptCtx, OperationOptions options) {
        String scriptLanguage = scriptCtx.getScriptLanguage();
        String command = scriptCtx.getScriptText();
        OperationLog.log("{0} Script REQ {1}: {2}", getConnectionDesc(), scriptLanguage, command);

        String output;
        try {

            output = exec(command, scriptCtx.getScriptArguments());

        } catch (Exception e) {
            OperationLog.error("{0} Script ERR {1}", getConnectionDesc(), e.getMessage());
            throw new ConnectorException("Script execution failed: "+e.getMessage(), e);
        }

        OperationLog.log("{0} Script RES {1}", getConnectionDesc(), (output==null||output.isEmpty())?"no output":("output "+output.length()+" chars"));
        LOG.ok("Script returned output\n{0}", output);

        return output;
    }

    private String exec(String command, Map<String, Object> scriptArguments) {

        // TODO: process scriptArguments

        final Session.Command cmd;
        try {
            cmd = session.exec(command);
        } catch (ConnectionException | TransportException e) {
            throw new ConnectorIOException("Network error while executing SSH command: "+e.getMessage(), e);
        }
        String output;
        try {
            output = IOUtils.readFully(cmd.getInputStream()).toString();
        } catch (IOException e) {
            throw new ConnectorIOException("Error reading output of SSH command: "+e.getMessage(), e);
        }

        try {
            cmd.join(5, TimeUnit.SECONDS);
        } catch (ConnectionException e) {
            throw new ConnectorIOException("Error \"joining\" SSH command: "+e.getMessage(), e);
        }

        LOG.info("SSH command exit status: {0}", cmd.getExitStatus());
        return output;
    }
}
