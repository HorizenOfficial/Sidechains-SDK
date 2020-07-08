package com.horizen.api.http;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import com.horizen.node.SidechainNodeView;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static akka.http.javadsl.server.Directives.*;

public abstract class ApplicationApiGroup {
    private FunctionsApplierOnSidechainNodeView functionsApplierOnSidechainNodeView;

    public abstract String basePath();

    public abstract List<Route> getRoutes();

    public final FunctionsApplierOnSidechainNodeView getFunctionsApplierOnSidechainNodeView() {
        return functionsApplierOnSidechainNodeView;
    }

    public final void setFunctionsApplierOnSidechainNodeView(FunctionsApplierOnSidechainNodeView functionsApplierOnSidechainNodeView) {
        this.functionsApplierOnSidechainNodeView = functionsApplierOnSidechainNodeView;
    }

    protected static Route buildRoutForApiResponse(ApiResponse data) {
        try {
            return ApiResponseUtil.toResponseAsJava(data);
        }
        catch (Exception e) {
            return Directives.complete(HttpResponse.create()
                .withStatus(500)
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, "{\"error\":500, \"reason\"=" + e.getMessage() + ", detail=\"\"}")));
        }
    }

    /*
     * Create AKKA route by embedding functionToBind which take input parameter T into AKKA Directives, functionToBind's result of type <T> shall be converted to Route, compose functions
     * functionToBind and buildRoutForApiResponse, i.e. apply function buildRoutForApiResponse on result of functionToBind
     */
    protected final <T> Route bindPostRequest(String path, Function<T, ApiResponse> functionToBind, Class<T> clazz) {
        Unmarshaller<HttpEntity, T> unmarshaller = Jackson.unmarshaller(clazz);
        return Directives.path(path,
            () -> Directives.post(
                () -> entity(unmarshaller, functionToBind.andThen(ApplicationApiGroup::buildRoutForApiResponse))));
    }

    /**
     * Function to bind functionToBind have two parameters SidechainNodeView and T. However Akka route could accept only function with one input parameter T. Where T is object created from HTTP request.
     * So we need to convert our (SidechainNodeView, T) -> ApiResponse function to T -> ApiResponse. We do it in the next way:
     * 1. We have function applyBiFunctionOnSidechainNodeView(T functionParameter, BiFunction<SidechainNodeView, T, ApiResponse> func) which take as input functionToBind parameter and functionToBind itself
     * 2. Function applyBiFunctionOnSidechainNodeView know the proper way to apply functionToBind on SidechainNodeView with functionParameter
     * 3. We create new anonymous transformed function based on applyBiFunctionOnSidechainNodeView by partially applying functionToBind on applyBiFunctionOnSidechainNodeView, in other words we create
     * anonymous function object which save functionToBind function as internal variable and expect input parameter T and use way described in applyBiFunctionOnSidechainNodeView.
     * 4. Anonymous transformed function is used for creating Akka route as in previous createAkkaRoute function.
     */
    protected final <T> Route bindPostRequest(String path, BiFunction<SidechainNodeView, T, ApiResponse> functionToBind, Class<T> clazz) {
        Function<T, ApiResponse> transformedFunction = secondArgumentPartialApply(this::applyBiFunctionOnSidechainNodeView, functionToBind);
        return bindPostRequest(path, transformedFunction, clazz);
    }

    protected final Route bindPostRequest(String path, Function<SidechainNodeView, ApiResponse> functionToBind) {
        return Directives.path(path,
            () -> Directives.post(
                () -> requestEntityEmpty(
                    () -> onSuccess(CompletableFuture.supplyAsync(
                        () -> applyFunctionOnSidechainNodeView(functionToBind)),
                        ApplicationApiGroup::buildRoutForApiResponse))));
    }

    private ApiResponse applyFunctionOnSidechainNodeView(Function<SidechainNodeView, ApiResponse> func) {
        try
        {
            return getFunctionsApplierOnSidechainNodeView().applyFunctionOnSidechainNodeView(func);
        }
        catch (Exception e) {
            return new InternalExceptionApiErrorResponse(Optional.of(e));
        }
    }

    private <T> ApiResponse applyBiFunctionOnSidechainNodeView(T functionParameter, BiFunction<SidechainNodeView, T, ApiResponse> func) {
        try
        {
            return getFunctionsApplierOnSidechainNodeView().applyBiFunctionOnSidechainNodeView(func, functionParameter);
        }
        catch (Exception e) {
            return new InternalExceptionApiErrorResponse(Optional.of(e));
        }
    }

    public static <T, U, R> Function<T, R> secondArgumentPartialApply(BiFunction<T, U, R> f, U x) {
        Function<T, R> partialAppliedFunction = y -> f.apply(y, x);
        return partialAppliedFunction;
    }

    public static class InternalExceptionApiErrorResponse implements InternalErrorResponse {
        private final Optional<Throwable> exception;

        public InternalExceptionApiErrorResponse(Optional<Throwable> exception)
        {
            this.exception = exception;
        }

        @Override
        public Optional<Throwable> exception()
        {
            return exception;
        }
    }
}