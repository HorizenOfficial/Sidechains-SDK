package com.horizen.api.http;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.node.SidechainNodeView;
import com.horizen.serialization.Views;
import scala.Some;

import java.util.ArrayList;
import java.util.List;

public class SimpleCustomApi extends ApplicationApiGroup {

    @Override
    public String basePath() {
        return "simpleApi";
    }

    @Override
    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<>();
        routes.add(allPublicKeys());
        return routes;
    }

    private Route allPublicKeys() {
        return Directives.path("allPublicKeys", () ->
                Directives.post((() ->
                        Directives.extractRequestEntity(entity -> {
                            try {
                                SidechainNodeView view = getSidechaiNodeView();
                                ExtendedNodeWallet wallet = (ExtendedNodeWallet) view.getNodeWallet();
                                RespNewSecret resp = new RespNewSecret();
                                List<ExtendedProposition> props = wallet.allPublicKeys();
                                resp.setPropositions(props);
                                return ApiResponseUtil.toResponseAsJava(resp);
                            } catch (Exception e) {
                                ErrorAllPublickeys error = new ErrorAllPublickeys("Error.", Some.apply(e));
                                try {
                                    return ApiResponseUtil.toResponseAsJava(error);
                                } catch (Exception ee) {
                                    return Directives.complete(HttpResponse.create()
                                            .withStatus(500)
                                            .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, "{\"error\":500, \"reason\"=" + ee.getMessage() + ", detail=\"\"}")));
                                }
                            }
                        }))));
    }

    @JsonView(Views.Default.class)
    class RespNewSecret implements SuccessResponse {

        private List<ExtendedProposition> propositions;

        public List<ExtendedProposition> getPropositions() {
            return propositions;
        }

        public void setPropositions(List<ExtendedProposition> propositions) {
            this.propositions = propositions;
        }
    }
}
