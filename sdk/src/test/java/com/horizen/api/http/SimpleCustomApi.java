package com.horizen.api.http;

import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.node.SidechainNodeView;
import com.horizen.secret.Secret;
import com.horizen.serialization.Views;
import scala.Option;
import scala.Some;

import java.util.ArrayList;
import java.util.List;

public class SimpleCustomApi extends ApplicationApiGroup
{
    @Override
    public String basePath() {
        return "customSecret";
    }

    @Override
    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<>();
        routes.add(bindPostRequest("getNSecrets", this::getNSecretsFunction, GetSecretRequest.class));
        routes.add(bindPostRequest("getNSecretOtherImplementation", this::getNSecretOtherImplementationFunction, GetSecretRequest.class));
        routes.add(bindPostRequest("getAllSecretByEmptyHttpBody", this::getAllSecretByEmptyHttpBodyFunction));
        routes.add(bindPostRequest("throwExceptionInFunction", this::throwExceptionInFunction));
        return routes;
    }

    private ApiResponse getNSecretsFunction(GetSecretRequest ent) {
        try {
            RespAllSecret resp = new RespAllSecret();
            List<Secret> res = new ArrayList<>(getFunctionsApplierOnSidechainNodeView().applyFunctionOnSidechainNodeView(v -> v.getNodeWallet().allSecrets()));
            resp.setSecrets(res.subList(0, ent.getSecretCount()));
            return resp;
        } catch (Exception e) {
            return new ErrorAllSecrets(e.getMessage(), Option.apply(e));
        }
    }

    private ApiResponse getNSecretOtherImplementationFunction(SidechainNodeView view, GetSecretRequest ent) {
        try {
            RespAllSecret resp = new RespAllSecret();
            List<Secret> res = view.getNodeWallet().allSecrets();
            resp.setSecrets(res.subList(0, ent.getSecretCount()));
            return resp;
        } catch (Exception e) {
            return new ErrorAllSecrets("Error.", Some.apply(e));
        }
    }

    private ApiResponse getAllSecretByEmptyHttpBodyFunction(SidechainNodeView view) {
        try {
            RespAllSecret resp = new RespAllSecret();
            List<Secret> res = view.getNodeWallet().allSecrets();
            resp.setSecrets(res);
            return resp;
        } catch (Exception e) {
            return new ErrorAllSecrets("Error.", Some.apply(e));
        }
    }

    private ApiResponse throwExceptionInFunction(SidechainNodeView view) {
        throw new IllegalStateException();
    }

    public static class GetSecretRequest {
        private int secretCount;

        public GetSecretRequest(int secretCount) {
            this.secretCount = secretCount;
        }

        public int getSecretCount() {
            return secretCount;
        }

        public void setSecretCount(int secretCount) {
            this.secretCount = secretCount;
        }
    }

    @JsonView(Views.Default.class)
    class RespAllSecret implements SuccessResponse
    {

        private List<Secret> secrets;

        public List<Secret> getSecrets() {
            return secrets;
        }

        public void setSecrets(List<Secret> secrets) {
            this.secrets = secrets;
        }
    }

    @JsonView(Views.Default.class)
    public class ErrorAllSecrets implements ErrorResponse
    {
        private String description;
        private Option<Throwable> exception;

        public ErrorAllSecrets(String description, Option<Throwable> exception) {
            this.description = description;
            this.exception = exception;
        }

        public String getDescription()
        {
            return description;
        }

        @Override
        public String code() {
            return "0901";
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public Option<Throwable> exception() {
            return exception;
        }
    }
}

