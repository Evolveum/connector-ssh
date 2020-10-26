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

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractSshTest {

    private SshConfiguration createConfiguration() {
        SshConfiguration config = new SshConfiguration();

        config.setHost(getHostname());
        config.setUsername(getUsername());
        config.setPassword(new GuardedString(getPassword().toCharArray()));
        addToConnectorConfiguration(config);

        return config;
    }

    protected abstract String getHostname();

    protected abstract String getUsername();

    protected abstract String getPassword();

    protected abstract String getLaguage();

    protected void addToConnectorConfiguration(SshConfiguration config) {
    }


    private ConnectorFacade createConnectorFacade(SshConfiguration config) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration apiConfig = TestHelpers.createTestConfiguration(SshConnector.class, config);
        return factory.newInstance(apiConfig);
    }

    protected ConnectorFacade setupConnector() {
        return createConnectorFacade(createConfiguration());
    }

    @Test
    public void testTest() throws Exception {
        ConnectorFacade connector = setupConnector();
        connector.test();
        // Nothing to assert here. If there is no exception then we are fine.
    }


    @Test
    public void testEcho() throws Exception {
        ConnectorFacade connector = setupConnector();

        ping(connector);
    }

    protected abstract void ping(ConnectorFacade connector);
}
