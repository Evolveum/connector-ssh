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

import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Test for Windows servers.
 *
 * This assumes that powershell is set as default shell on server.
 *
 * The tests are disabled by default, as they need a special server-side setup.
 */
public class SshWindowsTest extends AbstractSshTest {

    private static final int PERF_ATTEMPTS = 10;

    @Override
    protected String getHostname() {
        return "ad03.ad2019.lab.evolveum.com";
    }

    @Override
    protected String getUsername() {
        return "sshtest";
    }

    @Override
    protected String getPassword() {
        return "XXXXXXXXXXXXXXX";
    }

    protected String getLaguage() {
        return "powershell";
    }

    protected void addToConnectorConfiguration(SshConfiguration config) {
        config.setArgumentStyle("variables-powershell");
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
        args1.put("foo", "bar");
        args1.put(null, "baz");
        ScriptContext context1 = new ScriptContext(getLaguage(), "echo $foo", args1);
        Object output1 = connector.runScriptOnResource(context1, null);

        System.out.println("Script output 1: "+output1);

        AssertJUnit.assertEquals("bar\r\nbaz\r\n", output1);

        ScriptContext context2 = new ScriptContext(getLaguage(), "echo 'Have a nice doomsday'", null);
        Object output2 = connector.runScriptOnResource(context2, null);

        System.out.println("Script output 2: "+output2);

        AssertJUnit.assertEquals("Have a nice doomsday\r\n", output2);
    }

    @Test
    public void testPerformance() throws Exception {
        ConnectorFacade connector = setupConnector();

        long startTs = System.currentTimeMillis();
        for (int i = 0; i <= PERF_ATTEMPTS; i++) {
            ping(connector);
        }
        long endTs = System.currentTimeMillis();

        System.out.println("PERF: "+PERF_ATTEMPTS+" attempts in "+(endTs-startTs)+"ms: "+((endTs-startTs)/PERF_ATTEMPTS)+"ms/attempt");
    }

    protected void ping(ConnectorFacade connector) {

        ScriptContext context = new ScriptContext(getLaguage(), "echo 'Hello World'", null);
        Object output = connector.runScriptOnResource(context, null);

        System.out.println("Script output: "+output);

        AssertJUnit.assertEquals("Hello World\r\n", output);

    }

}
