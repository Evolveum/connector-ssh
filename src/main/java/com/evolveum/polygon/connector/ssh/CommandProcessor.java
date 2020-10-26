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

import java.util.Map;

public class CommandProcessor {

    private final SshConfiguration configuration;

    public CommandProcessor(SshConfiguration configuration) {
        this.configuration = configuration;
    }

    public String process(ScriptContext scriptCtx) {
        String command = scriptCtx.getScriptText();
        if (command == null) {
            return null;
        }
        Map<String, Object> arguments = scriptCtx.getScriptArguments();
        if (arguments == null) {
            return command;
        }
        if (configuration.getArgumentStyle() == null) {
            return encodeArgumentsAndCommandToString(command, arguments);
        }
        switch (configuration.getArgumentStyle()) {
            case SshConfiguration.ARGUMENT_STYLE_VARIABLES_POWERSHELL:
                return encodePowerShellVariablesAndCommandToString(command, arguments);
            default:
                return encodeArgumentsAndCommandToString(command, arguments);
        }
    }

    private String encodeArgumentsAndCommandToString(String command, Map<String, Object> arguments) {
        StringBuilder commandLineBuilder = new StringBuilder();
        commandLineBuilder.append(command);
        String paramPrefix = getParamPrefix();
        for (Map.Entry<String, Object> argEntry: arguments.entrySet()) {
            if (argEntry.getKey() == null) {
                // we want this to go last
                continue;
            }
            commandLineBuilder.append(" ");
            commandLineBuilder.append(paramPrefix).append(argEntry.getKey());
            if (argEntry.getValue() != null) {
                commandLineBuilder.append(" ");
                commandLineBuilder.append(argEntry.getValue().toString());
            }
        }
        if (arguments.get(null) != null) {
            commandLineBuilder.append(" ");
            commandLineBuilder.append(arguments.get(null));
        }
        return commandLineBuilder.toString();
    }

    private String encodePowerShellVariablesAndCommandToString(String command, Map<String, Object> arguments) {
        if (arguments == null) {
            return command;
        }
        StringBuilder commandLineBuilder = new StringBuilder();
        for (Map.Entry<String, Object> argEntry: arguments.entrySet()) {
            if (argEntry.getKey() == null) {
                // we want this to go last
                continue;
            }
            commandLineBuilder.append("$").append(argEntry.getKey());
            commandLineBuilder.append(" = ");
            commandLineBuilder.append(quoteSingle(argEntry.getValue().toString()));
            commandLineBuilder.append("; ");
        }
        commandLineBuilder.append(command);
        if (arguments.get(null) != null) {
            commandLineBuilder.append(" ");
            commandLineBuilder.append(arguments.get(null));
        }
        return commandLineBuilder.toString();
    }

    private String quoteSingle(Object value) {
        if (value == null) {
            return "";
        }
        return "'" + value.toString().replaceAll("'", "''") + "'";
    }

    private String getParamPrefix() {
        if (configuration.getArgumentStyle() == null) {
            return "-";
        }
        switch (configuration.getArgumentStyle()) {
            case SshConfiguration.ARGUMENT_STYLE_DASH:
                return "-";
            case SshConfiguration.ARGUMENT_STYLE_SLASH:
                return "/";
            default:
                throw new ConfigurationException("Unknown value of argument style: "+configuration.getArgumentStyle());
        }
    }

}
