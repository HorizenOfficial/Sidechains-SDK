package io.horizen.json;

import io.horizen.account.state.NativeSmartContractMsgProcessor;
import io.horizen.api.http.SidechainApiErrorResponseSchema;
import io.horizen.api.http.SidechainApiManagedError;
import io.horizen.api.http.SidechainApiResponseBody;
import io.horizen.json.serializer.ApplicationJsonSerializer;
import scala.Option;

import java.io.*;
import java.util.Arrays;

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

    public static <T> byte[] serializeObject(T objectToSerialize) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(stream)) {
            oos.writeObject(objectToSerialize);
            return stream.toByteArray();
        }
    }

    public static <T> Option<T> deserializeObject(byte[] objectToDeserialize) throws IOException, ClassNotFoundException {
        if (objectToDeserializeIsEmpty(objectToDeserialize)) return Option.empty();

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(objectToDeserialize))) {
            return Option.apply((T) ois.readObject());
        }
    }

    private static boolean objectToDeserializeIsEmpty(byte[] objectToDeserialize) {
        return objectToDeserialize.length == 0 || Arrays.equals(objectToDeserialize, NativeSmartContractMsgProcessor.NULL_HEX_STRING_32());
    }
}
