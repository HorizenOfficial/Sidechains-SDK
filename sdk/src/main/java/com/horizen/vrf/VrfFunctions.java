package com.horizen.vrf;

import java.util.List;

public interface VrfFunctions {
    List<byte[]> generate(byte[] seed);
    boolean verify(byte[] publicKey, int slotNumber, byte[] nonceBytes, byte[] proofBytes);
    boolean isValid();
    byte[] proofBytesToVrfHashBytes(byte[] proof);
    byte[] messageToVrfProofBytes(byte[] secretKey, int slotNumber, byte[] nonceBytes);
}
