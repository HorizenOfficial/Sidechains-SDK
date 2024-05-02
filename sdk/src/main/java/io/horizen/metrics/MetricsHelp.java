package io.horizen.metrics;


import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.json.Views;

@JsonView(Views.Default.class)
public class MetricsHelp {

    private String id;
    private String description;

    public MetricsHelp(String id, String description){
        this.id = id;
        this.description = description;
    }
    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }
}
