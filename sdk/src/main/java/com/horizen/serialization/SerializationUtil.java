package com.horizen.serialization;

import com.horizen.api.http.SidechainApiErrorResponseSchema;
import com.horizen.api.http.SidechainApiManagedError;
import com.horizen.api.http.SidechainApiResponseBody;
import scala.Option;

public class SerializationUtil {

    public static String serialize(Object value) throws Exception {
        return ApplicationJsonSerializer.getInstance().serialize(value);
    }

    public static String serialize(ApplicationJsonSerializer serializer, Object value) throws Exception {
        return serializer.serialize(value);
    }

    public static String serializeWithResult(Object value) throws Exception {
        return ApplicationJsonSerializer.getInstance().serialize(new SidechainApiResponseBody(value));
    }

    public static String serializeWithResult(ApplicationJsonSerializer serializer, Object value) throws Exception {
        return serializer.serialize(new SidechainApiResponseBody(value));
    }

    public static String serializeErrorWithResult(String code,
                                                  String description,
                                                  String detail) throws Exception {
        return ApplicationJsonSerializer.getInstance().serialize(
                new SidechainApiErrorResponseSchema(
                        new SidechainApiManagedError(code, description, Option.apply(detail))
                ));
    }

    public static String serializeErrorWithResult(ApplicationJsonSerializer serializer,
                                                  String code,
                                                  String description,
                                                  String detail) throws Exception {
        return serializer.serialize(
                new SidechainApiErrorResponseSchema(
                        new SidechainApiManagedError(code, description, Option.apply(detail))
                ));
    }

    public static String formatResponse(String response){
        return response + "\n";
    }



}
