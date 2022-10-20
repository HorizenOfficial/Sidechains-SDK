package com.horizen.cryptolibprovider;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes;
import com.horizen.utils.Pair;
import scala.Enumeration;

import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuitWithKeyRotation {
    byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt,
                                     byte[] sidechainId,
                                     int epochNumber,
                                     byte[] endCumulativeScTxCommTreeRoot,
                                     long btrFee,
                                     long ftMinAmount,
                                     List<byte[]> customParameters);

    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   byte[] sidechainId,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   List<byte[]> customParameters,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   SchnorrKeysSignaturesListBytes schnorrKeysSignaturesListBytes,
                                   long threshold,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk,
                                   Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                                   Enumeration.Value sidechainCreationVersion,
                                   byte[] genesisKeysRootHash);

    Boolean verifyProof(List<WithdrawalRequestBox> bt,
                        byte[] sidechainId,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        List<byte[]> customFields,
                        byte[] constant,
                        long quality,
                        byte[] proof,
                        String verificationKeyPath,
                        Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                        byte[] genesisConstantBytes,
                        Enumeration.Value sidechainCreationVersion);

    byte[] generateSysDataConstant(byte[] genesisKeysRootHash, long threshold);

    List<byte[]> getCertificateCustomFields(List<byte[]> customFields);

    byte[] reconstructUtxoMerkleTreeRoot(byte[] fe1Bytes, byte[] fe2Bytes);
}
