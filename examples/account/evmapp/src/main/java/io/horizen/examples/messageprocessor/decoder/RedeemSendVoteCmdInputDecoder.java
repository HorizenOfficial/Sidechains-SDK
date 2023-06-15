package io.horizen.examples.messageprocessor.decoder;

import io.horizen.account.abi.ABIDecoder;
import io.horizen.account.sc2sc.AccountCrossChainRedeemMessage;
import io.horizen.utils.BytesUtils;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
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
                new TypeReference<Uint32>() {},
                new TypeReference<Bytes20>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes20>() {},
                new TypeReference<Bytes4>() {},

                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Bytes32>() {},
                new TypeReference<Utf8String>() {}
        ));
    }

    @Override
    public AccountCrossChainRedeemMessage createType(List<Type> listOfParams) {
        int messageType = ((Uint32) listOfParams.get(0)).getValue().intValue();
        byte[] sender = ((Bytes20) listOfParams.get(1)).getValue();
        byte[] receiverSidechain = ((Bytes32) listOfParams.get(2)).getValue();
        byte[] receiver = ((Bytes20) listOfParams.get(3)).getValue();
        byte[] payload = ((Bytes4) listOfParams.get(4)).getValue();

        byte[] certificateDataHash = ((Bytes32) listOfParams.get(5)).getValue();
        byte[] nextCertificateDataHash = ((Bytes32) listOfParams.get(6)).getValue();
        byte[] scCommitmentTreeRoot = ((Bytes32) listOfParams.get(7)).getValue();
        byte[] nextScCommitmentTreeRoot = ((Bytes32) listOfParams.get(8)).getValue();
        String proofAsBytes32 = ((Utf8String) listOfParams.get(9)).getValue();
        byte[] proof = BytesUtils.fromHexString(proofAsBytes32);

        return new AccountCrossChainRedeemMessage(
                messageType, sender, receiverSidechain, receiver, payload,
                certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
        );
    }
}