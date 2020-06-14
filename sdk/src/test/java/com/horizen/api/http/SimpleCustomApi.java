package com.horizen.api.http;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.node.SidechainNodeView;
import com.horizen.secret.Secret;
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
        routes.add(allSecrets());
        return routes;
    }

    private Route allSecrets() {
        return Directives.path("allSecrets", () ->
                Directives.post((() ->
                        Directives.extractRequestEntity(entity -> {
                            try {
                                SidechainNodeView view = getSidechainNodeView();
                                RespAllSecret resp = new RespAllSecret();
                                resp.setSecrets(view.getNodeWallet().allSecrets());
                                return ApiResponseUtil.toResponseAsJava(resp);
                            } catch (Exception e) {
                                ErrorAllSecrets error = new ErrorAllSecrets("Error.", Some.apply(e));
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
    class RespAllSecret implements SuccessResponse {

        private List<Secret> secrets;

        public List<Secret> getSecrets() {
            return secrets;
        }

        public void setSecrets(List<Secret> secrets) {
            this.secrets = secrets;
        }
    }
}
