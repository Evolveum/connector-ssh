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

public class SshUnixTest {

    private SshConfiguration createConfiguration() {
        SshConfiguration config = new SshConfiguration();

        config.setHost("localhost");
        config.setUsername("jack");
        config.setPassword(new GuardedString("qwe123".toCharArray()));

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
        // Nothing to assert here. If there is no exception then we are fine.
    }


    @Test
    public void testEcho() throws Exception {
        ConnectorFacade connector = setupConnector();

        ScriptContext context = new ScriptContext("bash", "echo \"Hello World\"", null);
        Object output = connector.runScriptOnResource(context, null);

        System.out.println("Script output: "+output);

        AssertJUnit.assertEquals("Hello World\n", output);
    }

    /**
     * Several script invocation on the same connector.
     * Exec mode should re-establish the session, session mode should keep the session.
     * Both commands should be working on the connector in any case.
     */
    @Test
    public void testMultiEcho() throws Exception {
        ConnectorFacade connector = setupConnector();

        // Executing "echo" by using arguments
        Map<String, Object> args1 = new HashMap<>();
        args1.put("n", null);
        args1.put(null, "\"Hello World\"");
        ScriptContext context1 = new ScriptContext("bash", "echo", args1);
        Object output1 = connector.runScriptOnResource(context1, null);

        System.out.println("Script output 1: "+output1);

        // Note: no \n here
        AssertJUnit.assertEquals("Hello World", output1);

        ScriptContext context2 = new ScriptContext("bash", "echo \"Have a nice doomsday\"", null);
        Object output2 = connector.runScriptOnResource(context2, null);

        System.out.println("Script output 2: "+output2);

        AssertJUnit.assertEquals("Have a nice doomsday\n", output2);
    }


}
