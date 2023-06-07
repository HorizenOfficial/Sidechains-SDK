package io.horizen.examples.api;

import akka.http.javadsl.server.Route;
import com.google.common.primitives.Bytes;
import io.horizen.account.api.http.AccountApplicationApiGroup;
import io.horizen.account.node.AccountNodeView;
import io.horizen.account.sc2sc.AccountCrossChainMessage;
import io.horizen.account.sc2sc.AccountCrossChainRedeemMessage;
import io.horizen.api.http.ApiResponse;
import io.horizen.examples.api.model.*;
import io.horizen.examples.messageprocessor.VoteMessageProcessor;
import io.horizen.examples.messageprocessor.VoteRedeemMessageProcessor;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.utils.BytesUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VoteController extends AccountApplicationApiGroup {
    private static final String VOTING_BASE_PATH = "vote";
    @Override
    public String basePath() {
        return VOTING_BASE_PATH;
    }

    @Override
    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<>();
        routes.add(bindPostRequest("sendVoteMessage", this::sendVoteMessage, SendVoteMessageRequest.class));
        routes.add(bindPostRequest("redeem", this::redeemVoteMessage, RedeemVoteMessageRequest.class));
        routes.add(bindPostRequest("showAllVotes", this::showAllVotes, ShowAllVotesRequest.class));
        return routes;
    }

    private ApiResponse sendVoteMessage(AccountNodeView accountNodeView, SendVoteMessageRequest request) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(request.getPayload());
        AccountCrossChainMessage ccMsg = new AccountCrossChainMessage(
                request.getMessageType(),
                BytesUtils.fromHexString(request.getSender()),
                BytesUtils.fromHexString(request.getReceiverSidechain()),
                BytesUtils.fromHexString(request.getReceiver()),
                bb.array()
        );
        byte[] data = Bytes.concat(BytesUtils.fromHexString(VoteMessageProcessor.SEND_VOTE), ccMsg.encode());
        return new SuccessResponseTx(BytesUtils.toHexString(data));
    }

    private ApiResponse redeemVoteMessage(AccountNodeView accountNodeView, RedeemVoteMessageRequest request) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(request.getPayload());
        AccountCrossChainRedeemMessage redeemMsg = new AccountCrossChainRedeemMessage(
                request.getMessageType(),
                BytesUtils.fromHexString(request.getSender()),
                BytesUtils.fromHexString(request.getReceiverSidechain()),
                BytesUtils.fromHexString(request.getReceiver()),
                bb.array(),
                BytesUtils.fromHexString(request.getCertificateDataHash()),
                BytesUtils.fromHexString(request.getNextCertificateDataHash()),
                BytesUtils.fromHexString(request.getScCommitmentTreeRoot()),
                BytesUtils.fromHexString(request.getNextScCommitmentTreeRoot()),
                BytesUtils.fromHexString(request.getProof())
        );
        byte[] data = Bytes.concat(BytesUtils.fromHexString(VoteRedeemMessageProcessor.REDEEM_SEND_VOTE), redeemMsg.encode());
        return new SuccessResponseTx(BytesUtils.toHexString(data));
    }

    private ApiResponse showAllVotes(AccountNodeView accountNodeView, ShowAllVotesRequest request) {
        byte[] data = Bytes.concat(BytesUtils.fromHexString(VoteRedeemMessageProcessor.SHOW_ALL_REDEEMED_VOTES));
        return new SuccessResponseTx(BytesUtils.toHexString(data));
    }
}