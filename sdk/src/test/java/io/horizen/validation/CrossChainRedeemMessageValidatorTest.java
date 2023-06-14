package io.horizen.validation;

import io.horizen.GenesisDataSettings;
import io.horizen.SidechainSettings;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.params.NetworkParams;
import io.horizen.proof.Signature25519;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.utils.BytesUtils;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;
import io.horizen.utxo.box.data.ZenBoxData;
import io.horizen.utxo.storage.SidechainStateStorage;
import io.horizen.utxo.transaction.AbstractCrossChainRedeemTransaction;
import io.horizen.validation.crosschain.receiver.CrossChainRedeemMessageValidator;
import org.junit.Test;
import scala.Option;
import sparkz.core.serialization.BytesSerializable;
import sparkz.core.serialization.SparkzSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrossChainRedeemMessageValidatorTest {

    private static class CrossChainRedeemTransactionMock extends AbstractCrossChainRedeemTransaction {
        public CrossChainRedeemTransactionMock(List<byte[]> inputZenBoxIds, List<Signature25519> inputZenBoxProofs, List<ZenBoxData> outputZenBoxesData, long fee, CrossChainRedeemMessageBoxData redeemMessageBox) {
            super(inputZenBoxIds, inputZenBoxProofs, outputZenBoxesData, fee, redeemMessageBox);
        }

        @Override
        public byte transactionTypeId() {
            return 0;
        }

        @Override
        public byte version() {
            return 0;
        }

        @Override
        public byte[] customFieldsData() {
            return new byte[0];
        }

        @Override
        public byte[] customDataMessageToSign() {
            return new byte[0];
        }

        @Override
        public SparkzSerializer<BytesSerializable> serializer() {
            return null;
        }
    }

    private final SidechainStateStorage scStateStorage = mock(SidechainStateStorage.class);
    private final Sc2scCircuit sc2scCircuit = mock(Sc2scCircuit.class);
    private final CrossChainRedeemMessageBoxData redeemMessageBox = mock(CrossChainRedeemMessageBoxData.class);
    private final CrossChainMessage crossChainMessage = mock(CrossChainMessage.class);
    private final byte[] scId = BytesUtils.fromHexString("a3adf0b3c8f3c570f058a370f98d14bd");
    private final CrossChainMessageHash crossChainMsgHash = mock(CrossChainMessageHash.class);
    private final byte[] scTxCommitmentTreeHash = "scTxCommitmentTreeHashOf32Chars!".getBytes(StandardCharsets.UTF_8);
    private final byte[] nextScTxCommitmentTreeHash = "nextScTxCommitmentTreeHashOf32Ch".getBytes(StandardCharsets.UTF_8);
    private final byte[] certificateDataHash = "certificateDataHash".getBytes(StandardCharsets.UTF_8);
    private final byte[] nextCertificateDataHash = "nextCertificateDataHash".getBytes(StandardCharsets.UTF_8);
    private final byte[] proof = "proof".getBytes(StandardCharsets.UTF_8);
    private final NetworkParams networkParams = mock(NetworkParams.class);

    @Test
    public void whenReceivingScIdIsDifferentThenTheScIdInSettings_throwsAnIllegalArgumentException() {
        // Arrange
        byte[] badScIdHex = BytesUtils.fromHexString("0b3c8f3c570f058a37a3adf0f98d14bd");
        String revBadScId = BytesUtils.toHexString(BytesUtils.toMainchainFormat(badScIdHex));
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                scStateStorage, sc2scCircuit, networkParams
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(scId);
        when(networkParams.sidechainId()).thenReturn(badScIdHex);

        // Act
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = String.format("Receiver sidechain id `%s` does not match with this sidechain id `%s`", BytesUtils.toHexString(scId), revBadScId);
        assertEquals(expectedMsg, thrown.getMessage());
    }

    @Test
    public void whenTryToRedeemTheSameMessageTwice_throwsAnIllegalArgumentException() throws Exception {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                scStateStorage, sc2scCircuit, networkParams
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(scId);
        when(networkParams.sidechainId()).thenReturn(BytesUtils.reverseBytes(scId));

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(true);

        // Act
        Exception thrown = assertThrows(
                Exception.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = "The message `Mock for CrossChainMessage, hashCode:";
        String expectedMsg2 = "has already been redeemed";
        String exceptionMsg = thrown.getMessage();
        assertTrue(exceptionMsg.contains(expectedMsg));
        assertTrue(exceptionMsg.contains(expectedMsg2));
    }

    @Test
    public void whenScTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException() throws Exception {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                scStateStorage, sc2scCircuit, networkParams
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(scId);
        when(networkParams.sidechainId()).thenReturn(BytesUtils.reverseBytes(scId));

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(false);

        when(redeemMessageBox.getScCommitmentTreeRoot()).thenReturn(scTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(scTxCommitmentTreeHash)).thenReturn(false);

        // Act
        Exception thrown = assertThrows(
                Exception.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = String.format("Sidechain commitment tree root `%s` does not exist", BytesUtils.toHexString(scTxCommitmentTreeHash));
        String exceptionMsg = thrown.getMessage();
        assertEquals(expectedMsg, exceptionMsg);
    }

    @Test
    public void whenNextScTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException() throws Exception {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                scStateStorage, sc2scCircuit, networkParams
        );
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(scId);
        when(networkParams.sidechainId()).thenReturn(BytesUtils.reverseBytes(scId));

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(false);

        when(redeemMessageBox.getScCommitmentTreeRoot()).thenReturn(scTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(scTxCommitmentTreeHash)).thenReturn(true);
        when(redeemMessageBox.getNextScCommitmentTreeRoot()).thenReturn(nextScTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(nextScTxCommitmentTreeHash)).thenReturn(false);

        // Act
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = String.format("Next sidechain commitment tree root `%s` does not exist", BytesUtils.toHexString(nextScTxCommitmentTreeHash));
        String exceptionMsg = thrown.getMessage();
        assertEquals(expectedMsg, exceptionMsg);
    }

    @Test
    public void whenProofIsNotValid_throwsAnIllegalArgumentException() throws Exception {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                scStateStorage, sc2scCircuit, networkParams
        );
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(scId);
        when(networkParams.sidechainId()).thenReturn(BytesUtils.reverseBytes(scId));

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(false);

        when(redeemMessageBox.getScCommitmentTreeRoot()).thenReturn(scTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(scTxCommitmentTreeHash)).thenReturn(true);
        when(redeemMessageBox.getNextScCommitmentTreeRoot()).thenReturn(nextScTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(nextScTxCommitmentTreeHash)).thenReturn(true);

        when(redeemMessageBox.getCertificateDataHash()).thenReturn(certificateDataHash);
        when(redeemMessageBox.getNextCertificateDataHash()).thenReturn(nextCertificateDataHash);
        when(redeemMessageBox.getProof()).thenReturn(proof);
        when(networkParams.sc2ScVerificationKeyFilePath()).thenReturn(Option.apply("path"));

        when(sc2scCircuit.verifyRedeemProof(
                crossChainMsgHash,
                redeemMessageBox.getScCommitmentTreeRoot(),
                redeemMessageBox.getNextScCommitmentTreeRoot(),
                redeemMessageBox.getProof(),
                networkParams.sc2ScVerificationKeyFilePath().get()
        )).thenReturn(false);

        // Act
        Exception thrown = assertThrows(
                Exception.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = String.format("Cannot verify this cross-chain message: `%s`", crossChainMessage);
        String exceptionMsg = thrown.getMessage();
        assertEquals(expectedMsg, exceptionMsg);
    }

    @Test
    public void whenAllValidationsPass_throwsNoException() throws Exception {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                scStateStorage, sc2scCircuit, networkParams
        );
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(scId);
        when(networkParams.sidechainId()).thenReturn(BytesUtils.reverseBytes(scId));

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(false);

        when(redeemMessageBox.getScCommitmentTreeRoot()).thenReturn(scTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(scTxCommitmentTreeHash)).thenReturn(true);
        when(redeemMessageBox.getNextScCommitmentTreeRoot()).thenReturn(nextScTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(nextScTxCommitmentTreeHash)).thenReturn(true);

        when(redeemMessageBox.getCertificateDataHash()).thenReturn(certificateDataHash);
        when(redeemMessageBox.getNextCertificateDataHash()).thenReturn(nextCertificateDataHash);
        when(networkParams.sc2ScVerificationKeyFilePath()).thenReturn(Option.apply("path"));
        when(redeemMessageBox.getProof()).thenReturn(proof);

        when(sc2scCircuit.verifyRedeemProof(
                crossChainMsgHash,
                redeemMessageBox.getScCommitmentTreeRoot(),
                redeemMessageBox.getNextScCommitmentTreeRoot(),
                redeemMessageBox.getProof(),
                networkParams.sc2ScVerificationKeyFilePath().get()
        )).thenReturn(true);

        // Act & Assert
        try {
            validator.validate(sidechainBlock);
        } catch (Exception e) {
            fail("Test failed unexpectedly");
        }
    }
}