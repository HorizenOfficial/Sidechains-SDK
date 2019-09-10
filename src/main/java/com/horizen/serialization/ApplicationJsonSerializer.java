package com.horizen.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;

public class ApplicationJsonSerializer {

    private Class<?> defaultView;
    private ObjectMapper objectMapper;
    private static ApplicationJsonSerializer instance;

    private ApplicationJsonSerializer(){
        objectMapper = new ObjectMapper();
        defaultView = Views.Default.class;
    }

    public static ApplicationJsonSerializer getInstance(){
        if(instance == null)
        {
            instance = new ApplicationJsonSerializer();
            instance.setDefaultConfiguration();
        }

        return instance;
    }

    public static ApplicationJsonSerializer newInstance(){
        ApplicationJsonSerializer newInstance = new ApplicationJsonSerializer();
        newInstance.setDefaultConfiguration();

        return newInstance;
    }

    public Class<?> getDefaultView() {
        return defaultView;
    }

    public void setDefaultView(Class<?> defaultView) {
        this.defaultView = defaultView;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setDefaultConfiguration() {
        objectMapper.registerModule(new DefaultScalaModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
    }

    public String serialize(Object value) throws Exception {
        return objectMapper.writerWithView(defaultView).writeValueAsString(value);
    }

}
