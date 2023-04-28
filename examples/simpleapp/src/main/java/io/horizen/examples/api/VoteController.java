package io.horizen.examples.api;

import akka.http.javadsl.server.Route;
import io.horizen.api.http.ApiResponse;
import io.horizen.examples.api.model.*;
import io.horizen.examples.transaction.RedeemVoteMessageTransaction;
import io.horizen.examples.transaction.SendVoteMessageTransaction;
import io.horizen.proof.Signature25519;
import io.horizen.proposition.Proposition;
import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.proposition.PublicKey25519PropositionSerializer;
import io.horizen.sc2sc.*;
import io.horizen.utils.ByteArrayWrapper;
import io.horizen.utils.BytesUtils;
import io.horizen.utxo.api.http.SidechainApplicationApiGroup;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.CrossChainMessageBox;
import io.horizen.utxo.box.ZenBox;
import io.horizen.utxo.box.data.CrossChainMessageBoxData;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;
import io.horizen.utxo.box.data.ZenBoxData;
import io.horizen.utxo.companion.SidechainTransactionsCompanion;
import io.horizen.utxo.node.NodeMemoryPool;
import io.horizen.utxo.node.SidechainNodeView;
import io.horizen.utxo.transaction.BoxTransaction;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

public class VoteController extends SidechainApplicationApiGroup {
    private static final String VOTING_BASE_PATH = "vote";
    private static final int VOTING_MESSAGE_TYPE = 1;
    private final SidechainTransactionsCompanion sidechainTransactionsCompanion;

    public VoteController(SidechainTransactionsCompanion sidechainTransactionsCompanion) {
        this.sidechainTransactionsCompanion = sidechainTransactionsCompanion;
    }

    @Override
    public String basePath() {
        return VOTING_BASE_PATH;
    }

