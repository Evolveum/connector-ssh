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

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

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

//    private int port = DEFAULT_PORT;

    /**
     * Username of the user, used for authentication.
     */
    private String username;

    /**
     * User password.
     */
    private GuardedString password = null;

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

    @ConfigurationProperty(order = 100)
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @ConfigurationProperty(order = 101)
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @ConfigurationProperty(order = 102)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

    @ConfigurationProperty(order = 103)
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

    @Override
    public void validate() {
    }

}
