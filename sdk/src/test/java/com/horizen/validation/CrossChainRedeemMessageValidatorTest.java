package com.horizen.validation;

import com.horizen.GenesisDataSettings;
import com.horizen.SidechainSettings;
import com.horizen.block.SidechainBlock;
import com.horizen.box.data.CrossChainRedeemMessageBoxData;
import com.horizen.box.data.ZenBoxData;
import com.horizen.cryptolibprovider.Sc2scCircuit;
import com.horizen.cryptolibprovider.utils.FieldElementUtils;
import com.horizen.proof.Signature25519;
import com.horizen.sc2sc.CrossChainMessage;
import com.horizen.sc2sc.CrossChainMessageHash;
import com.horizen.storage.SidechainStateStorage;
import com.horizen.transaction.AbstractCrossChainRedeemTransaction;
import com.horizen.utils.BytesUtils;
import com.horizen.validation.crosschain.receiver.CrossChainRedeemMessageValidator;
import org.junit.Test;
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

    private final SidechainSettings sidechainSettings = mock(SidechainSettings.class);
    private final SidechainStateStorage scStateStorage = mock(SidechainStateStorage.class);
    private final Sc2scCircuit sc2scCircuit = mock(Sc2scCircuit.class);
    private final CrossChainRedeemMessageBoxData redeemMessageBox = mock(CrossChainRedeemMessageBoxData.class);
    private final CrossChainMessage crossChainMessage = mock(CrossChainMessage.class);
    private final GenesisDataSettings genesisDataSettings = mock(GenesisDataSettings.class);
    private final String scId = BytesUtils.toHexString("scId".getBytes());
    private final CrossChainMessageHash crossChainMsgHash = mock(CrossChainMessageHash.class);
    private final byte[] scTxCommitmentTreeHash = "scTxCommitmentTreeHash".getBytes(StandardCharsets.UTF_8);
    private final byte[] nextScTxCommitmentTreeHash = "nextScTxCommitmentTreeHash".getBytes(StandardCharsets.UTF_8);
    private final byte[] certificateDataHash = "certificateDataHash".getBytes(StandardCharsets.UTF_8);
    private final byte[] nextCertificateDataHash = "nextCertificateDataHash".getBytes(StandardCharsets.UTF_8);
    private final byte[] proof = "proof".getBytes(StandardCharsets.UTF_8);

    @Test
    public void whenReceivingScIdIsDifferentThenTheScIdInSettings_throwsAnIllegalArgumentException() {
        // Arrange
        String badScIdHex = BytesUtils.toHexString("badScIdHex".getBytes());
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                sidechainSettings, scStateStorage, sc2scCircuit
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString(scId));
        when(sidechainSettings.genesisData()).thenReturn(genesisDataSettings);
        when(genesisDataSettings.scId()).thenReturn(badScIdHex);

        // Act
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = String.format("Receiver sidechain id `%s` does not match with this sidechain id `%s`", scId, badScIdHex);
        assertEquals(expectedMsg, thrown.getMessage());
    }

    @Test
    public void whenTryToRedeemTheSameMessageTwice_throwsAnIllegalArgumentException() {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                sidechainSettings, scStateStorage, sc2scCircuit
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString(scId));
        when(sidechainSettings.genesisData()).thenReturn(genesisDataSettings);
        when(genesisDataSettings.scId()).thenReturn(scId);

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(true);

        // Act
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
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
    public void whenScTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException() {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                sidechainSettings, scStateStorage, sc2scCircuit
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString(scId));
        when(sidechainSettings.genesisData()).thenReturn(genesisDataSettings);
        when(genesisDataSettings.scId()).thenReturn(scId);

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(false);

        when(redeemMessageBox.getScCommitmentTreeRoot()).thenReturn(scTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(scTxCommitmentTreeHash)).thenReturn(false);

        // Act
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = String.format("Sidechain commitment tree root `%s` does not exist", Arrays.toString(scTxCommitmentTreeHash));
        String exceptionMsg = thrown.getMessage();
        assertEquals(expectedMsg, exceptionMsg);
    }

    @Test
    public void whenNextScTxCommitmentTreeHashDoesNotExist_throwsAnIllegalArgumentException() {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                sidechainSettings, scStateStorage, sc2scCircuit
        );
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString(scId));
        when(sidechainSettings.genesisData()).thenReturn(genesisDataSettings);
        when(genesisDataSettings.scId()).thenReturn(scId);

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
        String expectedMsg = String.format("Next sidechain commitment tree root `%s` does not exist", Arrays.toString(nextScTxCommitmentTreeHash));
        String exceptionMsg = thrown.getMessage();
        assertEquals(expectedMsg, exceptionMsg);
    }

    @Test
    public void whenNextScTxCommitmentTreeHashDoesNotExistd_throwsAnIllegalArgumentException() {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                sidechainSettings, scStateStorage, sc2scCircuit
        );
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString(scId));
        when(sidechainSettings.genesisData()).thenReturn(genesisDataSettings);
        when(genesisDataSettings.scId()).thenReturn(scId);

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(false);

        when(redeemMessageBox.getScCommitmentTreeRoot()).thenReturn(scTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(scTxCommitmentTreeHash)).thenReturn(true);
        when(redeemMessageBox.getNextScCommitmentTreeRoot()).thenReturn(nextScTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(nextScTxCommitmentTreeHash)).thenReturn(true);

        when(redeemMessageBox.getCertificateDataHash()).thenReturn(certificateDataHash);
        when(redeemMessageBox.getNextCertificateDataHash()).thenReturn(nextCertificateDataHash);
        when(redeemMessageBox.getProof()).thenReturn(proof);

        when(sc2scCircuit.verifyRedeemProof(
                crossChainMsgHash,
                FieldElementUtils.messageToFieldElement(redeemMessageBox.getScCommitmentTreeRoot()),
                FieldElementUtils.messageToFieldElement(redeemMessageBox.getNextScCommitmentTreeRoot()),
                redeemMessageBox.getProof()
        )).thenReturn(false);

        // Act
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(sidechainBlock)
        );

        // Assert
        String expectedMsg = String.format("Cannot verify this cross-chain message: `%s`", crossChainMessage);
        String exceptionMsg = thrown.getMessage();
        assertEquals(expectedMsg, exceptionMsg);
    }

    @Test
    public void whenAllValidationsPass_throwsNoException() {
        // Arrange
        CrossChainRedeemMessageValidator validator = new CrossChainRedeemMessageValidator(
                sidechainSettings, scStateStorage, sc2scCircuit
        );
        CrossChainRedeemTransactionMock txToBeValidated = new CrossChainRedeemTransactionMock(
                List.of(), List.of(), List.of(), 1, redeemMessageBox
        );
        SidechainBlock sidechainBlock = mock(SidechainBlock.class);

        when(sidechainBlock.transactions()).thenReturn(scala.jdk.CollectionConverters.asScalaBuffer(List.of(txToBeValidated)));
        when(redeemMessageBox.getMessage()).thenReturn(crossChainMessage);
        when(crossChainMessage.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString(scId));
        when(sidechainSettings.genesisData()).thenReturn(genesisDataSettings);
        when(genesisDataSettings.scId()).thenReturn(scId);

        when(sc2scCircuit.getCrossChainMessageHash(crossChainMessage)).thenReturn(crossChainMsgHash);
        when(scStateStorage.doesCrossChainMessageHashFromRedeemMessageExist(crossChainMsgHash)).thenReturn(false);

        when(redeemMessageBox.getScCommitmentTreeRoot()).thenReturn(scTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(scTxCommitmentTreeHash)).thenReturn(true);
        when(redeemMessageBox.getNextScCommitmentTreeRoot()).thenReturn(nextScTxCommitmentTreeHash);
        when(scStateStorage.doesScTxCommitmentTreeRootExist(nextScTxCommitmentTreeHash)).thenReturn(true);

        when(redeemMessageBox.getCertificateDataHash()).thenReturn(certificateDataHash);
        when(redeemMessageBox.getNextCertificateDataHash()).thenReturn(nextCertificateDataHash);
        when(redeemMessageBox.getProof()).thenReturn(proof);

        when(sc2scCircuit.verifyRedeemProof(
                crossChainMsgHash,
                FieldElementUtils.messageToFieldElement(redeemMessageBox.getScCommitmentTreeRoot()),
                FieldElementUtils.messageToFieldElement(redeemMessageBox.getNextScCommitmentTreeRoot()),
                redeemMessageBox.getProof()
        )).thenReturn(true);

        // Act & Assert
        try {
            validator.validate(sidechainBlock);
        } catch (Exception e) {
            fail("Test failed unexpectedly");
        }
    }
}