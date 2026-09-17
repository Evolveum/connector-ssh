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

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

import java.util.Objects;

/**
 * SSH connector configuration.
 *
 * @author Radovan Semancik
 *
 */
public class SshConfiguration extends AbstractConfiguration {

    /**
     * Server hostname.
     */
    private String host;

    /**
     * Server port.
     */
    private int port = 22;

    /**
     * Username of the user, used for authentication.
     */
    private String username;

    /**
     * User password.
     */
    private GuardedString password = null;

    /**
     * Private Key for public key authentication.
     * The format is PKCS8.
     */
    private GuardedString privateKey = null;

    /**
     * Passphrase for public key authentication.
     */
    private GuardedString passphrase = null;

    /**
     * Authentication scheme for the SSH connection.
     */
    private String authenticationScheme = AUTHENTICATION_SCHEME_PASSWORD;

    public static final String AUTHENTICATION_SCHEME_PASSWORD = "password";
    public static final String AUTHENTICATION_SCHEME_PUBLIC_KEY = "publicKey"; // Not completely implemented yet

    /**
     * Known hosts in the usual "known_hosts" file format.
     * This may be either one value, entries separated by newlines (as they are in the file).
     * Or there may be multiple values, each of them containing a single known host entry.
     *
     * WARNING: If the knownHosts property is empty (has no values), the the connector blindly accepts any host.
     */
    private String[] knownHosts;

    /**
     * Argument style, used to transform script arguments to command-line.
     */
    private String argumentStyle = ARGUMENT_STYLE_DASH;

    // command -f foo -b bar
    public static final String ARGUMENT_STYLE_DASH = "dash";

    // command --fu=foo --bar=baz
//    public static final String ARGUMENT_STYLE_DASHDASH = "dashdash";

    // command /f foo /b bar
    public static final String ARGUMENT_STYLE_SLASH = "slash";

    // $fu='foo'; $bar='baz'; command $foo $bar
    public static final String ARGUMENT_STYLE_VARIABLES_POWERSHELL = "variables-powershell";

    // fu='foo'; bar='baz'; command $foo $bar
    public static final String ARGUMENT_STYLE_VARIABLES_BASH = "variables-bash";

    /**
     * Defines how to handle NULL value arguments.
     * If a script argument is NULL, it can be inserted as an empty string ("asEmpty") or it can be removed from the argument list ("asGone").
     */
    private String handleNullValues = HANDLE_NULL_AS_GONE;

    public static final String HANDLE_NULL_AS_EMPTY_STRING = "asEmptyString";
    public static final String HANDLE_NULL_AS_GONE = "asGone";

    /**
     * Defines how arguments will be transported into the script:
     * <ul>
     *     <li>[default] "inplace" means that arguments will be inserted into the script as variables or parameters, wrapped by single quotes.</li>
     *     <li>"env" means that arguments will be transported as environment variables. This is only supported for
     *     the argument styles based on variables (variables-powershell, variables-bash). Make sure that the sshd_config
     *     of the ssh daemon is configured to allow setting envs via ssh.  To prevent name clashes, the environment variable names
     *     will be prefixed by the value of variableTransportEnvPrefix property (default: CONNID_ARG_).
     *     <p>Example sshd_config: <pre>AcceptEnv CONNID_ARG_*</pre></p>
     *     </li>
     * </ul>
     *
     * @see <a href="https://man.openbsd.org/sshd_config#AcceptEnv">sshd_config man page</a>
     */
    private String variableTransport = VARIABLE_TRANSPORT_INPLACE;

    public static final String VARIABLE_TRANSPORT_INPLACE = "inplace";
    public static final String VARIABLE_TRANSPORT_ENV = "env";

    /**
     * Prefix for environment variables used for argument transport, if variableTransport is set to "env".
     * Make sure that the sshd_config of the ssh daemon is configured to allow setting envs with the given prefix via ssh.
     * Make sure that the prefix does not collide with other environment variable names.
     * Default: CONNID_ARG_
     */
    private String variableTransportEnvPrefix = "CONNID_ARG_";

    @ConfigurationProperty(order = 100)
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @ConfigurationProperty(order = 101)
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @ConfigurationProperty(order = 102)
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @ConfigurationProperty(order = 103)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

    @ConfigurationProperty(order = 104)
    public GuardedString getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(GuardedString privateKey) {
        this.privateKey = privateKey;
    }

    @ConfigurationProperty(order = 105)
    public GuardedString getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(GuardedString passphrase) {
        this.passphrase = passphrase;
    }

    @ConfigurationProperty(order = 106)
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }

    public void setAuthenticationScheme(String authenticationScheme) {
        this.authenticationScheme = authenticationScheme;
    }

    @ConfigurationProperty(order = 110)
    public String[] getKnownHosts() {
        return knownHosts;
    }

    public void setKnownHosts(String[] knownHosts) {
        this.knownHosts = knownHosts;
    }

    @ConfigurationProperty(order = 120)
    public String getArgumentStyle() {
        return argumentStyle;
    }

    public void setArgumentStyle(String argumentStyle) {
        this.argumentStyle = argumentStyle;
    }

    @ConfigurationProperty(order = 130)
    public String getHandleNullValues() {
        return handleNullValues;
    }

    public void setHandleNullValues(String handleNullValues) {
        this.handleNullValues = handleNullValues;
    }

    @ConfigurationProperty(order = 140)
    public String getVariableTransport() {
        return variableTransport;
    }

    public void setVariableTransport(String variableTransport) {
        this.variableTransport = variableTransport;
    }

    @ConfigurationProperty(order = 150)
    public String getVariableTransportEnvPrefix() {
        return variableTransportEnvPrefix;
    }

    public void setVariableTransportEnvPrefix(String variableTransportEnvPrefix) {
        this.variableTransportEnvPrefix = variableTransportEnvPrefix;
    }

    @Override
    public void validate() {
        if (Objects.equals(variableTransport, VARIABLE_TRANSPORT_ENV) &&
            !(ARGUMENT_STYLE_VARIABLES_BASH.equals(argumentStyle) ||
              ARGUMENT_STYLE_VARIABLES_POWERSHELL.equals(argumentStyle))) {
            throw new IllegalArgumentException(
                    "variableTransport set to 'env' is only supported for argument styles '" +
                    ARGUMENT_STYLE_VARIABLES_BASH + "' and '" + ARGUMENT_STYLE_VARIABLES_POWERSHELL + "'");
        }

    }

}
