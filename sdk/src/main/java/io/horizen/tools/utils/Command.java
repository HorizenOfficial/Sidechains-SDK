package io.horizen.tools.utils;

import com.fasterxml.jackson.databind.JsonNode;

public class Command {
    private String name;
    private JsonNode data;

    public Command(String name, JsonNode data) {
        this.name = name;
        this.data = data;
    }

    public String name() {
        return name;
    }

    public JsonNode data() {
        return data;
    }
}
