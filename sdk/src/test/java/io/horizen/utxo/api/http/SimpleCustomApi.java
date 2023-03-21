package io.horizen.utxo.api.http;

import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.api.http.ApiResponse;
import io.horizen.api.http.ErrorResponse;
import io.horizen.api.http.SuccessResponse;
import io.horizen.utxo.node.SidechainNodeView;
import io.horizen.secret.Secret;
import io.horizen.json.Views;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimpleCustomApi extends SidechainApplicationApiGroup
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
            List<Secret> res = new ArrayList<>(getFunctionsApplierOnSidechainNodeView().applyFunctionOnSidechainNodeView(v -> (v).getNodeWallet().allSecrets()));
            resp.setSecrets(res.subList(0, ent.getSecretCount()));
            return resp;
        } catch (Exception e) {
            return new ErrorAllSecrets(e.getMessage(), Optional.of(e));
        }
    }

    private ApiResponse getNSecretOtherImplementationFunction(SidechainNodeView view, GetSecretRequest ent) {
        try {
            RespAllSecret resp = new RespAllSecret();
            List<Secret> res = view.getNodeWallet().allSecrets();
            resp.setSecrets(res.subList(0, ent.getSecretCount()));
            return resp;
        } catch (Exception e) {
            return new ErrorAllSecrets("Error.", Optional.of(e));
        }
    }

    private ApiResponse getAllSecretByEmptyHttpBodyFunction(SidechainNodeView view) {
        try {
            RespAllSecret resp = new RespAllSecret();
            List<Secret> res = view.getNodeWallet().allSecrets();
            resp.setSecrets(res);
            return resp;
        } catch (Exception e) {
            return new ErrorAllSecrets("Error.", Optional.of(e));
        }
    }

    private ApiResponse throwExceptionInFunction(SidechainNodeView view) {
        throw new IllegalStateException();
    }

    @JsonView(Views.Default.class)
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
    static
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
    public static class ErrorAllSecrets implements ErrorResponse
    {
        private final String description;
        private final Optional<Throwable> exception;

        public ErrorAllSecrets(String description, Optional<Throwable> exception) {
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
        public Optional<Throwable> exception() {
            return exception;
        }
    }
}

