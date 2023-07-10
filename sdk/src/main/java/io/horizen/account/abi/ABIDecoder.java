package io.horizen.account.abi;

import org.web3j.abi.DefaultFunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;

import java.util.List;

public interface ABIDecoder<T> {

    List<TypeReference<Type>> getListOfABIParamTypes();


    default int getABIDataParamsStaticLengthInBytes(){
        return Type.MAX_BYTE_LENGTH * getListOfABIParamTypes().size();
    }

    // this must be overridden by decoders who want the dynamic abiEncodedData size to be checked, for instance when
    // using an Utf8String of fixed size
    int DO_NOT_CHECK_DYNAMIC_SIZE = -1;
    default int getABIDataParamsDynamicLengthInBytes() { return DO_NOT_CHECK_DYNAMIC_SIZE;}

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
        if (areAllArgumentsFixedLength()) {
            if(abiEncodedData.length != getABIDataParamsStaticLengthInBytes()) {
                throw new IllegalArgumentException("Wrong message data field length: " + abiEncodedData.length +
                        ", expected: " + getABIDataParamsStaticLengthInBytes());
            }
        } else {
            int dynamicLength = getABIDataParamsDynamicLengthInBytes();
            // check size of the dynamic struct if needed for this decoder
            if (dynamicLength != DO_NOT_CHECK_DYNAMIC_SIZE && abiEncodedData.length != dynamicLength){
                throw  new IllegalArgumentException("Wrong message data field length: " + abiEncodedData.length +
                        ", expected: " + dynamicLength);
            }
        }
        String inputParamsString = org.web3j.utils.Numeric.toHexString(abiEncodedData);
        DefaultFunctionReturnDecoder decoder = new DefaultFunctionReturnDecoder();
        List<Type> listOfParams = decoder.decodeFunctionResult(inputParamsString, getListOfABIParamTypes());
        return createType(listOfParams);
    }

    T createType(List<Type> listOfParams);

}
