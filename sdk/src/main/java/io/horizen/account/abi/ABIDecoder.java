package io.horizen.account.abi;

import org.web3j.abi.DefaultFunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;

import java.util.List;

public interface  ABIDecoder<T> {

    List<TypeReference<Type>> getListOfABIParamTypes();

    default int getABIDataParamsLengthInBytes(){
         return Type.MAX_BYTE_LENGTH * getListOfABIParamTypes().size();
    }

    default T decode(byte[] abiEncodedData) {
        if (abiEncodedData.length != getABIDataParamsLengthInBytes()){
            throw  new IllegalArgumentException("Wrong message data field length: " + abiEncodedData.length +
                    ", expected: " + getABIDataParamsLengthInBytes());
        }
        String inputParamsString = org.web3j.utils.Numeric.toHexString(abiEncodedData);
        DefaultFunctionReturnDecoder decoder = new DefaultFunctionReturnDecoder();
        List<Type> listOfParams = decoder.decodeFunctionResult(inputParamsString, getListOfABIParamTypes());
        return createType(listOfParams);
    }

    T createType(List<Type> listOfParams);

}
