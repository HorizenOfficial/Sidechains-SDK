package com.horizen.api.http;

import akka.http.javadsl.server.Route;
import com.horizen.node.SidechainNodeView;
import scala.util.Try;

import java.util.List;

public abstract class ApplicationApiGroup {

    private ApplicationNodeViewProvider applicationNodeViewProvider;

    public abstract String basePath();

    public abstract List<Route> getRoutes();

    public final SidechainNodeView getSidechaiNodeView() throws Exception {
        Try<SidechainNodeView> tryView = applicationNodeViewProvider.getSidechainNodeView();
        if(tryView.isSuccess())
            return tryView.get();
        else throw new IllegalStateException(tryView.failed().get());
    }

    public final SidechainJsonSerializer getJsonObjectMapper() {
        return applicationNodeViewProvider.newJsonSerializer();
    }

    public final String serialize(Object anObject) {
        return applicationNodeViewProvider.serialize(anObject);
    }

    public final String serialize(Object anObject, Class<?> aView) {
        if(aView == null)
            throw new IllegalArgumentException("Argument aView cannot be null.");
        else return applicationNodeViewProvider.serialize(anObject, aView);
    }

    public final String serialize(Object anObject, SidechainJsonSerializer sidechainJsonSerializer) {
        if(sidechainJsonSerializer == null)
            throw new IllegalArgumentException("Argument sidechainJsonSerializer cannot be null.");
        else return applicationNodeViewProvider.serialize(anObject, sidechainJsonSerializer);
    }

    public final String serialize(Object anObject, Class<?> aView, SidechainJsonSerializer sidechainJsonSerializer) {
        if(aView == null)
            throw new IllegalArgumentException("Argument aView cannot be null.");
        else if(sidechainJsonSerializer == null)
            throw new IllegalArgumentException("Argument sidechainJsonSerializer cannot be null.");
        else return applicationNodeViewProvider.serialize(anObject, aView, sidechainJsonSerializer);
    }

    public final String serializeError(String code, String description, String detail) {
        return applicationNodeViewProvider.serializeError(code, description, detail);
    }

    public final String serializeError(String code, String description, String detail, Class<?> aView) {
        if(aView == null)
            throw new IllegalArgumentException("Argument aView cannot be null.");
        else return applicationNodeViewProvider.serializeError(code, description, detail, aView);
    }

    public final String serializeError(String code, String description, String detail, SidechainJsonSerializer sidechainJsonSerializer) {
        if(sidechainJsonSerializer == null)
            throw new IllegalArgumentException("Argument sidechainJsonSerializer cannot be null.");
        else return applicationNodeViewProvider.serializeError(code, description, detail, sidechainJsonSerializer);
    }

    public final String serializeError(String code, String description, String detail, Class<?> aView, SidechainJsonSerializer sidechainJsonSerializer) {
        if(aView == null)
            throw new IllegalArgumentException("Argument aView cannot be null.");
        else if(sidechainJsonSerializer == null)
            throw new IllegalArgumentException("Argument sidechainJsonSerializer cannot be null.");
        else return applicationNodeViewProvider.serializeError(code, description, detail, aView, sidechainJsonSerializer);
    }

    public final void setApplicationNodeViewProvider(ApplicationNodeViewProvider applicationNodeViewProvider){
        this.applicationNodeViewProvider = applicationNodeViewProvider;
    }

}