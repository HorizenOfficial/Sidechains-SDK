package com.horizen.cryptolibprovider;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes;
import com.horizen.utils.Pair;

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
                                   Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                                   int sidechainCreationVersionNumber,
                                   byte[] genesisKeysRootHash,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk);

    Boolean verifyProof(List<WithdrawalRequestBox> bt,
                        byte[] sidechainId,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        List<byte[]> customFields,
                        byte[] constant,
                        long quality,
                        Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                        byte[] genesisConstantBytes,
                        int sidechainCreationVersionNumber,
                        byte[] proof,
                        String verificationKeyPath);

    byte[] generateSysDataConstant(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList, long threshold);

    List<byte[]> getCertificateCustomFields(List<byte[]> customFields);

    boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath, int customFieldsNum);

    byte[] generateKeysRootHash(List<byte[]> publicSignersKeysList, List<byte[]> publicMastersKeysList);
}
