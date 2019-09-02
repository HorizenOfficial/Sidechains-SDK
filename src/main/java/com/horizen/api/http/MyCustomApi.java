package com.horizen.api.http;

import akka.http.javadsl.model.HttpEntities;
import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;

import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.StringUnmarshallers;
import akka.http.scaladsl.model.StatusCodes;
import com.horizen.node.SidechainNodeView;
import io.circe.Json;
import scala.Tuple2;
import scala.collection.JavaConverters;

import java.util.ArrayList;
import java.util.List;

public class MyCustomApi extends ApplicationApiGroup {

    @Override
    public String basePath() {
        return "myCustomApi";
    }

    @Override
    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<Route>();
        routes.add(resource());
        routes.add(hello());
        //routes.add(resourceWithError());
        routes.add(resource_2());
        routes.add(bestBlockId());
        return routes;
    }

/*    private Route resourceWithError(){
        Tuple2<String, Json> t = new Tuple2<>("result", Json.fromString("custom api"));
        List<Tuple2<String, Json>> l = new ArrayList<Tuple2<String, Json>>();
        l.add(t);
        Json j = Json.obj(JavaConverters.collectionAsScalaIterable(l).toSeq());

        return akka.http.javadsl.server.Directives.path("getBalance", () ->
                akka.http.javadsl.server.Directives.post(() ->
                        akka.http.javadsl.server.Directives.extractRequestEntity(
                                inner ->
                                {
                                    String errorCode = new MyCustomErrorCodes().RESOURCE_NOT_FOUND;
                                    return new SidechainApiResponse(
                                            j
                                    );
                                }
                        ))
        );

    }*/

    private Route hello(){
        Tuple2<String, Json> t = new Tuple2<>("result", Json.fromString("custom api"));
        List<Tuple2<String, Json>> l = new ArrayList<Tuple2<String, Json>>();
        l.add(t);
        Json j = Json.obj(JavaConverters.collectionAsScalaIterable(l).toSeq());
        return akka.http.javadsl.server.Directives.path("getBalance", () ->
                akka.http.javadsl.server.Directives.post(() ->
                        akka.http.javadsl.server.Directives.extractRequestEntity(
                                inner ->
                                        akka.http.javadsl.server.Directives.complete(
                                                StatusCodes.OK(),
                                                HttpEntities.create(j.toString().getBytes())
                                                //        "Received " + inner.toString()
                                        )
                        ))
        );

    }

    private Route resource() {
        return akka.http.javadsl.server.Directives.path("resource", () ->
                akka.http.javadsl.server.Directives.post(() -> parameterOrDefault(StringUnmarshallers.BOOLEAN, true,"param", p ->
                        akka.http.javadsl.server.Directives.extractRequestEntity(
                                inner ->
                                        akka.http.javadsl.server.Directives.complete(
                                                "Received " + inner.toString()+"\n"+p+"\n")
                        )
                ))
        );
    }


    private Route resource_2() {
        Tuple2<String, Json> t = new Tuple2<>("result", Json.fromString("ok"));
        List<Tuple2<String, Json>> l = new ArrayList<Tuple2<String, Json>>();
        l.add(t);
        Json j = Json.obj(JavaConverters.collectionAsScalaIterable(l).toSeq());
        return akka.http.javadsl.server.Directives.path(segment("order").slash(integerSegment()), id ->
                allOf(Directives::get, Directives::extractRequestEntity, inner ->

                        akka.http.javadsl.server.Directives.complete(
                                HttpResponse.create().
                                        withStatus(200).
                                        withEntity(HttpEntities.create(
                                                j.toString().getBytes()
                                        )
                                )
                        ))
        );
    }

    private Route bestBlockId() {
        Tuple2<String, Json> t = new Tuple2<>("result", Json.fromString("ok"));
        List<Tuple2<String, Json>> l = new ArrayList<Tuple2<String, Json>>();
        l.add(t);
        Json j = Json.obj(JavaConverters.collectionAsScalaIterable(l).toSeq());
        return akka.http.javadsl.server.Directives.path("bestBlockId", () ->
                akka.http.javadsl.server.Directives.get( () ->
                        akka.http.javadsl.server.Directives.complete(
                                getResponse()
                        )
                ));
    }

    private HttpResponse getResponse() {
        try {
            SidechainNodeView view = getSidechaiNodeView();
            String id = view.getNodeHistory().getBestBlock().id();
            return HttpResponse.create().
                    withStatus(200).
                    withEntity(HttpEntities.create(("Block id: "+id).getBytes())
                    );
        }catch (Exception e){
            return HttpResponse.create().
                    withStatus(200).
                    withEntity(HttpEntities.create("No sidechain node view received".getBytes())
                    );
        }

    }

}
