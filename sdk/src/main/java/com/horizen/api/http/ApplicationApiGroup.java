package com.horizen.api.http;

import akka.http.javadsl.server.Route;
import com.horizen.node.SidechainNodeView;
import scala.util.Try;

import java.util.List;

public abstract class ApplicationApiGroup {

    private ApplicationNodeViewProvider applicationNodeViewProvider;

    public abstract String basePath();

    public abstract List<Route> getRoutes();

    public final SidechainNodeView getSidechainNodeView() throws Exception {
        Try<SidechainNodeView> tryView = applicationNodeViewProvider.getSidechainNodeView();
        if (tryView.isSuccess())
            return tryView.get();
        else throw new IllegalStateException(tryView.failed().get());
    }

    public final void setApplicationNodeViewProvider(ApplicationNodeViewProvider applicationNodeViewProvider) {
        this.applicationNodeViewProvider = applicationNodeViewProvider;
    }

}