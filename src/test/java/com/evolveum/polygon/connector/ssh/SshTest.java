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
import org.testng.annotations.Test;

public class SshTest {

    private SshConfiguration createConfiguration() {
        SshConfiguration config = new SshConfiguration();

        config.setHost("localhost");
        config.setUsername("jack");
        config.setPassword(new GuardedString("secret".toCharArray()));

        return config;
    }

    private ConnectorFacade createConnectorFacade(SshConfiguration config) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration apiConfig = TestHelpers.createTestConfiguration(SshConnector.class, config);
        return factory.newInstance(apiConfig);
    }

    private ConnectorFacade setupConnector() {
        return createConnectorFacade(createConfiguration());
    }

    @Test
    public void testTest() throws Exception {
        ConnectorFacade connector = setupConnector();
        connector.test();

        // TODO asserts?
    }


    @Test
    public void testEcho() throws Exception {
        ConnectorFacade connector = setupConnector();

        ScriptContext context = new ScriptContext("bash", "echo \"Hello World\"", null);
        Object output = connector.runScriptOnResource(context, null);

        System.out.println("Script output: "+output);

        // TODO asserts?
    }


}
