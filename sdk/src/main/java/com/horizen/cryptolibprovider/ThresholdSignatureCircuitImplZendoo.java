package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.sigproofnative.BackwardTransfer;
import com.horizen.sigproofnative.CreateProofResult;
import com.horizen.sigproofnative.NaiveThresholdSigProof;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitImplZendoo implements ThresholdSignatureCircuit {
    private static final SchnorrSignature signaturePlaceHolder = new SchnorrSignature();

    private static BackwardTransfer withdrawalRequestBoxToBackwardTransfer(WithdrawalRequestBox box) {
        return new BackwardTransfer(box.proposition().bytes(), box.value());
    }

    @Override
    public byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt, byte[] endWithdrawalEpochBlockHash, byte[] prevEndWithdrawalEpochBlockHash) {
        BackwardTransfer[] backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).toArray(BackwardTransfer[]::new);
        FieldElement messageToSign = NaiveThresholdSigProof.createMsgToSign(backwardTransfers, endWithdrawalEpochBlockHash, prevEndWithdrawalEpochBlockHash);
        byte[] messageAsBytes = messageToSign.serializeFieldElement();

        messageToSign.freeFieldElement();
        return messageAsBytes;
    }

    @Override
    public Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                          byte[] endEpochBlockHash,
                                          byte[] prevEndEpochBlockHash,
                                          List<byte[]> schnorrPublicKeysBytesList,
                                          List<Optional<byte[]>> schnorrSignatureBytesList,
                                          long threshold,
                                          String provingKeyPath){
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        List<SchnorrSignature> signatures = schnorrSignatureBytesList
                                                .stream()
                                                .map(signatureBytesOpt -> signatureBytesOpt.map(SchnorrSignature::deserialize).orElse(signaturePlaceHolder))
                                                .collect(Collectors.toList());

        List<SchnorrPublicKey> publicKeys =
                schnorrPublicKeysBytesList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        CreateProofResult proofAndQuality = NaiveThresholdSigProof.createProof(
                backwardTransfers, endEpochBlockHash, prevEndEpochBlockHash, signatures, publicKeys, threshold, provingKeyPath);

        publicKeys.forEach(SchnorrPublicKey::freePublicKey);
        signatures.forEach(SchnorrSignature::freeSignature);

        return new Pair<>(proofAndQuality.getProof(), proofAndQuality.getQuality());
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash, long quality, byte[] proof, byte[] sysDataConstant, String verificationKeyPath) {
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        FieldElement constant = FieldElement.deserialize(sysDataConstant);
        boolean verificationResult =
                NaiveThresholdSigProof.verifyProof(backwardTransfers, endEpochBlockHash, prevEndEpochBlockHash, constant, quality, proof, verificationKeyPath);

        constant.freeFieldElement();

        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold){
        List<SchnorrPublicKey> schnorrPublicKeys = publicKeysList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        FieldElement sysDataConstant = NaiveThresholdSigProof.getConstant(schnorrPublicKeys, threshold);
        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();
        schnorrPublicKeys.forEach(SchnorrPublicKey::freePublicKey);

        return sysDataConstantBytes;
    }

    @Override
    public int sysDataConstantLength() {
        return PoseidonHash.HASH_LENGTH;
    }

    // TODO: CoboundaryMarlin proofs has not a constant Vk and Proof size, remove the length/size methods below
    @Override
    public int proofSizeLength() {
        return 771; //@TODO take it from JNI side
    }

    @Override
    public int certVkSize() {
        return 1544; //@TODO take it from JNI side
    }

    // Note: segmentSize should correlate with the snark circuit complexity, but is always less or equal the one defined in the MC network.
    private static final int segmentSize = 2^15;

    @Override
    public boolean generateCoboundaryMarlinDLogKeys(String g1KeyPath) {
        return ProvingSystem.generateDLogKeys(ProvingSystemType::CoboundaryMarlin, segmentSize, g1KeyPath, Optional.empty());
    }

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath) {
        return NaiveThresholdSigProof.setup(ProvingSystemType::CoboundaryMarlin, maxPks, provingKeyPath, verificationKeyPath);
    }

    @Override
    public String getCoboundaryMarlinSnarkVerificationKeyHex(String verificationKeyPath) {
        // groth16 valid verification key for 7 pub keys and threshold = 5
        //return "5e7b462cc84ae0faaa5884bd5c4a5a5edf13db210599aeeb4d273c0f5f32967b7071ce2b4d490b9f08f6ce66a8405735c79197cd6773d1c5aeb2a38da1c102df07b05879c77198e5aafa7feed25d4137e86b3d98d9edd9547a460f1615b10000ee9570fbffedd44170477b37500a0a1cb3f94b6361f10f8a68c4075fbc17542d7174b3d95e12ddb8aea5d6b6c53c1df6c8f60010cd2e69902ba5e89e86747569463a23254730fc8d2aabf39648a505df9dcce461443b181ef3eda46074070000550836db2c97820971db6b1421e348d946ed4d3f255295abea46556615e3123de33ec56f784f70302901a4bc10c79c6a8b1e32477aeff9fba75876592981b678fc5a2703ac0b3055e567a6cb1ebab578fc4f9121fd968680250696cb85790000078fcfb60bdfc79aa1e377cb120480538e0236156f23129a88824ca5a1d77e371e5e98a16e6f32087c91aa02a4f5e00e412e515c3b678f6535141203c6886c637b626a2ada4062d037503359a680979091c68941a307db6e4ed8bc49d21b00002f0e6f88fb69309873fdefb015569e5511fb5399295204876543d065d177bf36ab79183a7c5e504b50691bc5b4ed0293324cfe2555d3fc8e39485822a90a91afcd4ef79ec3aefbd4cbe25cbccd802d8334ce1dce238c3f7505330a14615500001f89fbe1922ab3aa31a28fd29e19673714a7e48050dee59859d68345bb7bee7d5e888d8b798a58d7c650f9138304c05a92b668294c6114185ccb2c67ce0bbbb7e1dcbb6d76f5cacd7c9732a33b21d69bd7a28c9cca68b5735d50413862bc0100308bb0dd0bd53f3d1134966702dd3c7cc8b58b270a6996a646493250b0d5f3978d0c971f8fa7a0c958f3efe2fa5269244973fafb701c2eb66dd25901f93d677ab6c538c1ed11f115e52d3f2c7087ea40c3e8cd089376baa38842e9429b5f0000d19a8d874d791f952f13d3c8ecd92e44009c09815e5ae6a8e5def7ea52fe3de4accfb5ba2aa401fbcec14b069cd0dc0f66ab025b45ef9831a26acf58673db7487043654e7980fcb2b6c1bd7593a4dfff810436f653e309121c7ccf2df70b010000732254ec6df184be360cd9ed383ed7c8c236d7761cfc0ce4e7f0cac5a06f4edab9cfc75a7dc1449c0e18ed9564c974c2e1b6847c637f74e5d391cbc80fc6e672ffd66b5ce4fb73bda8359ab8a0ea1e855df1e07d82f93c935c7e1a9a55c5000065efdbb7c3e82291a482b2f24cbd46f4dd02c370cf6dcfe8fb3c00b8b004b5ad51369b1f1b134a824d1f16d72ca6a27ba2d6190150329139cf2c6d9e5a14722f8d39b96b882c1f60a7b230e929819e2abe1cd9d7f3e8c726b1a94d20c8010100732c396eca6ffa1bf851cef449f2f087edd93e4f641b4bd93a482d9f129e675aedb688993d4e2cee824d2803301364ba10fbb66895927adb53bad8aefe8a1caab6f4ccb45883e414a1223ac7f90a89087cd752dfa0c7b3e19bbae000edd5000028d1d23c627d1252d2a2a20a246af2280f50e3fde667873aadd9893ba6833118358398e7428e717128f764714a8d52b090c1f554f58e25ea815338d7bc7326c949567e74f2f2ab3c88f5075fea75594608b8937c9059a42d712ffbd1bd980100000000000250c1a474689e375a309446e5cdd3a0c26cecdcff5c7b8cdc0728868983f1a35a49e3a1bae6f969c3d47356c08d3d169d2c0a2be908d82cd35f41a23d8c2924a9f790ab3a00d53061d440a176670d6a32de2ecd19cf8a9774729c09a6ea4d0100d8838bf55d95521291da12294b302c66042eda0dc2acc79360a1fdd8c9a366fa790c52bf926c2d96b5ba88a3a443487c5235f7c476f350c2101cfbe3bd0361dd291ebc5e42c097a158704b71006886a3662ca6db7d816b4ad12444835d89000000795ce2b34aef921ccb3d9b9695f5d3fe0a03743c955cfcf01f8a1815a7c8b03de85fe15201d4b4b6f401cb334a6988ea5bde8986a468c47c3c6a5ae96a3160ff15e06699ea82bd40c0d5547fe1be77af7817861bbfcca3f4232f05a9cec800006c216565cee4d57b32d2d70bb3cb8d4a967c0eb5d7137b2ec58466f3d4d3b5375e4baa823bcc29c6ad877d9708cd5dc1c31fa3883a80710431110c4aa22e97b67fa639f54e86cfab87187011270139df7873bed12f6fb8cd9ab48f389338010000";

        if(!Files.exists(Paths.get(verificationKeyPath)))
            return "";

        try {
            return BytesUtils.toHexString(Files.readAllBytes(Paths.get(verificationKeyPath)));
        } catch (IOException e) {
            return "";
        }
    }
}
