package io.horizen.json;

import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainProtocolVersion;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertArrayEquals;

public class SerializationUtilTest {
  @Test
  public void serializeObjectAndDeserializeObjectCanSerializeAndDeserializeCorrectlyAnObject() throws IOException, ClassNotFoundException {
    // Arrange
    CrossChainMessage ccMsg = new CrossChainMessage(
        CrossChainProtocolVersion.VERSION_1, 1, new byte[32], new byte[32], new byte[32], new byte[32], new byte[32]
    );
    List<CrossChainMessage> list = List.of(ccMsg, ccMsg, ccMsg, ccMsg, ccMsg);

    // Act
    byte[] serialized = SerializationUtil.serializeObject(list);
    List<CrossChainMessage> deserialized = SerializationUtil.deserializeObject(serialized).getOrElse(List::of);

    // Assert
    assertEquals(list, deserialized);
  }

  @Test
  public void serializeObjectAndDeserializeObjectCanSerializgeAndDeserializeCorrectlyAnObject() throws IOException, ClassNotFoundException {
    // Arrange
    byte[] emptyArray = new byte[0];

    // Act
    byte[] serialized = SerializationUtil.serializeObject(emptyArray);
    byte[] deserialized = SerializationUtil.deserializeObject(serialized).getOrElse(() -> new byte[0]);

    // Assert
    assertArrayEquals(emptyArray, deserialized);
  }
}