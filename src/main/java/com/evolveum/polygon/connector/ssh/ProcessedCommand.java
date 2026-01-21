package com.evolveum.polygon.connector.ssh;

import java.util.Map;
import java.util.stream.Collectors;

public record ProcessedCommand(String commandString, Map<String, String> envs) {

    /**
     * Special toString implementation to avoid leaking the envs values which could be secrets.
     *
     * @return a string representation of the ProcessedCommand object, with the envs values masked as ****
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ProcessedCommand{");
        sb.append("commandString='").append(commandString).append('\'');
        sb.append(", envs={");
        sb.append(envs.keySet().stream().map(s -> s + "=****").collect(Collectors.joining(", ")));
        sb.append("}");
        sb.append('}');
        return sb.toString();
    }
}
