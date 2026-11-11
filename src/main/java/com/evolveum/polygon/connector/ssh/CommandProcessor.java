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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CommandProcessor {

    private final SshConfiguration configuration;

    public CommandProcessor(SshConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
    Validates transport settings before formatting any script arguments.
    @throws ConfigurationException if transport settings or environment names are invalid
    */
    public ProcessedCommand process(ScriptContext scriptCtx) {
        configuration.validateTransport();
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

    /**
    Checks only transported arguments; Bash assignments must not overwrite another argument's transport variable.
    */
    private void validateNames(Map<String, Object> arguments, Language language) {
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Object> argument : arguments.entrySet()) {
            String name = argument.getKey();
            if (name == null || (argument.getValue() == null && SshConfiguration.HANDLE_NULL_AS_GONE.equals(configuration.getHandleNullValues()))) {
                continue;
            }
            if (!SshConfiguration.ENVIRONMENT_NAME.matcher(name).matches()) {
                throw new ConfigurationException("Invalid script argument name for environment transport: '" + name + "'");
            }
            names.add(name);
        }
        if (language == Language.BASH) {
            for (String name : names) {
                String environment = configuration.getVariableTransportEnvPrefix() + name;
                if (names.contains(environment)) {
                    throw new ConfigurationException("Script argument '" + environment + "' conflicts with environment transport for '" + name + "'");
                }
            }
        }
    }

    private ProcessedCommand encodeVariablesAndCommandToString(String command,
                                                               Map<String, Object> arguments,
                                                               Language language) {
        if (arguments == null) {
            return new ProcessedCommand(command, Map.of());
        }
        if (SshConfiguration.VARIABLE_TRANSPORT_ENV.equals(configuration.getVariableTransport())) {
            validateNames(arguments, language);
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
                                  .append("; unset ")
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