    @Override
    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<>();
        routes.add(bindPostRequest("sendToSidechain", this::sendVoteToSidechain, SendVoteMessageToSidechainRequest.class));
        routes.add(bindPostRequest("redeem", this::redeem, RedeemVoteMessageRequest.class));
        routes.add(bindPostRequest("getByAddress", this::getByAddress, GetVotesFromAddressRequest.class));
        return routes;
    }

    private ApiResponse sendVoteToSidechain(SidechainNodeView view, SendVoteMessageToSidechainRequest request) {
        try {
            PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer()
                    .parseBytes(BytesUtils.fromHexString(request.getProposition()));

            System.out.println("In the voting controller with proposition: " + request.getProposition());
            System.out.println("And the proposition25519: " + proposition.toString());
            System.out.println("The request: " + request);

            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt(request.getVote());

            CrossChainMessageBoxData ccMsgBoxData = new CrossChainMessageBoxData(
                    proposition,
                    CrossChainProtocolVersion.VERSION_1,
                    VOTING_MESSAGE_TYPE,
                    BytesUtils.fromHexString(request.getReceivingSidechain()),
                    BytesUtils.fromHexString(request.getReceivingAddress()),
                    bb.array()
            );

            // Try to collect regular boxes to pay fee
            List<Box<Proposition>> paymentBoxes = new ArrayList<>();
            long amountToPay = request.getFee();

            // Avoid to add boxes that are already spent in some Transaction that is present in node Mempool.
            List<byte[]> boxIdsToExclude = boxesFromMempool(view.getNodeMemoryPool());
            List<Box<Proposition>> ZenBoxes = view.getNodeWallet().boxesOfType(ZenBox.class, boxIdsToExclude);
            int index = 0;
            while (amountToPay > 0 && index < ZenBoxes.size()) {
                paymentBoxes.add(ZenBoxes.get(index));
                amountToPay -= ZenBoxes.get(index).value();
                index++;
            }

            if (amountToPay > 0) {
                throw new IllegalStateException("Not enough coins to pay the fee.");
            }

            // Set change if exists
            long change = Math.abs(amountToPay);
            List<ZenBoxData> regularOutputs = new ArrayList<>();
            if (change > 0) {
                regularOutputs.add(new ZenBoxData((PublicKey25519Proposition) paymentBoxes.get(0).proposition(), change));
            }

            // Creation of real proof requires transaction bytes. Transaction creation function, in turn, requires some proofs.
            // Thus real transaction creation is done in next steps:
            // 1. Create some fake/empty proofs,
            // 2. Create transaction by using those fake proofs
            // 3. Receive Tx message to be signed from transaction at step 2 (we could get it because proofs are not included into message to be signed)
            // 4. Create real proof by using Tx message to be signed
            // 5. Create real transaction with real proofs

            // Create fake proofs to be able to create transaction to be signed.
            List<byte[]> inputIds = new ArrayList<>();
            for (Box<Proposition> b : paymentBoxes) {
                inputIds.add(b.id());
            }

            List<Signature25519> fakeProofs = Collections.nCopies(inputIds.size(), null);

            SendVoteMessageTransaction unsignedTx = new SendVoteMessageTransaction(
                    inputIds,
                    fakeProofs,
                    regularOutputs,
                    ccMsgBoxData,
                    SendVoteMessageTransaction.TX_VERSION,
                    request.getFee()
            );

            byte[] messageToSign = unsignedTx.messageToSign();

            // Create real signatures.
            List<Signature25519> proofs = new ArrayList<>();
            for (Box<Proposition> box : paymentBoxes) {
                proofs.add((Signature25519) view.getNodeWallet().secretByPublicKey(box.proposition()).get().sign(messageToSign));
            }

            SendVoteMessageTransaction tx = new SendVoteMessageTransaction(
                    inputIds,
                    proofs,
                    regularOutputs,
                    ccMsgBoxData,
                    SendVoteMessageTransaction.TX_VERSION,
                    request.getFee()
            );

            return new SuccessResponseTx(BytesUtils.toHexString(sidechainTransactionsCompanion.toBytes(tx)));
        } catch (Exception e) {
            return new ErrorResponseTx(
                    Constants.SEND_VOTE_MESSAGE_ERROR_RESPONSE_CODE, "Error while creating cross chain message to be sent", Optional.of(e)
            );
        }
    }

    private ApiResponse redeem(SidechainNodeView view, RedeemVoteMessageRequest request) {
        try {
            PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer()
                    .parseBytes(BytesUtils.fromHexString(request.getProposition()));

            CrossChainMessage ccMsg = new CrossChainMessageImpl(
                    CrossChainProtocolVersion.VERSION_1,
                    request.getMessageType(),
                    BytesUtils.fromHexString(request.getSenderSidechain()),
                    BytesUtils.fromHexString(request.getSender()),
                    BytesUtils.fromHexString(request.getReceiverSidechain()),
                    BytesUtils.fromHexString(request.getReceiver()),
                    BytesUtils.fromHexString(request.getPayload())
            );

            CrossChainRedeemMessage redeemMessage = new CrossChainRedeemMessageImpl(
                    ccMsg,
                    BytesUtils.fromHexString(request.getCertificateDataHash()),
                    BytesUtils.fromHexString(request.getNextCertificateDataHash()),
                    BytesUtils.fromHexString(request.getScCommitmentTreeRoot()),
                    BytesUtils.fromHexString(request.getNextScCommitmentTreeRoot()),
                    BytesUtils.fromHexString(request.getProof())
            );
            CrossChainRedeemMessageBoxData ccRedeemMsgBoxData = new CrossChainRedeemMessageBoxData(
                    proposition, redeemMessage.getMessage(), redeemMessage.getCertificateDataHash(), redeemMessage.getNextCertificateDataHash(),
                    redeemMessage.getScCommitmentTreeRoot(), redeemMessage.getNextScCommitmentTreeRoot(), redeemMessage.getProof()
            );

            // Try to collect regular boxes to pay fee
            List<Box<Proposition>> paymentBoxes = new ArrayList<>();
            long amountToPay = request.getFee();

            // Avoid to add boxes that are already spent in some Transaction that is present in node Mempool.
            List<byte[]> boxIdsToExclude = boxesFromMempool(view.getNodeMemoryPool());
            List<Box<Proposition>> ZenBoxes = view.getNodeWallet().boxesOfType(ZenBox.class, boxIdsToExclude);
            int index = 0;
            while (amountToPay > 0 && index < ZenBoxes.size()) {
                paymentBoxes.add(ZenBoxes.get(index));
                amountToPay -= ZenBoxes.get(index).value();
                index++;
            }

            if (amountToPay > 0) {
                throw new IllegalStateException("Not enough coins to pay the fee.");
            }

            // Set change if exists
            long change = Math.abs(amountToPay);
            List<ZenBoxData> regularOutputs = new ArrayList<>();
            if (change > 0) {
                regularOutputs.add(new ZenBoxData((PublicKey25519Proposition) paymentBoxes.get(0).proposition(), change));
            }

            // Creation of real proof requires transaction bytes. Transaction creation function, in turn, requires some proofs.
            // Thus real transaction creation is done in next steps:
            // 1. Create some fake/empty proofs,
            // 2. Create transaction by using those fake proofs
            // 3. Receive Tx message to be signed from transaction at step 2 (we could get it because proofs are not included into message to be signed)
            // 4. Create real proof by using Tx message to be signed
            // 5. Create real transaction with real proofs

            // Create fake proofs to be able to create transaction to be signed.
            List<byte[]> inputIds = new ArrayList<>();
            for (Box<Proposition> b : paymentBoxes) {
                inputIds.add(b.id());
            }

            List<Signature25519> fakeProofs = Collections.nCopies(inputIds.size(), null);

            RedeemVoteMessageTransaction unsignedTx = new RedeemVoteMessageTransaction(
                    inputIds,
                    fakeProofs,
                    regularOutputs,
                    ccRedeemMsgBoxData,
                    RedeemVoteMessageTransaction.TX_VERSION,
                    request.getFee()
            );

            byte[] messageToSign = unsignedTx.messageToSign();

            // Create real signatures.
            List<Signature25519> proofs = new ArrayList<>();
            for (Box<Proposition> box : paymentBoxes) {
                proofs.add((Signature25519) view.getNodeWallet().secretByPublicKey(box.proposition()).get().sign(messageToSign));
            }

            RedeemVoteMessageTransaction tx = new RedeemVoteMessageTransaction(
                    inputIds,
                    proofs,
                    regularOutputs,
                    ccRedeemMsgBoxData,
                    RedeemVoteMessageTransaction.TX_VERSION,
                    request.getFee()
            );

            return new SuccessResponseTx(BytesUtils.toHexString(sidechainTransactionsCompanion.toBytes(tx)));
        } catch (Exception e) {
            return new ErrorResponseTx(
                    Constants.REDEEM_ERROR_RESPONSE_CODE, "Error while creating redeem message", Optional.of(e)
            );
        }
    }

    private ApiResponse getByAddress(SidechainNodeView view, GetVotesFromAddressRequest request) {
        try {
            List<Box<Proposition>> allCrossChainMessageBoxes = view.getNodeWallet().boxesOfType(CrossChainMessageBox.class);
            PublicKey25519Proposition propositionAddress = PublicKey25519PropositionSerializer.getSerializer()
                    .parseBytes(BytesUtils.fromHexString(request.getAddress()));
            List<Box<Proposition>> ccMsgBoxesOfAddress = allCrossChainMessageBoxes
                    .stream()
                    .filter(box -> box.proposition().equals(propositionAddress))
                    .collect(Collectors.toList());

            // Make sure we just consider boxes with votes
            List<CrossChainMessageBox> msgBoxes = new ArrayList<>();
            for (Box<?> box : ccMsgBoxesOfAddress) {
                CrossChainMessageBox msgBox = (CrossChainMessageBox) box;
                if (msgBox.getMessageType() == VOTING_MESSAGE_TYPE) {
                    msgBoxes.add(msgBox);
                }
            }

            int total = msgBoxes.size();
            double avg = computeAvgVotes(msgBoxes, total);

            String responseString = String.format("{\"total\": %s, \"average: %s\"}", total, avg);

            return new SuccessResponseTx(responseString);
        } catch (Exception e) {
            return new ErrorResponseTx(
                    Constants.GET_BY_ADDRESS_ERROR_RESPONSE_CODE, "Error while getting votes by address", Optional.of(e)
            );
        }
    }

    private double computeAvgVotes(List<CrossChainMessageBox> boxes, int total) {
        double voteSum = 0;
        for (CrossChainMessageBox box : boxes) {
            voteSum += Integer.parseInt(new String(box.getPayload()));
        }
        return voteSum / total;
    }

    // Utility functions to get from the current mempool the list of all boxes to be opened.
    private List<byte[]> boxesFromMempool(NodeMemoryPool mempool) {
        List<byte[]> boxesFromMempool = new ArrayList<>();
        for (BoxTransaction tx : mempool.getTransactions()) {
            Set<ByteArrayWrapper> ids = tx.boxIdsToOpen();
            for (ByteArrayWrapper id : ids) {
                boxesFromMempool.add(id.data());
            }
        }
        return boxesFromMempool;
    }
}
