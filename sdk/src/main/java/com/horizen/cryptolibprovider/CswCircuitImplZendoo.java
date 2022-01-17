package com.horizen.cryptolibprovider;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.Box;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.cswnative.CswFtProverData;
import com.horizen.cswnative.CswProof;
import com.horizen.cswnative.CswSysData;
import com.horizen.cswnative.CswUtxoProverData;
import com.horizen.fwtnative.ForwardTransferOutput;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.proposition.Proposition;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.scutxonative.ScUtxoOutput;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.ForwardTransferCswData;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.utils.UtxoCswData;
import com.horizen.utils.WithdrawalEpochUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CswCircuitImplZendoo implements CswCircuit {
    // Note: supportedSegmentSize should correlate with the snark circuit complexity,
    // but is always less or equal the one defined in the MC network (maxSegmentSize).
    private static final int supportedSegmentSize = (1 << 18);

    @Override
    public int utxoMerkleTreeHeight() {
        return Constants.SC_MST_HEIGHT();
    }

    @Override
    public FieldElement getUtxoMerkleTreeLeaf(Box<Proposition> box) {
        ScUtxoOutput utxo = new ScUtxoOutput(box.proposition().bytes(), box.value(), box.nonce(), box.customFieldsHash());
        return utxo.getNullifier();
    }

    @Override
    public byte[] getCertDataHash(WithdrawalEpochCertificate cert) {
        WithdrawalCertificate we = createWithdrawalCertificate(cert);

        FieldElement hashFe = we.getHash();
        byte[] hashBytes = hashFe.serializeFieldElement();

        hashFe.freeFieldElement();
        try {
            we.close();
        } catch (Exception ignored) {
        }

        return hashBytes;
    }

    @Override
    public byte[] privateKey25519ToScalar(PrivateKey25519 pk) {
        byte[] pkBytes = pk.privateKey();

        byte[] hash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            digest.update(pkBytes, 0, pkBytes.length);
            hash = digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }

        // Only the lower 32 bytes are used
        byte[] lowerBytes = Arrays.copyOfRange(hash, 0, 32);

        // Pruning:
        // The lowest three bits of the first octet are cleared
        lowerBytes[0] &= 0b11111000;
        // The highest bit of the last octet is cleared, and the second highest bit of the last octet is set.
        lowerBytes[31] &= 0b01111111;
        lowerBytes[31] |= 0b01000000;

        return lowerBytes;
    }

    @Override
    public int rangeSize(int withdrawalEpochLength) {
        int submissionWindowLength = WithdrawalEpochUtils.certificateSubmissionWindowLength(withdrawalEpochLength);
        return 2 * withdrawalEpochLength + submissionWindowLength;
    }

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(int withdrawalEpochLength, String provingKeyPath, String verificationKeyPath) {
        int rangeSize = rangeSize(withdrawalEpochLength);
        boolean isConstantPresent = true;
        return CswProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, rangeSize, CommonCircuit.customFieldsNumber,
                isConstantPresent, provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }

    private WithdrawalCertificate createWithdrawalCertificate(WithdrawalEpochCertificate cert) {
        return new WithdrawalCertificate(
                FieldElement.deserialize(cert.sidechainId()),
                cert.epochNumber(),
                scala.collection.JavaConverters.seqAsJavaList(cert.backwardTransferOutputs()).stream().map(bto -> new BackwardTransfer(bto.pubKeyHash(), bto.amount())).collect(Collectors.toList()),
                cert.quality(),
                FieldElement.deserialize(cert.endCumulativeScTxCommitmentTreeRoot()),
                cert.ftMinAmount(),
                cert.btrFee(),
                Arrays.stream(cert.customFieldsOpt().get()).map(FieldElement::deserialize).collect(Collectors.toList())
        );
    }

    @Override
    public byte[] utxoCreateProof(UtxoCswData utxo,
                                  WithdrawalEpochCertificate lastActiveCert,
                                  byte[] mcbScTxsCumComEnd,
                                  byte[] senderPubKeyHash,
                                  PrivateKey25519 pk,
                                  int withdrawalEpochLength,
                                  byte[] constant,
                                  byte[] sidechainId,
                                  String provingKeyPath,
                                  boolean checkProvingKey,
                                  boolean zk) {
        WithdrawalCertificate we = createWithdrawalCertificate(lastActiveCert);

        CswSysData sysData = new CswSysData(
                Optional.of(FieldElement.deserialize(constant)),
                Optional.of(we.getHash()),
                Optional.of(FieldElement.deserialize(mcbScTxsCumComEnd)),
                utxo.amount(),
                FieldElement.deserialize(utxo.getNullifier()),
                senderPubKeyHash);

        FieldElement scIdFe = FieldElement.deserialize(sidechainId);

        ScUtxoOutput utxoOutput = new ScUtxoOutput(
                utxo.spendingPubKey(),
                utxo.amount(),
                utxo.nonce(),
                utxo.customHash());

        CswUtxoProverData utxoProverData = new CswUtxoProverData(
                utxoOutput,
                privateKey25519ToScalar(pk),
                MerklePath.deserialize(utxo.utxoMerklePath()));

        byte[] proof = CswProof.createProof(rangeSize(withdrawalEpochLength), CommonCircuit.customFieldsNumber, sysData, scIdFe,
                Optional.of(we), Optional.of(utxoProverData), Optional.empty(), Optional.of(supportedSegmentSize),
                provingKeyPath, checkProvingKey, zk);

        try {
            we.close();
            sysData.close();
            scIdFe.close();
            utxoProverData.close();
        } catch (Exception ignored) {}

        return proof;
    }

    @Override
    public byte[] ftCreateProof(ForwardTransferCswData ft,
                                Optional<WithdrawalEpochCertificate> lastActiveCertOpt,
                                byte[] mcbScTxsCumComStart,
                                List<byte[]> scTxsComHashes,
                                byte[] mcbScTxsCumComEnd,
                                byte[] senderPubKeyHash,
                                PrivateKey25519 pk,
                                int withdrawalEpochLength,
                                byte[] constant,
                                byte[] sidechainId,
                                String provingKeyPath,
                                boolean checkProvingKey,
                                boolean zk){
        Optional<WithdrawalCertificate> weOpt = lastActiveCertOpt.map(this::createWithdrawalCertificate);
        CswSysData sysData = new CswSysData(
                Optional.of(FieldElement.deserialize(constant)),
                weOpt.map(WithdrawalCertificate::getHash),
                Optional.of(FieldElement.deserialize(mcbScTxsCumComEnd)),
                ft.amount(),
                FieldElement.deserialize(ft.getNullifier()),
                senderPubKeyHash);

        FieldElement scIdFe = FieldElement.deserialize(sidechainId);

        ForwardTransferOutput ftOutput = new ForwardTransferOutput(
                ft.amount(),
                BytesUtils.reverseBytes(ft.receiverPubKeyReversed()), // Set receiver bytes in a PubKey255199 bytes original order
                ft.paybackAddrDataHash(),
                ft.txHash(),
                ft.outIdx());

        CswFtProverData ftProverData = new CswFtProverData(
                ftOutput,
                privateKey25519ToScalar(pk),
                FieldElement.deserialize(mcbScTxsCumComStart),
                MerklePath.deserialize(ft.scCommitmentMerklePath()),
                MerklePath.deserialize(ft.ftMerklePath()),
                FieldElement.deserialize(ft.scCrCommitment()),
                FieldElement.deserialize(ft.btrCommitment()),
                FieldElement.deserialize(ft.certCommitment()),
                scTxsComHashes.stream().map(FieldElement::deserialize).collect(Collectors.toList()));

        byte[] proof = CswProof.createProof(rangeSize(withdrawalEpochLength), CommonCircuit.customFieldsNumber, sysData, scIdFe,
                weOpt, Optional.empty(), Optional.of(ftProverData), Optional.of(supportedSegmentSize),
                provingKeyPath, checkProvingKey, zk);

        try {
            sysData.close();
            scIdFe.close();
            if(weOpt.isPresent())
                weOpt.get().close();
            ftProverData.close();
        } catch (Exception ignored) {}

        return proof;
    }
}
