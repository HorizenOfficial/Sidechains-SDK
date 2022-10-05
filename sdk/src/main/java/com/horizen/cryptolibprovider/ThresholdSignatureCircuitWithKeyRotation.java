package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.utils.Pair;
import scala.collection.Seq;

import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuitWithKeyRotation {
    byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt,
                                     byte[] sidechainId,
                                     int epochNumber,
                                     byte[] endCumulativeScTxCommTreeRoot,
                                     long btrFee,
                                     long ftMinAmount,
                                     Seq<byte[]> customParameters);

    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   byte[] sidechainId,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   Seq<byte[]> customParameters,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   List<byte[]> schnorrPublicKeysBytesList,
                                   long threshold,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk); // todo check name

    Boolean verifyProof(List<WithdrawalRequestBox> bt,
                        byte[] sidechainId,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        Seq<byte[]> customFields,
                        byte[] constant,
                        long quality, byte[] proof,
                        boolean checkProof,
                        String verificationKeyPath,
                        boolean checkVerificationKey);

    byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold);

    boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath, int customFieldsNum);

    List<byte[]> getCertificateCustomFields(Seq<byte[]> customFields);

    byte[] reconstructUtxoMerkleTreeRoot(byte[] fe1Bytes, byte[] fe2Bytes);
}
