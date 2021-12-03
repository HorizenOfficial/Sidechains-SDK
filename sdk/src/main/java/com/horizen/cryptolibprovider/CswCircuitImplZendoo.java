package com.horizen.cryptolibprovider;

import com.horizen.box.Box;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.proposition.Proposition;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.certnative.NaiveThresholdSigProof;
import com.horizen.scutxonative.ScUtxoOutput;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.WithdrawalEpochUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class CswCircuitImplZendoo implements CswCircuit {

    @Override
    public int utxoMerkleTreeHeight() {
        // TODO: choose proper merkle tree height compatible with the CSW circuit.
        return 12;
    }

    @Override
    public FieldElement getUtxoMerkleTreeLeaf(Box<Proposition> box) {
        ScUtxoOutput utxo = new ScUtxoOutput(box.proposition().bytes(), box.value(), box.nonce(), box.customFieldsHash());
        return utxo.getNullifier();
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

    private long rangeSize(int withdrawalEpochLength) {
        int submissionWindowLength = WithdrawalEpochUtils.certificateSubmissionWindowLength(withdrawalEpochLength);
        return 2L * withdrawalEpochLength + submissionWindowLength;
    }

    public boolean generateCoboundaryMarlinKeys(int withdrawalEpochLength, String provingKeyPath, String verificationKeyPath) {
        long rangeSize = rangeSize(withdrawalEpochLength); // TODO Use it in future implementation of CSW circuit

        // TODO Replace with corresponding method for CSW keypair.
        return NaiveThresholdSigProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, 1, 2, provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }
}
