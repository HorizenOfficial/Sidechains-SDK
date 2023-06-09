package io.horizen.utxo.box;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainProtocolVersion;
import io.horizen.utils.Ed25519;
import io.horizen.utils.Pair;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;
import org.junit.Test;
import sparkz.util.ByteArrayBuilder;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.VLQByteBufferReader;
import sparkz.util.serialization.VLQByteBufferWriter;
import sparkz.util.serialization.Writer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class CrossChainRedeemMessageBoxSerializerTest {
    @Test
    public void aCrossChainRedeemMessageBoxSerializedAndThenParsedMustMatchOriginalBox() {
        // Arrange
        ByteArrayBuilder b = new ByteArrayBuilder();
        Writer writer = new VLQByteBufferWriter(b);
        byte[] seed = "12345".getBytes();
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(seed);
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(keyPair.getValue());
        CrossChainMessage message = new CrossChainMessage(
                CrossChainProtocolVersion.VERSION_1,
                0,
                "d504dbfde192182c68d2bcec6e452049".getBytes(StandardCharsets.UTF_8),
                "d504dbfde192182c68d2bcec6e452049".getBytes(StandardCharsets.UTF_8),
                "0303908acce9dd1078bdf16a87a9d9f8".getBytes(StandardCharsets.UTF_8),
                "0303908acce9dd1078bdf16a87a9d9f8".getBytes(StandardCharsets.UTF_8),
                "0303908acce9dd1078bdf16a87a9d9f8".getBytes(StandardCharsets.UTF_8)
        );
        byte[] certificateDataHash = "certificateDataHash".getBytes(StandardCharsets.UTF_8);
        byte[] nextCertificateDataHash = "nextCertificateDataHash".getBytes(StandardCharsets.UTF_8);
        byte[] scCommitmentTreeRoot = "scCommitmentTreeRoot".getBytes(StandardCharsets.UTF_8);
        byte[] nextScCommitmentTreeRoot = "nextScCommitmentTreeRoot".getBytes(StandardCharsets.UTF_8);
        byte[] proof = "proof".getBytes(StandardCharsets.UTF_8);
        CrossChainRedeemMessageBoxData boxData = new CrossChainRedeemMessageBoxData(
                proposition,
                message,
                certificateDataHash,
                nextCertificateDataHash,
                scCommitmentTreeRoot,
                nextScCommitmentTreeRoot,
                proof
        );
        long nonce = 123456789;
        CrossChainRedeemMessageBox box = new CrossChainRedeemMessageBox(boxData, nonce);
        CrossChainRedeemMessageBoxSerializer serializer = CrossChainRedeemMessageBoxSerializer.getSerializer();

        // Act
        serializer.serialize(box, writer);
        Reader reader = new VLQByteBufferReader(ByteBuffer.wrap(b.toBytes()));
        CrossChainRedeemMessageBox parsedBox = serializer.parse(reader);

        // Assert
        assertEquals(parsedBox, box);
    }
}