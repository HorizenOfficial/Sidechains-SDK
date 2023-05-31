package io.horizen.examples.messageprocessor;

import io.horizen.SidechainSettings;
import io.horizen.account.sc2sc.AbstractCrossChainMessageProcessor;
import io.horizen.account.sc2sc.AccountCrossChainMessage;
import io.horizen.account.state.*;
import io.horizen.evm.Address;
import io.horizen.examples.messageprocessor.decoder.SendVoteCmdInputDecoder;
import io.horizen.params.NetworkParams;
import sparkz.crypto.hash.Keccak256;

import static io.horizen.account.abi.ABIUtil.*;

public class VoteMessageProcessor extends AbstractCrossChainMessageProcessor {
    public static final Address contractAddress = CustomWellknownAddress.VOTE_SMART_CONTRACT_ADDRESS;
    public final static String SEND_VOTE = getABIMethodId("sendVote(uint32,bytes20,bytes20,bytes20,bytes1)");

    public VoteMessageProcessor(byte[] sidechainId) {
        super(sidechainId);
    }

    @Override
    public byte[] process(Message msg, BaseAccountStateView view, GasPool gas, BlockContext blockContext) throws ExecutionFailedException {
        String opCodeHex = getFunctionSignature(msg.getData());
        byte[] result;
        if (opCodeHex.equals(SEND_VOTE)) {
            result = sendVote(msg, view, blockContext.withdrawalEpochNumber);
        } else {
            throw new ExecutionRevertedException(String.format("op code not supported: %s", opCodeHex));
        }
        return result;
    }

    private byte[] sendVote(Message msg, BaseAccountStateView view, int withdrawalEpoch) throws ExecutionRevertedException {
        byte[] argumentsByte = getArgumentsFromData(msg.getData());
        SendVoteCmdInputDecoder decoder = new SendVoteCmdInputDecoder();
        AccountCrossChainMessage accCCMsg = decoder.decode(argumentsByte);
        return addCrossChainMessage(accCCMsg, view, withdrawalEpoch);
    }

    @Override
    public Address contractAddress() {
        return CustomWellknownAddress.VOTE_SMART_CONTRACT_ADDRESS;
    }

    @Override
    public byte[] contractCode() {
        return Keccak256.hash("VoteSmartContractCode");
    }
}
