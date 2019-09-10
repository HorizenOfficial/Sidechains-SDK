package com.horizen.serialization;

import com.horizen.api.http.schema.SidechainApiErrorResponseScheme;
import com.horizen.api.http.schema.SidechainApiManagedError;
import com.horizen.api.http.schema.SidechainApiResponseBody;
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
                new SidechainApiErrorResponseScheme(
                        new SidechainApiManagedError(code, description, Option.apply(detail))
                ));
    }

    public static String serializeErrorWithResult(ApplicationJsonSerializer serializer,
                                                  String code,
                                                  String description,
                                                  String detail) throws Exception {
        return serializer.serialize(
                new SidechainApiErrorResponseScheme(
                        new SidechainApiManagedError(code, description, Option.apply(detail))
                ));
    }
}
