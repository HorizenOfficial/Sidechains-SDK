package io.horizen.account.abi;

import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.utils.Numeric;
import java.util.List;

public interface  ABIEncodable<T extends Type> {

    default byte[] encode() {
        List<Type> listOfABIObjs = List.of(asABIType());
        StringBuilder sb = new StringBuilder();
        for (Type t : listOfABIObjs) {
            sb.append(TypeEncoder.encode(t));
        }
        return Numeric.hexStringToByteArray(sb.toString());
    }

    T asABIType();

}


