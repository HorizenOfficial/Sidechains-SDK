package com.horizen.account.api;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.PathMatcher0;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import com.horizen.api.http.ApiResponse;
import com.horizen.api.http.ApplicationApiGroup;
import com.horizen.api.http.InternalExceptionApiErrorResponse;
import com.horizen.node.SidechainNodeView;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static akka.http.javadsl.server.Directives.entity;
import static akka.http.javadsl.server.PathMatchers.segment;

public abstract class ControllerBase extends ApplicationApiGroup {
    private final String version;
    private static final String API_BASE_NAME = "account";

    public ControllerBase(String version) {
        this.version = version;
    }

    @Override
    public String basePath() {
        return version;
    }

    protected final <T> Route bindPostRequestSubPath(
            BiFunction<SidechainNodeView, T, ApiResponse> functionToBind,
            Class<T> clazz,
            String... subPaths
    ) {
        Function<T, ApiResponse> transformedFunction =
                secondArgumentPartialApply(this::applyBiFunctionOnSidechainNodeView, functionToBind);
        return bindPostRequestSubPath(transformedFunction, clazz, subPaths);
    }

    /*
     * Create AKKA route by embedding functionToBind which take input parameter T into AKKA Directives, functionToBind's result of type <T> shall be converted to Route, compose functions
     * functionToBind and buildRouteForApiResponse, i.e. apply function buildRouteForApiResponse on result of functionToBind
     */
    protected final <T> Route bindPostRequestSubPath(
            Function<T, ApiResponse> functionToBind,
            Class<T> requestBodyClazz,
            String... subPaths
    ) {
        Unmarshaller<HttpEntity, T> unmarshaller = Jackson.unmarshaller(requestBodyClazz);
        PathMatcher0 pathMatcher = buildSubPaths(subPaths);

        return
                Directives.path(
                        pathMatcher,
                        () -> Directives.post(
                                () -> entity(unmarshaller, functionToBind.andThen(ApplicationApiGroup::buildRouteForApiResponse)))
                );
    }

    protected final <T> Route bindGetRequestSubPath(
            BiFunction<SidechainNodeView, T, ApiResponse> functionToBind,
            String queryParamSymbol,
            String... subPaths
    ) {
        Function<T, ApiResponse> transformedFunction =
                secondArgumentPartialApply(this::applyBiFunctionOnSidechainNodeView, functionToBind);
        return bindGetRequestSubPath(transformedFunction, queryParamSymbol, subPaths);
    }

    protected final <T> Route bindGetRequestSubPath(
            Function<T, ApiResponse> functionToBind,
            String queryParamSymbol,
            String... subPaths
    ) {
        PathMatcher0 pathMatcher = buildSubPaths(subPaths);
        return Directives.path(
                pathMatcher,
                () -> Directives.get(
                        () -> Directives.parameterOptional(
                                queryParamSymbol,
                                (contractSymbol) -> functionToBind
                                        .andThen(ApplicationApiGroup::buildRouteForApiResponse)
                                        .apply((T) contractSymbol.orElse(null))
                        )
                )
        );

    }

    private PathMatcher0 buildSubPaths(String... subPaths) {
        if (subPaths.length == 0) {
            throw new IllegalStateException();
        }

        PathMatcher0 pathMatcher = segment(API_BASE_NAME);

        pathMatcher = pathMatcher.slash(subPaths[0]);

        for (int subPathIndex = 1; subPathIndex < subPaths.length; subPathIndex++) {
            pathMatcher = pathMatcher.slash(
                    segment(subPaths[subPathIndex])
            );
        }

        return pathMatcher;
    }

    private <T> ApiResponse applyBiFunctionOnSidechainNodeView(
            T functionParameter,
            BiFunction<SidechainNodeView, T, ApiResponse> func
    ) {
        try {
            return getFunctionsApplierOnSidechainNodeView().applyBiFunctionOnSidechainNodeView(func, functionParameter);
        } catch (Exception e) {
            return new InternalExceptionApiErrorResponse(Optional.of(e));
        }
    }
}
