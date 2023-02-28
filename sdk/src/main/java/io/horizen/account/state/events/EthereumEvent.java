package io.horizen.account.state.events;

import com.horizen.account.state.events.annotation.Anonymous;
import com.horizen.account.state.events.annotation.Indexed;
import com.horizen.account.state.events.annotation.Parameter;
import com.horizen.account.state.receipt.EthereumConsensusDataLog;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.utils.Numeric;
import sparkz.crypto.hash.Keccak256;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

public class EthereumEvent {
    private EthereumEvent() {
    }

    /**
     * @param eventInstance instance of the custom event class
     * @return HashMap containing the parameter index, if parameter is indexed and the value
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static ArrayList<EventParameterData> getEventParameterData(Object eventInstance) throws IllegalAccessException, InvocationTargetException {
        var annotatedParams = new TreeMap<Integer, EventParameterData>();
        var parameterCandidates = new ArrayList<AccessibleObject>();

        // Check if there are any parameter annotations in any event constructor, because scala ignores the allowed java annotation targets
        for (var constructor : eventInstance.getClass().getDeclaredConstructors()) {
            if (Arrays.stream(constructor.getParameterAnnotations()).flatMap(Arrays::stream).anyMatch(x -> x instanceof Parameter)) {
                throw new IllegalArgumentException("Error while trying the get the parameter data: Parameter annotation not allowed in constructor");
            }
        }

        parameterCandidates.addAll(List.of(eventInstance.getClass().getDeclaredMethods()));
        parameterCandidates.addAll(List.of(eventInstance.getClass().getDeclaredFields()));
        for (AccessibleObject acObj : parameterCandidates) {
            if (acObj.getAnnotation(Parameter.class) == null) continue;
            if (!acObj.canAccess(eventInstance))
                throw new IllegalAccessException("Error: No access to object, check access modifiers");
            var indexed = acObj.getAnnotation(Indexed.class) != null;
            if (annotatedParams.containsKey(acObj.getAnnotation(Parameter.class).value()))
                throw new IllegalArgumentException("Error: Duplicate parameter annotation value");
            if (acObj instanceof Field) {
                var field = (Field) acObj;
                annotatedParams.put(acObj.getAnnotation(Parameter.class).value(), new EventParameterData(indexed, field.getType(), (Type) field.get(eventInstance)));
            } else {
                var method = (Method) acObj;
                annotatedParams.put(acObj.getAnnotation(Parameter.class).value(), new EventParameterData(indexed, method.getReturnType(), (Type) method.invoke(eventInstance)));
            }
        }
        return new ArrayList<>(annotatedParams.values());
    }

    /**
     * @param contractAddress
     * @param eventFunction
     * @param anonymous
     * @return EthereumConsensusDataLog containing the contract address, the topics and the data
     * @throws IOException
     */
    private static EthereumConsensusDataLog createEthereumConsensusDataLog(Address contractAddress, Function eventFunction, Boolean anonymous) throws IOException {
        List<Hash> topics = new ArrayList<>();
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
        var outputParameters = eventFunction.getOutputParameters();

        if (!anonymous) {
            topics.add(new Hash(EventEncoder.encode(new Event(eventFunction.getName(), new ArrayList<>(outputParameters)))));
        }

        for (var i = 0; i < outputParameters.size(); i++) {
            var encodedValue = Numeric.hexStringToByteArray(TypeEncoder.encode(eventFunction.getInputParameters().get(i)));
            if (outputParameters.get(i).isIndexed()) {
                if (topics.size() > 3)
                    throw new IllegalArgumentException("Error: More than four topics defined - defined topics: " + topics.size());
                // values <= 32 byte will be used as is
                if (encodedValue.length > 32) encodedValue = (byte[]) Keccak256.hash(encodedValue);
                topics.add(new Hash(encodedValue));

            } else {
                dataOutputStream.write(encodedValue);
            }
        }

        return new EthereumConsensusDataLog(contractAddress, topics.toArray(new Hash[0]), dataOutputStream.toByteArray());
    }

    /**
     * @param contractAddress
     * @param eventInstance   Instance of the custom event class
     * @return EthereumConsensusDataLog containing the contract address, the topics and the data
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public static EthereumConsensusDataLog getEthereumConsensusDataLog(Address contractAddress, Object eventInstance) throws ClassNotFoundException, IOException, IllegalAccessException, InvocationTargetException {
        List<TypeReference<?>> parametersTypeRef = new ArrayList<>();
        List<Type> convertedParams = new ArrayList<>();
        var annotatedParameters = getEventParameterData(eventInstance);

        for (var parameterData : annotatedParameters) {
            convertedParams.add(parameterData.value);
            parametersTypeRef.add(TypeReference.makeTypeReference(parameterData.value.getTypeAsString(), parameterData.indexed, false));
        }

        var classRef = eventInstance.getClass();
        return createEthereumConsensusDataLog(contractAddress, new Function(classRef.getSimpleName(), convertedParams, parametersTypeRef), classRef.getAnnotation(Anonymous.class) != null);
    }

    private static class EventParameterData {
        Boolean indexed;
        Class<?> annotatedParam;
        Type value;

        public EventParameterData(Boolean indexed, Class<?> annotatedParam, Type value) {
            this.indexed = indexed;
            this.annotatedParam = annotatedParam;
            this.value = value;
        }
    }
}
