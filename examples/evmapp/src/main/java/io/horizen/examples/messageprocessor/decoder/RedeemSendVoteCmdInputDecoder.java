package io.horizen.examples.messageprocessor.decoder;

import io.horizen.account.abi.ABIDecoder;
import io.horizen.account.sc2sc.AccountCrossChainMessage;
import io.horizen.account.sc2sc.AccountCrossChainRedeemMessage;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes20;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Bytes4;
import org.web3j.abi.datatypes.generated.Uint32;

import java.util.Arrays;
import java.util.List;

public class RedeemSendVoteCmdInputDecoder implements ABIDecoder<AccountCrossChainRedeemMessage> {
    @Override
    public List<TypeReference<Type>> getListOfABIParamTypes() {
        return Utils.convert(Arrays.asList(
                new TypeReference<Uint32>() {
                },
                new TypeReference<Bytes20>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes20>() {},
                new TypeReference<Bytes4>() {},

                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<DynamicBytes>() {}
        ));
    }

    @Override
    public AccountCrossChainRedeemMessage createType(List<Type> listOfParams) {
        int messageType = (int) listOfParams.get(0).getValue();
        byte[] sender = (byte[]) listOfParams.get(1).getValue();
        byte[] receiverSidechain = (byte[]) listOfParams.get(2).getValue();
        byte[] receiver = (byte[]) listOfParams.get(3).getValue();
        byte[] payload = (byte[]) listOfParams.get(4).getValue();

        byte[] certificateDataHash = (byte[]) listOfParams.get(5).getValue();
        byte[] nextCertificateDataHash = (byte[]) listOfParams.get(6).getValue();
        byte[] scCommitmentTreeRoot = (byte[]) listOfParams.get(7).getValue();
        byte[] nextScCommitmentTreeRoot = (byte[]) listOfParams.get(8).getValue();
        byte[] proof = (byte[]) listOfParams.get(9).getValue();

        return new AccountCrossChainRedeemMessage(
                messageType, sender, receiverSidechain, receiver, payload,
                certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
        );
    }
}