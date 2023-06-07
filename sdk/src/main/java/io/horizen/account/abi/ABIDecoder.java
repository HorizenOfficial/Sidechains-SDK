package io.horizen.account.abi;

import org.web3j.abi.DefaultFunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;

import java.util.List;

public interface ABIDecoder<T> {

    List<TypeReference<Type>> getListOfABIParamTypes();

    default int getABIDataParamsLengthInBytes() throws ClassNotFoundException {
        return Type.MAX_BYTE_LENGTH * getListOfABIParamTypes().size();
    }

    default boolean areAllArgumentsFixedLength() throws ClassNotFoundException {
        List<TypeReference<Type>> paramsTypes = getListOfABIParamTypes();
        for (TypeReference<Type> t : paramsTypes) {
            Class<Type> classType = t.getClassType();
            if (isDynamicType(classType)) {
                return false;
            }
        }
        return true;
    }

    default boolean isDynamicType(Class<Type> classType) {
        return DynamicArray.class.isAssignableFrom(classType) ||
                DynamicBytes.class.isAssignableFrom(classType) ||
                Utf8String.class.isAssignableFrom(classType);
    }

    default T decode(byte[] abiEncodedData) throws ClassNotFoundException {
        if (areAllArgumentsFixedLength() && abiEncodedData.length != getABIDataParamsLengthInBytes()) {
            throw new IllegalArgumentException("Wrong message data field length: " + abiEncodedData.length +
                    ", expected: " + getABIDataParamsLengthInBytes());
        }
        String inputParamsString = org.web3j.utils.Numeric.toHexString(abiEncodedData);
        DefaultFunctionReturnDecoder decoder = new DefaultFunctionReturnDecoder();
        List<Type> listOfParams = decoder.decodeFunctionResult(inputParamsString, getListOfABIParamTypes());
        return createType(listOfParams);
    }

    T createType(List<Type> listOfParams);

}
