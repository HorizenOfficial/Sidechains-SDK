package io.horizen.cryptolibprovider;

import io.horizen.block.WithdrawalEpochCertificate;
import io.horizen.utxo.box.Box;
import com.horizen.librustsidechains.FieldElement;
import io.horizen.proposition.Proposition;
import io.horizen.secret.PrivateKey25519;
import io.horizen.utxo.utils.ForwardTransferCswData;
import io.horizen.utxo.utils.UtxoCswData;
import scala.Enumeration;

import java.util.List;
import java.util.Optional;

public interface CswCircuit {
    int utxoMerkleTreeHeight();

    FieldElement getUtxoMerkleTreeLeaf(Box<Proposition> box);

    byte[] getCertDataHash(WithdrawalEpochCertificate cert, Enumeration.Value sidechainCreationVersion) throws Exception;

    int rangeSize(int withdrawalEpochLength);

    boolean generateCoboundaryMarlinSnarkKeys(int withdrawalEpochLen, String provingKeyPath, String verificationKeyPath);

    byte[] privateKey25519ToScalar(PrivateKey25519 pk);

    byte[] utxoCreateProof(UtxoCswData utxo,
                           WithdrawalEpochCertificate lastActiveCert,
                           byte[] mcbScTxsCumComEnd,
                           byte[] receiverPubKeyHash,
                           PrivateKey25519 pk,
                           int withdrawalEpochLength,
                           byte[] constant,
                           byte[] sidechainId,
                           String provingKeyPath,
                           boolean checkProvingKey,
                           boolean zk,
                           Enumeration.Value sidechainCreationVersion) throws Exception;

    byte[] ftCreateProof(ForwardTransferCswData ft,
                         Optional<WithdrawalEpochCertificate> lastActiveCertOpt,
                         byte[] mcbScTxsCumComStart,
                         List<byte[]> scTxsComHashes,
                         byte[] mcbScTxsCumComEnd,
                         byte[] receiverPubKeyHash,
                         PrivateKey25519 pk,
                         int withdrawalEpochLength,
                         byte[] constant,
                         byte[] sidechainId,
                         String provingKeyPath,
                         boolean checkProvingKey,
                         boolean zk,
                         Enumeration.Value sidechainCreationVersion) throws Exception;
}
