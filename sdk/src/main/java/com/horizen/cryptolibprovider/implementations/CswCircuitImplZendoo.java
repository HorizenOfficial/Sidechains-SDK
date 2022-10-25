package com.horizen.cryptolibprovider.implementations;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.Box;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.cryptolibprovider.CommonCircuit;
import com.horizen.cryptolibprovider.CswCircuit;
import com.horizen.cswnative.CswFtProverData;
import com.horizen.cswnative.CswProof;
import com.horizen.cswnative.CswSysData;
import com.horizen.cswnative.CswUtxoProverData;
import com.horizen.fwtnative.ForwardTransferOutput;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.proposition.Proposition;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.scutxonative.ScUtxoOutput;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.ForwardTransferCswData;
import com.horizen.utils.UtxoCswData;
import com.horizen.utils.WithdrawalEpochUtils;
import scala.Enumeration;

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
        return utxo.getHash();
    }

    @Override
    public byte[] getCertDataHash(WithdrawalEpochCertificate cert, Enumeration.Value sidechainCreationVersion) throws Exception {
        try(WithdrawalCertificate wc = CommonCircuit.createWithdrawalCertificate(cert, sidechainCreationVersion); FieldElement hashFe = wc.getHash()) {
            return hashFe.serializeFieldElement();
        }
    }

    @Override
    public byte[] privateKey25519ToScalar(PrivateKey25519 pk) {
        byte[] pkBytes = pk.privateKey();

        byte[] hash;
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
        // The highest bit of the last octet is cleared, and the second-highest bit of the last octet is set.
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
        return CswProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, rangeSize, CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW,
                isConstantPresent, Optional.of(supportedSegmentSize), provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }

    @Override
    public byte[] utxoCreateProof(UtxoCswData utxo,
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
                                  Enumeration.Value sidechainCreationVersion) throws Exception {
        try(
                WithdrawalCertificate we = CommonCircuit.createWithdrawalCertificate(lastActiveCert, sidechainCreationVersion);
                CswSysData sysData = new CswSysData(
                    Optional.of(FieldElement.deserialize(constant)),
                    Optional.of(we.getHash()),
                    Optional.of(FieldElement.deserialize(mcbScTxsCumComEnd)),
                    utxo.amount(),
                    FieldElement.deserialize(utxo.getNullifier()),
                    receiverPubKeyHash);
                FieldElement scIdFe = FieldElement.deserialize(sidechainId);
                CswUtxoProverData utxoProverData = new CswUtxoProverData(
                    new ScUtxoOutput(utxo.spendingPubKey(), utxo.amount(), utxo.nonce(), utxo.customHash()),
                    privateKey25519ToScalar(pk),
                    MerklePath.deserialize(utxo.utxoMerklePath()));
        ) {
            return CswProof.createProof(rangeSize(withdrawalEpochLength), CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW, sysData, scIdFe,
                    Optional.of(we), Optional.of(utxoProverData), Optional.empty(), Optional.of(supportedSegmentSize),
                    provingKeyPath, checkProvingKey, zk);
        }
    }

    @Override
    public byte[] ftCreateProof(ForwardTransferCswData ft,
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
                                Enumeration.Value sidechainCreationVersion) throws Exception {
        Optional<WithdrawalCertificate> weOpt = lastActiveCertOpt.map(cert -> CommonCircuit.createWithdrawalCertificate(cert, sidechainCreationVersion));
        try(
                CswSysData sysData = new CswSysData(
                        Optional.of(FieldElement.deserialize(constant)),
                        weOpt.map(WithdrawalCertificate::getHash),
                        Optional.of(FieldElement.deserialize(mcbScTxsCumComEnd)),
                        ft.amount(),
                        FieldElement.deserialize(ft.getNullifier()),
                        receiverPubKeyHash);

                FieldElement scIdFe = FieldElement.deserialize(sidechainId);

                CswFtProverData ftProverData = new CswFtProverData(
                        new ForwardTransferOutput(
                                ft.amount(),
                                BytesUtils.reverseBytes(ft.receiverPubKeyReversed()), // Set receiver bytes in a PubKey255199 bytes original order
                                ft.paybackAddrDataHash(),
                                ft.txHash(),
                                ft.outIdx()),
                        privateKey25519ToScalar(pk),
                        FieldElement.deserialize(mcbScTxsCumComStart),
                        MerklePath.deserialize(ft.scCommitmentMerklePath()),
                        MerklePath.deserialize(ft.ftMerklePath()),
                        FieldElement.deserialize(ft.scCrCommitment()),
                        FieldElement.deserialize(ft.btrCommitment()),
                        FieldElement.deserialize(ft.certCommitment()),
                        scTxsComHashes.stream().map(FieldElement::deserialize).collect(Collectors.toList()))
        ) {
            return CswProof.createProof(rangeSize(withdrawalEpochLength), CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW, sysData, scIdFe,
                    weOpt, Optional.empty(), Optional.of(ftProverData), Optional.of(supportedSegmentSize),
                    provingKeyPath, checkProvingKey, zk);
        } finally {
            if(weOpt.isPresent())
                weOpt.get().close();
        }
    }
}
