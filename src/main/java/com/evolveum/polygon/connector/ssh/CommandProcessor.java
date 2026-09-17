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

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ScriptContext;

import java.util.HashMap;
import java.util.Map;

public class CommandProcessor {

    private final SshConfiguration configuration;

    public CommandProcessor(SshConfiguration configuration) {
        this.configuration = configuration;
    }

    public ProcessedCommand process(ScriptContext scriptCtx) {
        String command = scriptCtx.getScriptText();
        if (command == null) {
            return null;
        }
        Map<String, Object> arguments = scriptCtx.getScriptArguments();
        if (arguments == null) {
            return new ProcessedCommand(command, Map.of());
        }
        if (configuration.getArgumentStyle() == null) {
            return encodeArgumentsAndCommandToString(command, arguments, "-");
        }
        return switch (configuration.getArgumentStyle()) {
            case SshConfiguration.ARGUMENT_STYLE_VARIABLES_BASH ->
                    encodeVariablesAndCommandToString(command, arguments, Language.BASH);
            case SshConfiguration.ARGUMENT_STYLE_VARIABLES_POWERSHELL ->
                    encodeVariablesAndCommandToString(command, arguments, Language.POWERSHELL);
            case SshConfiguration.ARGUMENT_STYLE_DASH -> encodeArgumentsAndCommandToString(command, arguments, "-");
            case SshConfiguration.ARGUMENT_STYLE_SLASH -> encodeArgumentsAndCommandToString(command, arguments, "/");
            default -> throw new ConfigurationException(
                    "Unknown value of argument style: " + configuration.getArgumentStyle());
        };
    }

    private ProcessedCommand encodeArgumentsAndCommandToString(String command,
                                                               Map<String, Object> arguments,
                                                               String paramPrefix) {
        StringBuilder commandLineBuilder = new StringBuilder();
        commandLineBuilder.append(command);
        for (Map.Entry<String, Object> argEntry : arguments.entrySet()) {
            if (argEntry.getKey() == null) {
                // we want this to go last
                continue;
            }
            Object value = argEntry.getValue();
            boolean insertAttribute = true;
            if (value == null) {
                switch (configuration.getHandleNullValues()) {
                    case SshConfiguration.HANDLE_NULL_AS_EMPTY_STRING:
                        value = "";
                        break;
                    case SshConfiguration.HANDLE_NULL_AS_GONE:
                        insertAttribute = false;
                        break;
                    default:
                        throw new ConfigurationException(
                                "Unknown value of handleNullValues: " + configuration.getHandleNullValues());
                }
            }
            if (insertAttribute) {
                commandLineBuilder.append(" ");
                commandLineBuilder.append(paramPrefix).append(argEntry.getKey());
                commandLineBuilder.append(" ");
                commandLineBuilder.append(value);
            }
        }
        if (arguments.get(null) != null) {
            commandLineBuilder.append(" ");
            commandLineBuilder.append(arguments.get(null));
        }
        return new ProcessedCommand(commandLineBuilder.toString(), Map.of());
    }

    private enum Language {BASH, POWERSHELL}
    private ProcessedCommand encodeVariablesAndCommandToString(String command,
                                                               Map<String, Object> arguments,
                                                               Language language) {
        if (arguments == null) {
            return new ProcessedCommand(command, Map.of());
        }
        StringBuilder commandLineBuilder = new StringBuilder();
        Map<String, String> envs = new HashMap<>();
        for (Map.Entry<String, Object> argEntry : arguments.entrySet()) {
            if (argEntry.getKey() == null) {
                // we want this to go last
                continue;
            }
            Object value = argEntry.getValue();
            boolean insertAttribute = true;
            if (value == null) {
                switch (configuration.getHandleNullValues()) {
                    case SshConfiguration.HANDLE_NULL_AS_EMPTY_STRING:
                        value = "";
                        break;
                    case SshConfiguration.HANDLE_NULL_AS_GONE:
                        insertAttribute = false;
                        break;
                    default:
                        throw new ConfigurationException(
                                "Unknown value of handleNullValues: " + configuration.getHandleNullValues());
                }
            }
            if (insertAttribute) {
                switch (language) {
                    case BASH -> encodeBashVariable(commandLineBuilder, argEntry.getKey(), value.toString(), envs);
                    case POWERSHELL ->
                            encodePowershellVariable(commandLineBuilder, argEntry.getKey(), value.toString(), envs);
                }

            }
        }
        commandLineBuilder.append(command);
        if (arguments.get(null) != null) {
            commandLineBuilder.append(" ");
            commandLineBuilder.append(arguments.get(null));
        }
        return new ProcessedCommand(commandLineBuilder.toString(), envs);
    }

    private void encodeBashVariable(StringBuilder commandLineBuilder,
                                    String variableName,
                                    String value,
                                    Map<String, String> envs) {
        switch (configuration.getVariableTransport()) {
            case SshConfiguration.VARIABLE_TRANSPORT_INPLACE ->
                    commandLineBuilder.append(variableName).append("=").append(quoteSingle(value)).append("; ");
            case SshConfiguration.VARIABLE_TRANSPORT_ENV -> {
                envs.put(configuration.getVariableTransportEnvPrefix() + variableName, value);
                commandLineBuilder.append(variableName);
                commandLineBuilder.append("=$")
                                  .append(configuration.getVariableTransportEnvPrefix())
                                  .append(variableName)
                                  .append("; unset")
                                  .append(configuration.getVariableTransportEnvPrefix())
                                  .append(variableName)
                                  .append("; \n");

            }
            default -> throw new ConfigurationException(
                    "Unknown value of variable transport: " + configuration.getVariableTransport());

        }
    }

    private void encodePowershellVariable(StringBuilder commandLineBuilder,
                                          String variableName,
                                          String value,
                                          Map<String, String> envs) {
        switch (configuration.getVariableTransport()) {
            case SshConfiguration.VARIABLE_TRANSPORT_INPLACE -> commandLineBuilder.append("$")
                                                                                  .append(variableName)
                                                                                  .append("=")
                                                                                  .append(quoteSingle(value))
                                                                                  .append("; ");
            case SshConfiguration.VARIABLE_TRANSPORT_ENV -> {
                envs.put(configuration.getVariableTransportEnvPrefix() + variableName, value);
                commandLineBuilder.append("$")
                                  .append(variableName)
                                  .append(" = $env:")
                                  .append(configuration.getVariableTransportEnvPrefix())
                                  .append(variableName)
                                  .append("; $env:")
                                  .append(configuration.getVariableTransportEnvPrefix())
                                  .append(variableName)
                                  .append(" = $null; \n");

            }
            default -> throw new ConfigurationException(
                    "Unknown value of variable transport: " + configuration.getVariableTransport());

        }
    }

    private String quoteSingle(Object value) {
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
