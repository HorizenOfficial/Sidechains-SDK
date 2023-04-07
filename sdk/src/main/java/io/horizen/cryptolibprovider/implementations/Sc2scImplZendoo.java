package io.horizen.cryptolibprovider.implementations;

import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.MerklePath;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.sc2sc.CrossChainMessageHashImpl;

import java.util.List;

//TODO: tobe implemented..
public class Sc2scImplZendoo implements Sc2scCircuit {

    @Override
    public int getMaxMessagesPerCertificate() {
        return 10;
    }

    @Override
    public byte[] getCrossChainMessageTreeRoot(List<CrossChainMessage> messages){
        return new byte[1];
    }
    @Override
    public MerklePath getCrossChainMessageMerklePath(List<CrossChainMessage> messages, CrossChainMessage leaf){
        return  MerklePath.deserialize(new byte[0]);
    }

    @Override
    public byte[] createRedeemProof(CrossChainMessageHash messageHash,
                                    MerklePath messageMerkePath,
                                    WithdrawalCertificate topQuality_cert_epochN,
                                    MerklePath merklePath_topQuality_cert_epochN,
                                    FieldElement sc_tx_commitment_root_cert_epochN,
                                    WithdrawalCertificate cert_epoch_N1,
                                    MerklePath merklePath_topQuality_cert_epochN1,
                                    FieldElement sc_tx_commitment_root_cert_epochN1) {
        return new byte[0];
    }

    @Override
    public boolean verifyRedeemProof(CrossChainMessageHash messageHash,
                                     byte[] sc_tx_commitment_root_cert_epochN,
                                     byte[] sc_tx_commitment_root_cert_epochN1,
                                     byte[] proof) {
        return true;
    }

    @Override
    public CrossChainMessageHash getCrossChainMessageHash(CrossChainMessage msg) {
        return new CrossChainMessageHashImpl(new byte[0]);
    }
}
