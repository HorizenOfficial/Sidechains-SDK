package io.horizen.examples.messageprocessor;

import io.horizen.SidechainSettings;
import io.horizen.account.sc2sc.AbstractCrossChainRedeemMessageProcessor;
import io.horizen.account.sc2sc.AccountCrossChainRedeemMessage;
import io.horizen.account.sc2sc.ScTxCommitmentTreeRootHashMessageProvider;
import io.horizen.account.state.*;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.evm.Address;
import io.horizen.examples.messageprocessor.decoder.RedeemSendVoteCmdInputDecoder;
import io.horizen.params.NetworkParams;
import sparkz.crypto.hash.Keccak256;

import static io.horizen.account.abi.ABIUtil.*;

public class VoteRedeemMessageProcessor extends AbstractCrossChainRedeemMessageProcessor {
    public final static String REDEEM_SEND_VOTE = getABIMethodId("redeemSendVote(uint32,bytes20,bytes20,bytes20,bytes1,bytes20,bytes20,bytes20,bytes20)");

    private SidechainSettings sidechainSettings;
    private Sc2scCircuit sc2scCircuit;
    private ScTxCommitmentTreeRootHashMessageProvider scTxMsgProc;

    public VoteRedeemMessageProcessor(NetworkParams networkParams, Sc2scCircuit sc2scCircuit, ScTxCommitmentTreeRootHashMessageProvider scTxMsgProc) {
        super(networkParams, sc2scCircuit, scTxMsgProc);
    }

    @Override
    public byte[] process(Message msg, BaseAccountStateView view, GasPool gas, BlockContext blockContext) throws ExecutionFailedException {
        String opCodeHex = getFunctionSignature(msg.getData());
        byte[] result;
        if (opCodeHex.equals(REDEEM_SEND_VOTE)) {
            AccountCrossChainRedeemMessage accCCRedeemMsg = getAccountCrossChainRedeemMessageFromMessage(msg);
            result = processRedeemMessage(accCCRedeemMsg, view);
            if (result == null) {
                throw new ExecutionRevertedException("Could not create redeem message");
            }
        } else {
            throw new ExecutionRevertedException(String.format("op code not supported: %s", opCodeHex));
        }
        return result;
    }

    @Override
    public AccountCrossChainRedeemMessage getAccountCrossChainRedeemMessageFromMessage(Message msg) {
        try {
            byte[] arguments = getArgumentsFromData(msg.getData());
            RedeemSendVoteCmdInputDecoder decoder = new RedeemSendVoteCmdInputDecoder();
            return decoder.decode(arguments);
        } catch (ExecutionRevertedException e) {
            return null;
        }
    }

    @Override
    public Address contractAddress() {
        return CustomWellknownAddress.REDEEM_VOTE_SMART_CONTRACT_ADDRESS;
    }

    @Override
    public byte[] contractCode() {
        return Keccak256.hash("RedeemVoteSmartContractCode");
    }
}
