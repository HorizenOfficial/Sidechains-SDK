package com.horizen.api.http;

import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.StringUnmarshallers;
import akka.http.scaladsl.model.ContentType;
import akka.http.scaladsl.model.StatusCodes;
import com.horizen.SidechainSettings;
import com.horizen.block.MainchainBlockReference;
import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.box.NoncedBox;
import com.horizen.box.RegularBox;
import com.horizen.companion.SidechainTransactionsCompanion;
import com.horizen.node.SidechainNodeView;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.serialization.Views;
import com.horizen.transaction.BoxTransaction;
import com.horizen.transaction.RegularTransaction;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.TransactionSerializer;
import io.circe.Encoder;
import io.circe.Json;
import javafx.util.Pair;
import scala.Function1;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.JavaConverters.*;
import scala.collection.Seq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static akka.http.javadsl.server.Directives.allOf;
import static akka.http.javadsl.server.Directives.parameterOrDefault;
import static akka.http.javadsl.server.PathMatchers.integerSegment;
import static akka.http.javadsl.server.PathMatchers.segment;

public class MySecondCustomApi extends ApplicationApiGroup {

    private SidechainSettings sett;
    public MySecondCustomApi(SidechainSettings sett){
        this.sett = sett;
    }

    @Override
    public String basePath() {
        return "mySecondCustomApi";
    }

    @Override
    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<Route>();
        routes.add(resource());
        routes.add(hello());
        routes.add(hello2());
        routes.add(resource_2());
        routes.add(bestBlockId());
        return routes;
    }

    private Route hello(){

        long fee = 10;
        long timestamp = 1547798549470L;

        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        PrivateKey25519 pk1 = creator.generateSecret("test_seed1".getBytes());
        PrivateKey25519 pk2 = creator.generateSecret("test_seed2".getBytes());
        PrivateKey25519 pk3 = creator.generateSecret("test_seed3".getBytes());

        ArrayList<Pair<RegularBox, PrivateKey25519>> from = new ArrayList<>();
        from.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 60), pk1));
        from.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 50), pk2));
        from.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 20), pk3));

        PrivateKey25519 pk4 = creator.generateSecret("test_seed4".getBytes());
        PrivateKey25519 pk5 = creator.generateSecret("test_seed5".getBytes());
        PrivateKey25519 pk6 = creator.generateSecret("test_seed6".getBytes());

        ArrayList<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add(new Pair<>(pk4.publicImage(), 10L));
        to.add(new Pair<>(pk5.publicImage(), 20L));
        to.add(new Pair<>(pk6.publicImage(), 90L));

        ArrayList<Long> expectedNonces = new ArrayList<>(Arrays.asList(
                3911136990993187881L,
                -2589583342552885352L,
                -6555861982699909223L)
        );
        CustomTransaction transaction = new CustomTransaction(from, to, fee, timestamp);

        String resp = JsonUtil.toJson(transaction);

        return akka.http.javadsl.server.Directives.path("hello", () ->
                akka.http.javadsl.server.Directives.get(() ->
                        akka.http.javadsl.server.Directives.extractRequestEntity(
                                (inner ->
                                {
//                                    int i = process();
                                    return akka.http.javadsl.server.Directives.complete(
                                            StatusCodes.OK(),
                                            HttpEntities.create(resp.getBytes())
                                            //        "Received " + inner.toString()
                                    );
                                })
                        )
                )
        );

    }

    private Route hello2(){

        long fee = 10;
        long timestamp = 1547798549470L;

        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        PrivateKey25519 pk1 = creator.generateSecret("test_seed1".getBytes());
        PrivateKey25519 pk2 = creator.generateSecret("test_seed2".getBytes());
        PrivateKey25519 pk3 = creator.generateSecret("test_seed3".getBytes());

        ArrayList<Pair<RegularBox, PrivateKey25519>> from = new ArrayList<>();
        from.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 60), pk1));
        from.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 50), pk2));
        from.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 20), pk3));

        PrivateKey25519 pk4 = creator.generateSecret("test_seed4".getBytes());
        PrivateKey25519 pk5 = creator.generateSecret("test_seed5".getBytes());
        PrivateKey25519 pk6 = creator.generateSecret("test_seed6".getBytes());

        ArrayList<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add(new Pair<>(pk4.publicImage(), 10L));
        to.add(new Pair<>(pk5.publicImage(), 20L));
        to.add(new Pair<>(pk6.publicImage(), 90L));

        ArrayList<Long> expectedNonces = new ArrayList<>(Arrays.asList(
                3911136990993187881L,
                -2589583342552885352L,
                -6555861982699909223L)
        );
        SecondCustomTransaction transaction = new SecondCustomTransaction(from, to, fee, timestamp);

        String resp = JsonUtil.toJsonWithCustomView(transaction, Views.CustomView.class);

        return akka.http.javadsl.server.Directives.path("hello2", () ->
                        akka.http.javadsl.server.Directives.get(() ->
                                        akka.http.javadsl.server.Directives.extractRequestEntity(
                                                (inner ->
                                                {
//                                    int i = process();
                                                    return akka.http.javadsl.server.Directives.complete(
                                                            StatusCodes.OK(),
                                                            HttpEntities.create(resp.getBytes())
                                                            //        "Received " + inner.toString()
                                                    );
                                                })
                                        )
                        )
        );

    }

    private int process(){
        int a = 2;
        return a/0;
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
