/*
 * Copyright (c) 2026 Evolveum
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

import net.schmizz.sshj.connection.ConnectionException;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.RequestHandler;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.channel.ChannelSessionFactory;
import org.apache.sshd.server.command.Command;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SshLifecycleTest {

    @DataProvider(name = "rejections")
    public Object[][] rejectedRequests() {
        return new Object[][] {{1}, {2}};
    }

    @Test(dataProvider = "rejections")
    public void testRejectedEnvironment(int rejectedRequest) throws Exception {
        // Given: Server rejects an environment request
        try (TestServer server = new TestServer()) {
            server.rejectedRequest = rejectedRequest;
            ScriptContext script = createScript();
            Map<String, String> expectedValues = expectedValues();

            // When: Execute with rejected environment setup
            ConnectorException failure = null;
            try {
                server.connector.runScriptOnResource(script, null);
            } catch (ConnectorException exception) {
                failure = exception;
            }

            // Then: Preserve failure and close connection
            Assert.assertNotNull(failure, "Rejected environment requests must fail");
            Assert.assertEquals(failure.getCause().getClass(), ConnectorIOException.class);
            Assert.assertEquals(failure.getCause().getCause().getClass(), ConnectionException.class);
            Assert.assertEquals(failure.getCause().getCause().getMessage(), "Request failed");
            Assert.assertEquals(server.requests.size(), rejectedRequest);
            Assert.assertEquals(server.acceptedValues.size(), rejectedRequest - 1);
            for (Map.Entry<String, String> request : server.requests) {
                Assert.assertEquals(request.getValue(), expectedValues.get(request.getKey()));
            }
            if (rejectedRequest == 2) {
                Map.Entry<String, String> accepted = server.requests.get(0);
                Assert.assertEquals(server.acceptedValues, Collections.singletonMap(accepted.getKey(), accepted.getValue()));
            }
            Assert.assertTrue(server.commands.isEmpty(), "Rejected setup must never execute a command");
            server.awaitClosed();

            // Given: Server now accepts every variable
            server.rejectedRequest = 0;
            server.requests.clear();
            server.acceptedValues.clear();

            // When: Retry with the same connector
            Object output = server.connector.runScriptOnResource(script, null);

            // Then: Execute successfully without retained connections
            Assert.assertEquals(output, "completed");
            Assert.assertEquals(server.acceptedValues, expectedValues);
            Assert.assertEquals(server.requests.size(), 2);
            Assert.assertEquals(server.commands.size(), 1);
            server.awaitClosed();
        }
    }

    @Test
    public void testSuccessfulEnvironment() throws Exception {
        // Given: Server accepts environment variables
        try (TestServer server = new TestServer()) {
            ScriptContext script = createScript();

            // When: Execute with transported variables
            Object output = server.connector.runScriptOnResource(script, null);

            // Then: Return output and close connection
            Assert.assertEquals(output, "completed");
            Assert.assertEquals(server.acceptedValues, expectedValues());
            Assert.assertEquals(server.requests.size(), 2);
            Assert.assertEquals(server.commands.size(), 1);
            Assert.assertTrue(server.commands.get(0).endsWith("lifecycle"));
            server.awaitClosed();
        }
    }

    private ScriptContext createScript() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("first", "first-secret");
        arguments.put("second", "second-secret");
        return new ScriptContext("bash", "lifecycle", arguments);
    }

    private Map<String, String> expectedValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CONNID_ARG_first", "first-secret");
        values.put("CONNID_ARG_second", "second-secret");
        return values;
    }

    /**
    Exercises the real SSH protocol with pinned host keys and a controlled environment rejection policy.
    */
    private static final class TestServer implements AutoCloseable {
        private final SshServer server = SshServer.setUpDefaultServer();
        private final SshConnector connector = new SshConnector();
        private final BlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
        private final List<Map.Entry<String, String>> requests = new CopyOnWriteArrayList<>();
        private final Map<String, String> acceptedValues = new ConcurrentHashMap<>();
        private final List<String> commands = new CopyOnWriteArrayList<>();
        private volatile int rejectedRequest;

        private TestServer() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair key = generator.generateKeyPair();
            server.setHost("127.0.0.1");
            server.setPort(0);
            server.setKeyPairProvider(session -> Collections.singletonList(key));
            server.setPasswordAuthenticator((username, password, session) -> "test-user".equals(username) && "test-password".equals(password));
            server.addSessionListener(new SessionListener() {
                @Override
                public void sessionCreated(Session session) {
                    sessions.add(session);
                }
            });
            server.setChannelFactories(Collections.singletonList(new ChannelSessionFactory() {
                @Override
                public Channel createChannel(Session session) {
                    return new ChannelSession() {
                        @Override
                        protected RequestHandler.Result handleEnvParsed(String name, String value) throws IOException {
                            requests.add(new AbstractMap.SimpleImmutableEntry<>(name, value));
                            if (requests.size() == rejectedRequest) {
                                return RequestHandler.Result.ReplyFailure;
                            }
                            acceptedValues.put(name, value);
                            return super.handleEnvParsed(name, value);
                        }
                    };
                }
            }));
            server.setCommandFactory((channel, command) -> {
                commands.add(command);
                return new ReplyCommand();
            });
            server.start();
            SshConfiguration configuration = new SshConfiguration();
            configuration.setHost("127.0.0.1");
            configuration.setPort(server.getPort());
            configuration.setUsername("test-user");
            configuration.setPassword(new GuardedString("test-password".toCharArray()));
            configuration.setKnownHosts(new String[] {"127.0.0.1 " + PublicKeyEntry.toString(key.getPublic())});
            configuration.setArgumentStyle(SshConfiguration.ARGUMENT_STYLE_VARIABLES_BASH);
            configuration.setVariableTransport(SshConfiguration.VARIABLE_TRANSPORT_ENV);
            connector.init(configuration);
        }

        private void awaitClosed() throws InterruptedException {
            Session session = sessions.poll(5, TimeUnit.SECONDS);
            Assert.assertNotNull(session, "The server must observe a connection");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((!session.isClosed() || !server.getActiveSessions().isEmpty()) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            Assert.assertTrue(session.isClosed(), "The connector must close the server-side connection");
            Assert.assertTrue(server.getActiveSessions().isEmpty(), "No server-side connections may remain");
        }

        @Override
        public void close() throws IOException {
            connector.dispose();
            server.stop(true);
        }
    }

    /**
    Returns a deterministic test response without launching an operating-system shell.
    */
    private static final class ReplyCommand implements Command {
        private OutputStream output;
        private ExitCallback callback;

        @Override
        public void setInputStream(InputStream input) {
        }

        @Override
        public void setOutputStream(OutputStream output) {
            this.output = output;
        }

        @Override
        public void setErrorStream(OutputStream error) {
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment environment) throws IOException {
            output.write("completed".getBytes(StandardCharsets.UTF_8));
            output.flush();
            callback.onExit(0);
        }

        @Override
        public void destroy(ChannelSession channel) {
        }
    }
}
