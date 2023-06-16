package io.horizen.account.abi;

import org.web3j.abi.DefaultFunctionEncoder;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Type;
import org.web3j.utils.Numeric;

import java.util.List;
import java.util.stream.Collectors;

public interface ABIListEncoder<M extends ABIEncodable<T>, T extends Type> {

    Class<T> getAbiClass();

    default byte[] encode(List<M> listOfObj){
        DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();
        List<T> listOfABIObj = listOfObj.stream().map(wr -> wr.asABIType()).collect(Collectors.toList());

        return Numeric.hexStringToByteArray(encoder.encodeParameters(List.of(new DynamicArray<T>(getAbiClass(), listOfABIObj))));

    }
}
