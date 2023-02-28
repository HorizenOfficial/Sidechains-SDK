package io.horizen.cryptolibprovider;

import com.horizen.certnative.BackwardTransfer;
import com.horizen.utils.Pair;
import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuit {
    byte[] generateMessageToBeSigned(List<BackwardTransfer> bt,
                                     byte[] sidechainId,
                                     int epochNumber,
                                     byte[] endCumulativeScTxCommTreeRoot,
                                     long btrFee,
                                     long ftMinAmount,
                                     Optional<byte[]> utxoMerkleTreeRoot);

    //None elements for schnorrSignatureBytesList if no secret key available, thus schnorrSignatureBytesList.size() == schnorrPublicKeysBytesList.size()
    //threshold is the same as in generateSysDataConstant
    Pair<byte[], Long> createProof(List<BackwardTransfer> bt,
                                   byte[] sidechainId,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   Optional<byte[]> utxoMerkleTreeRoot,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   List<byte[]> schnorrPublicKeysBytesList,
                                   long threshold,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk); // todo check name

    Boolean verifyProof(List<BackwardTransfer> bt,
                        byte[] sidechainId,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        Optional<byte[]> utxoMerkleTreeRoot,
                        byte[] constant,
                        long quality, byte[] proof,
                        boolean checkProof,
                        String verificationKeyPath,
                        boolean checkVerificationKey);

    byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold);

    boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath, int customFieldsNum);

    List<byte[]> getCertificateCustomFields(Optional<byte[]> utxoMerkleTreeRoot);

    byte[] reconstructUtxoMerkleTreeRoot(byte[] fe1Bytes, byte[] fe2Bytes);
}
