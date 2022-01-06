package com.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import com.horizen.utils.Ed25519;
import scala.util.Try;

import com.horizen.secret.PrivateKey25519;
import com.horizen.ScorexEncoding;

import scorex.crypto.hash.Blake2b256;
import com.google.common.primitives.Bytes;

import java.util.Arrays;

@JsonView(Views.Default.class)
public final class PublicKey25519Proposition
    extends ScorexEncoding
    implements ProofOfKnowledgeProposition<PrivateKey25519>
{
    public static final byte ADDRESS_VERSION = 1;
    public static final int CHECKSUM_LENGTH = 4;
    public static final int KEY_LENGTH = Ed25519.publicKeyLength();
    public static final int ADDRESS_LENGTH = 1 + KEY_LENGTH + CHECKSUM_LENGTH;

    @JsonProperty("publicKey")
    private byte[] _pubKeyBytes;

    public PublicKey25519Proposition(byte[] pubKeyBytes)
    {
        if(pubKeyBytes.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", KEY_LENGTH, pubKeyBytes.length));

        _pubKeyBytes = Arrays.copyOf(pubKeyBytes, KEY_LENGTH);
    }

    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(_pubKeyBytes, KEY_LENGTH);
    }

    @Override
    public PropositionSerializer serializer() {
        return PublicKey25519PropositionSerializer.getSerializer();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pubKeyBytes());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof PublicKey25519Proposition))
            return false;
        if (obj == this)
            return true;
        return Arrays.equals(pubKeyBytes(), ((PublicKey25519Proposition) obj).pubKeyBytes());
    }

    private byte[] pubKeyBytesWithVersion() {
        return Bytes.concat(new byte[] { ADDRESS_VERSION }, pubKeyBytes());
    }

    public static byte[] calcCheckSum(byte[] bytes) {
        return Arrays.copyOf(Blake2b256.hash(bytes), CHECKSUM_LENGTH);
    }

    public String address() {
        return encoder().encode(Bytes.concat(pubKeyBytesWithVersion(), calcCheckSum(pubKeyBytesWithVersion())));
    }

    @Override
    public String toString() {
        return address();
    }

    public boolean verify(byte[] message, byte[] signature) {
        return Ed25519.verify(signature, message, pubKeyBytes());
    }

    // TO DO: should we return null if something going wrong or throw exception?
    public static PublicKey25519Proposition parseAddress(String address) {
        Try<byte[]> res = encoder().decode(address);
        if(res.isFailure())
            throw new IllegalArgumentException("Wrong address encoding");

        byte[] addressBytes = res.get();
        if(addressBytes.length != ADDRESS_LENGTH)
            throw new IllegalArgumentException("Wrong address length");

        byte[] bytesWithVersion = Arrays.copyOf(addressBytes, addressBytes.length - CHECKSUM_LENGTH);
        byte[] checksum = Arrays.copyOfRange(addressBytes, addressBytes.length - CHECKSUM_LENGTH, addressBytes.length);

        byte[] checkSumGenerated = calcCheckSum(bytesWithVersion);

        if(!Arrays.equals(checksum, checkSumGenerated))
            throw new IllegalArgumentException("Wrong checksum");
        else
            return new PublicKey25519Proposition(Arrays.copyOfRange(bytesWithVersion, 1,bytesWithVersion.length));
    }

    public static int getLength() {
        return KEY_LENGTH;
    }

}

