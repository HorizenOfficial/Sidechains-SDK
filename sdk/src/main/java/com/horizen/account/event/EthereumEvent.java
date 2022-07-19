package com.horizen.account.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizen.evm.interop.EvmLog;
import com.horizen.evm.utils.Hash;
import org.web3j.abi.*;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.Type;
import org.web3j.utils.Numeric;
import scorex.crypto.hash.Keccak256;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

public class EthereumEvent {
    public EthereumEvent() {
    }

    private static class EventParameterData {
        Boolean indexed;
        Object annotatedParam;
        Object value;

        public EventParameterData(Boolean indexed, Object annotatedParam, Object value) {
            this.indexed = indexed;
            this.annotatedParam = annotatedParam;
            this.value = value;
        }
    }

    /**
     * @param eventInstance instance of the custom event class
     * @return HashMap containing the parameter index, if parameter is indexed and the value
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static Map<Integer, EventParameterData> getEventParameterData(Object eventInstance) throws IllegalAccessException, InvocationTargetException {
        Map<Integer, EventParameterData> annotatedParams = new HashMap<>();
        Method[] methods = eventInstance.getClass().getDeclaredMethods();
        if (methods.length > 0) {
            for (var method : methods) {
                if (method.getAnnotation(Parameter.class) == null) continue;
                var indexed = method.getAnnotation(Indexed.class) == null ? false : true;
                annotatedParams.put(method.getAnnotation(Parameter.class).value(), new EventParameterData(indexed, method.getReturnType(), method.invoke(eventInstance)));
            }
        } else {
            Field[] fields = eventInstance.getClass().getDeclaredFields();
            for (var field : fields) {
                if (field.getAnnotation(Parameter.class) == null) continue;
                var indexed = field.getAnnotation(Indexed.class) == null ? false : true;
                annotatedParams.put(field.getAnnotation(Parameter.class).value(), new EventParameterData(indexed, field.getType(), field.get(eventInstance)));
            }
        }
        return annotatedParams;
    }

    /**
     * @param contractAddress
     * @param eventFunction
     * @param classRef
     * @return EvmLog containing the contract address, the topics and the data
     * @throws IOException
     */
    private static EvmLog createEvmLog(Address contractAddress, Function eventFunction, Class<?> classRef) throws IOException {
        var address = com.horizen.evm.utils.Address.FromBytes(Numeric.hexStringToByteArray(contractAddress.getValue()));
        boolean anonymous = classRef.getAnnotation(Anonymous.class) != null;
        List<Hash> topics = new ArrayList<>();
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();

        if (anonymous)
            topics.add(Hash.FromBytes(Numeric.hexStringToByteArray(EventEncoder.encode(new Event(eventFunction.getName(), new ArrayList<>(eventFunction.getOutputParameters()))))));

        for (var i = 0; i < eventFunction.getOutputParameters().size(); i++) {
            var encodedValue = Numeric.hexStringToByteArray(TypeEncoder.encode(eventFunction.getInputParameters().get(i)));
            if (eventFunction.getOutputParameters().get(i).isIndexed()) {
                if (encodedValue.length > 32)
                    encodedValue = (byte[]) Keccak256.hash(encodedValue);
                topics.add(Hash.FromBytes(encodedValue));
            } else {
                dataOutputStream.write(encodedValue);
            }
        }
        return new EvmLog(address, topics.toArray(new Hash[topics.size()]), dataOutputStream.toByteArray());
    }

    /**
     * @param contractAddress
     * @param eventInstance   Instance of the custom event class
     * @return EvmLog containing the contract address, the topics and the data
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public static EvmLog getEvmLog(Address contractAddress, Object eventInstance) throws ClassNotFoundException, IOException, IllegalAccessException, InvocationTargetException {
        var classRef = eventInstance.getClass();
        List<Object> parameterTypes = new ArrayList<>();
        List<Boolean> parameterIndexed = new ArrayList<>();
        List<Object> parameterValues = new ArrayList<>();
        List<TypeReference<?>> parametersTypeRef = new ArrayList<>();
        List<Type> convertedParams = new ArrayList<>();
        var mapper = new ObjectMapper();
        var annotatedParameters = getEventParameterData(eventInstance);
        var keys = new TreeSet<>(annotatedParameters.keySet());

        // pass parameter types, if indexed and values
        for (Integer key : keys) {
            parameterTypes.add(annotatedParameters.get(key).annotatedParam);
            parameterIndexed.add(annotatedParameters.get(key).indexed);
            parameterValues.add(annotatedParameters.get(key).value);
        }

        for (int i = 0; i < parameterTypes.size(); i++) {
            convertedParams.add((Type) mapper.convertValue(parameterValues.get(i), (Class<?>) parameterTypes.get(i)));
            parametersTypeRef.add(TypeReference.makeTypeReference(convertedParams.get(i).getTypeAsString(), parameterIndexed.get(i), false));
        }

        // build Function instance containing function name, parameter values and type references
        Function transfer = new Function(classRef.getSimpleName(), convertedParams, parametersTypeRef);

        return createEvmLog(contractAddress, transfer, classRef);
    }
}
