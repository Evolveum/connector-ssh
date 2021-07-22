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
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
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
    private HostKeyVerifier hostKeyVerifier;
    private CommandProcessor commandProcessor;

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
        this.hostKeyVerifier = new ConnectorKnownHostsVerifier().parse(this.configuration.getKnownHosts());
        this.commandProcessor = new CommandProcessor(this.configuration);
    }

    private void connect() {
        ssh = new SSHClient();
        ssh.addHostKeyVerifier(hostKeyVerifier);
        LOG.ok("Connecting to {0}", getConnectionDesc());
        try {
            ssh.connect(configuration.getHost());
        } catch (IOException e) {
            LOG.error("Error creating SSH connection to {0}: {1}", getHostDesc(), e.getMessage());
            throw new ConnectionFailedException("Error creating SSH connection to " + getHostDesc() + ": " + e.getMessage(), e);
        }
        authenticate();
        LOG.ok("Authentication to {0} successful", getConnectionDesc());
        try {
            session = ssh.startSession();
        } catch (ConnectionException | TransportException e) {
            LOG.error("Communication error while creating SSH session for {1} failed: {2}", getConnectionDesc(), e.getMessage());
            throw new ConnectionFailedException("Communication error while creating SSH session for "+getConnectionDesc()+" failed: " + e.getMessage(), e);
        }
        LOG.info("Connection to {0} fully established", getConnectionDesc());
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
        LOG.ok("Authenticating to {0} using password authentication", getConnectionDesc());
        password.access( passwordChars -> {
            try {
                ssh.authPassword(configuration.getUsername(), passwordChars);
            } catch (UserAuthException e) {
                LOG.error("SSH password authentication as {0} to {1} failed: {2}", configuration.getUsername(), getHostDesc(), e.getMessage());
                throw new ConnectionFailedException("SSH password authentication as "+configuration.getUsername()+" to "+getHostDesc()+" failed: " + e.getMessage(), e);
            } catch (TransportException e) {
                LOG.error("Communication error during SSH password authentication as {0} to {1} failed: {2}", configuration.getUsername(), getHostDesc(), e.getMessage());
                throw new ConnectionFailedException("Communication error during SSH public key authentication as "+configuration.getUsername()+" to "+getHostDesc()+" failed: " + e.getMessage(), e);
            }
        });
    }

    private void authenticatePublicKey() {
        LOG.ok("Authenticating to {0} using public key authentication", getConnectionDesc());
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

    private void disconnect() {
        if (session != null && session.isOpen()) {
            LOG.ok("Closing session to {0}", getConnectionDesc());
            try {
                session.close();
            } catch (ConnectionException | TransportException e) {
                LOG.warn("Error closing SSH session for {0}: {1} (ignoring)", getConnectionDesc(), e.getMessage());
            }
            session = null;
        }
        if (ssh.isConnected()) {
            LOG.ok("Disconnecting from {0}", getConnectionDesc());
            try {
                ssh.disconnect();
            } catch (IOException e) {
                LOG.warn("Error disconnecting SSH session for {0}: {1} (ignoring)", getConnectionDesc(), e.getMessage());
            }
            LOG.info("Connection to {0} disconnected", getConnectionDesc());
        }
        ssh = null;
    }

    @Override
    public void dispose() {
        disconnect();
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
        String processedCommand = commandProcessor.process(scriptCtx);

        OperationLog.log("{0} Script REQ {1}: {2}", getConnectionDesc(), scriptLanguage, processedCommand);

        String output;
        try {

            output = exec(processedCommand);

        } catch (Exception e) {
            OperationLog.error("{0} Script ERR {1}", getConnectionDesc(), e.getMessage());
            throw new ConnectorException("Script execution failed: "+e.getMessage(), e);
        }

        OperationLog.log("{0} Script RES: {1}", getConnectionDesc(), (output==null||output.isEmpty())?"no output":("output "+output.length()+" chars"));
        LOG.ok("Script returned output\n{0}", output);

        return output;
    }

    // Exec can be run only once in each session. We need to explicitly connect and disconnect each time.
    private String exec(String processedCommand) {

        connect();

        final Session.Command cmd;
        try {
            cmd = session.exec(processedCommand);
        } catch (ConnectionException | TransportException e) {
            throw new ConnectorIOException("Network error while executing SSH command: "+e.getMessage(), e);
        }
        String output;
        String error;
        String errorMsg;
        try {
            LOG.info("---- executing ssh command -----------");
            LOG.info("processedCommand: {0} ", processedCommand);
            output = IOUtils.readFully(cmd.getInputStream()).toString();
            LOG.info("SSH command ouput: {0}", output);
            error = IOUtils.readFully(cmd.getErrorStream()).toString();
            LOG.info("SSH command error: {0}", error);
            LOG.info("command error: {0}", error);
            LOG.info("command exitErrorMsg: {0}", cmd.getExitErrorMessage());
            LOG.info("command exitStatus: {0}", cmd.getExitStatus());
            LOG.info("command exitSignal: {0}", cmd.getExitSignal());
            LOG.info("--------------------------------------");

            //throwing Exception based on exitStatus (e.g. !Integer.valueOf(0).equals(cmd.getExitStatus()) ) was not feasible
            // - calling powershell successfully returned exitCode null
            // - there may be return codes <> 0 having empty errorstream. E.g. calling grep (linux) having empty result
            // simple solution: throw Exception if there is something in error stream
            if (!error.isEmpty()){
                LOG.error("---- error executing ssh command ----");
                LOG.error("-- processedCommand: {0} ", processedCommand);
                LOG.error("-- command ouput: {0}", output);
                LOG.error("-- command error: {0}", error);
                LOG.error("-- command exitErrorMsg: {0}", cmd.getExitErrorMessage());
                LOG.error("-- command exitStatus: {0}", cmd.getExitStatus());
                LOG.error("-- command exitSignal: {0}", cmd.getExitSignal());
                LOG.error("--------------------------------------");
                throw new ConnectorException("Error executing SSH command: "+ error);
            }
        } catch (IOException e) {
            throw new ConnectorIOException("Error reading output of SSH command: "+e.getMessage(), e);
        }

        try {
            cmd.join(5, TimeUnit.SECONDS);
        } catch (ConnectionException e) {
            throw new ConnectorIOException("Error \"joining\" SSH command: "+e.getMessage(), e);
        }

        LOG.info("SSH command exit status: {0}", cmd.getExitStatus());

        disconnect();

        return output;
    }
}
