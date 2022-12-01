package com.horizen.cryptolibprovider;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignatures;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.schnorrnative.ValidatorKeysUpdatesList;
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
                                     List<byte[]> customFields);

    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   byte[] sidechainId,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   List<byte[]> customFields,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   SchnorrKeysSignatures schnorrKeysSignatures,
                                   long threshold,
                                   Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                                   int sidechainCreationVersionNumber,
                                   byte[] genesisKeysRootHash,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk) throws Exception;

    Boolean verifyProof(List<WithdrawalRequestBox> bt,
                        byte[] sidechainId,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        List<byte[]> customFields,
                        long quality,
                        Optional<WithdrawalCertificate> previousEpochCertificateOption,
                        byte[] genesisConstantBytes,
                        int sidechainCreationVersionNumber,
                        byte[] proof,
                        String verificationKeyPath);

    byte[] generateSysDataConstant(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList, long threshold) throws Exception;

    List<byte[]> getCertificateCustomFields(List<byte[]> customFields);

    boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath) throws Exception;

    byte[] generateKeysRootHash(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList) throws Exception;
    ValidatorKeysUpdatesList getSchnorrKeysSignaturesList(SchnorrKeysSignatures schnorrKeysSignatures);

    byte[] getSchnorrKeysHash(SchnorrKeysSignatures schnorrKeysSignatures);
}
