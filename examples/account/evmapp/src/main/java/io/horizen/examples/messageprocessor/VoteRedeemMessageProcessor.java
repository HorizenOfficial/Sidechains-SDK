package io.horizen.examples.messageprocessor;

import io.horizen.SidechainSettings;
import io.horizen.account.sc2sc.*;
import io.horizen.account.state.*;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.evm.Address;
import io.horizen.examples.messageprocessor.decoder.RedeemSendVoteCmdInputDecoder;
import io.horizen.params.NetworkParams;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.utils.BytesUtils;
import scala.Option;
import sparkz.crypto.hash.Keccak256;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static io.horizen.account.abi.ABIUtil.*;

public class VoteRedeemMessageProcessor extends AbstractCrossChainRedeemMessageProcessor {
    public final static String REDEEM_SEND_VOTE = getABIMethodId("redeemSendVote(uint32,bytes20,bytes20,bytes20,bytes1,bytes20,bytes20,bytes20,bytes20,bytes)");
    public final static String SHOW_ALL_REDEEMED_VOTES = getABIMethodId("showAllVotes(address)");

    private SidechainSettings sidechainSettings;
    private Sc2scCircuit sc2scCircuit;

    public VoteRedeemMessageProcessor(byte[] scId, Option<String> path, Sc2scCircuit sc2scCircuit, ScTxCommitmentTreeRootHashMessageProvider scTxMsgProc) {
        super(scId, path, sc2scCircuit, scTxMsgProc);
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
