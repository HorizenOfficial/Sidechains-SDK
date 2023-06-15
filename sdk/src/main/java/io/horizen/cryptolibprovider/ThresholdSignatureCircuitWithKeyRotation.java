package io.horizen.cryptolibprovider;

import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.schnorrnative.ValidatorKeysUpdatesList;
import io.horizen.block.WithdrawalEpochCertificate;
import io.horizen.certificatesubmitter.keys.SchnorrKeysSignatures;
import io.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuitWithKeyRotation {
    byte[] generateMessageToBeSigned(List<BackwardTransfer> bt,
                                     byte[] sidechainId,
                                     int epochNumber,
                                     byte[] endCumulativeScTxCommTreeRoot,
                                     long btrFee,
                                     long ftMinAmount,
                                     List<byte[]> customFields);

    Pair<byte[], Long> createProof(List<BackwardTransfer> bt,
                                   byte[] sidechainId,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   SchnorrKeysSignatures schnorrKeysSignatures,
                                   long threshold,
                                   Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                                   int sidechainCreationVersionNumber,
                                   byte[] genesisKeysRootHash,
                                   List<byte[]> customFields,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk) throws Exception;

    Boolean verifyProof(List<BackwardTransfer> bt,
                        byte[] sidechainId,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        long quality,
                        Optional<WithdrawalCertificate> previousEpochCertificateOption,
                        byte[] genesisConstantBytes,
                        int sidechainCreationVersionNumber,
                        byte[] proof,
                        List<byte[]> customFields,
                        String verificationKeyPath);

    byte[] generateSysDataConstant(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList, long threshold) throws Exception;

    List<byte[]> getCertificateCustomFields(List<byte[]> elementsToInsert);

    boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath) throws Exception;

    byte[] generateKeysRootHash(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList) throws Exception;
    ValidatorKeysUpdatesList getSchnorrKeysSignaturesList(SchnorrKeysSignatures schnorrKeysSignatures);

    byte[] getSchnorrKeysHash(SchnorrKeysSignatures schnorrKeysSignatures);

    byte[] getMsgToSignForSigningKeyUpdate(byte[] newSigningKeyBytes, int epochNumber, byte[] sidechainId);

    byte[] getMsgToSignForMasterKeyUpdate(byte[] newMasterKeyBytes, int epochNumber, byte[] sidechainId);
}