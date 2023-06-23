package io.horizen.examples.messageprocessor.decoder;

import io.horizen.account.abi.ABIDecoder;
import io.horizen.account.sc2sc.AccountCrossChainMessage;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes20;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Bytes4;
import org.web3j.abi.datatypes.generated.Uint32;

import java.util.Arrays;
import java.util.List;

public class SendVoteCmdInputDecoder implements ABIDecoder<AccountCrossChainMessage> {

    @Override
    public List<TypeReference<Type>> getListOfABIParamTypes() {
        return Utils.convert(Arrays.asList(
                new TypeReference<Uint32>() {
                },
                new TypeReference<Bytes20>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes20>() {},
                new TypeReference<Bytes32>() {}
        ));
    }

    @Override
    public AccountCrossChainMessage createType(List<Type> listOfParams) {
        int messageType = ((Uint32) listOfParams.get(0)).getValue().intValue();
        byte[] sender = ((Bytes20) listOfParams.get(1)).getValue();
        byte[] receiverSidechain = ((Bytes32) listOfParams.get(2)).getValue();
        byte[] receiver = ((Bytes20) listOfParams.get(3)).getValue();
        byte[] payload = ((Bytes32) listOfParams.get(4)).getValue();

        return new AccountCrossChainMessage(messageType, sender, receiverSidechain, receiver, payload);
    }

}
