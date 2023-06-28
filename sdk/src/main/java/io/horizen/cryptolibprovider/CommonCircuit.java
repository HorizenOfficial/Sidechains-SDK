package io.horizen.cryptolibprovider;

import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystem;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrSignature;
import io.horizen.block.MainchainBackwardTransferCertificateOutput;
import io.horizen.block.WithdrawalEpochCertificate;
import io.horizen.utils.BytesUtils;
import io.horizen.utxo.box.WithdrawalRequestBox;
import scala.Enumeration;
import scala.collection.Seq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class  CommonCircuit {
    private static final int maxSegmentSize = (1 << 18);

    // Keys total max size values are the same as in MC
    public static final int maxProofPlusVkSize = 9 * 1024;

    // 2 custom fields that represent UtxoMerkleTreeRoot
    public static final int CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW = 2;

    public static final int CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_NO_KEY_ROTATION = 0;

    // 32 custom fields for a possible future features placeholder.
    // The first field is used for certificate keys merkle root.
    // Two fields are dedicated for SC2SC feature: merkle root of messages and prev epoch top quality cert data hash.
    // In general the idea is to have different sidecahins with different circuits to be compatible with SC2SC circuit.
    // For example, for Latus circuit (at least 13 custom fields for own needs) with SC2SC support.
    public static final int CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION = 32;


    public boolean generateCoboundaryMarlinDLogKeys() {
        return ProvingSystem.generateDLogKeys(
                ProvingSystemType.COBOUNDARY_MARLIN,
                maxSegmentSize);
    }

    public String getCoboundaryMarlinSnarkVerificationKeyHex(String verificationKeyPath) {
        if(!Files.exists(Paths.get(verificationKeyPath)))
            return "";

        try {
            return BytesUtils.toHexString(Files.readAllBytes(Paths.get(verificationKeyPath)));
        } catch (IOException e) {
            return "";
        }
    }

    public static WithdrawalCertificate createWithdrawalCertificate(WithdrawalEpochCertificate cert, Enumeration.Value sidechainCreationVersion) {
        return new WithdrawalCertificate(
                FieldElement.deserialize(cert.sidechainId()),
                cert.epochNumber(),
                scala.collection.JavaConverters.<MainchainBackwardTransferCertificateOutput>seqAsJavaList(cert.backwardTransferOutputs())
                        .stream().map(bto -> new BackwardTransfer(bto.pubKeyHash(), bto.amount())).collect(Collectors.toList()),
                cert.quality(),
                FieldElement.deserialize(cert.endCumulativeScTxCommitmentTreeRoot()),
                cert.ftMinAmount(),
                cert.btrFee(),
                Arrays.stream(cert.customFieldsOpt(sidechainCreationVersion).get()).map(FieldElement::deserialize).collect(Collectors.toList())
        );
    }

    // NOTE: this method refers to the mainchain issue reported here https://github.com/HorizenOfficial/Sidechains-SDK/blob/dev/sdk/src/main/scala/io/horizen/block/SidechainCommitmentTree.scala#L74
    // when we need to create a WithdrawalCertificate from a WithdrawalEpochCertificate created in mainchain, use this function to not double swap the two parameters
    public static WithdrawalCertificate createWithdrawalCertificateWithBtrFreeAndFtMinAmountSwapped(WithdrawalEpochCertificate cert, Enumeration.Value sidechainCreationVersion) {
        return new WithdrawalCertificate(
                FieldElement.deserialize(cert.sidechainId()),
                cert.epochNumber(),
                scala.collection.JavaConverters.seqAsJavaList(cert.backwardTransferOutputs())
                        .stream().map(bto -> new BackwardTransfer(bto.pubKeyHash(), bto.amount())).collect(Collectors.toList()),
                cert.quality(),
                FieldElement.deserialize(cert.endCumulativeScTxCommitmentTreeRoot()),
                cert.btrFee(),
                cert.ftMinAmount(),
                Arrays.stream(cert.customFieldsOpt(sidechainCreationVersion).get()).map(FieldElement::deserialize).collect(Collectors.toList())
        );
    }

    public byte[] getCertDataHash(WithdrawalEpochCertificate cert, Enumeration.Value sidechainCreationVersion) {
        try(WithdrawalCertificate wc = createWithdrawalCertificateWithBtrFreeAndFtMinAmountSwapped(cert, sidechainCreationVersion); FieldElement hashFe = wc.getHash()) {
            return hashFe.serializeFieldElement();
        }
    }

    public static List<SchnorrSignature> getSignatures(List<Optional<byte[]>> schnorrSignatureBytesList){
        return schnorrSignatureBytesList
                .stream()
                .map(signatureBytesOpt -> signatureBytesOpt.map(SchnorrSignature::deserialize).orElse(new SchnorrSignature()))
                .collect(Collectors.toList());
    }

    public static List<BackwardTransfer> getBackwardTransfers(List<WithdrawalRequestBox> withdrawalRequestBoxes){
        return withdrawalRequestBoxes.stream()
                .map(box -> new BackwardTransfer(box.proposition().bytes(), box.value())).collect(Collectors.toList());
    }

    public static List<BackwardTransfer> getBackwardTransfers(Seq<WithdrawalRequestBox> withdrawalRequestBoxes){
        return getBackwardTransfers(scala.collection.JavaConverters.<WithdrawalRequestBox>seqAsJavaList(withdrawalRequestBoxes));
    }
}
