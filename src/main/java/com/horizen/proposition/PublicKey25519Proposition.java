package com.horizen.proposition;

import scala.util.Try;

import com.horizen.secret.PrivateKey25519;
import com.horizen.ScorexEncoding;

import scorex.crypto.hash.Blake2b256;
import scorex.crypto.signatures.Curve25519;
import com.google.common.primitives.Bytes;

import java.util.Arrays;

public class PublicKey25519Proposition<PK extends PrivateKey25519> extends ScorexEncoding implements ProofOfKnowledgeProposition<PK>
{
    public static final byte AddressVersion = 1;
    public static final int ChecksumLength = 4;
    public static final int PubKeyLength = 32;
    public static final int AddressLength = 1 + PubKeyLength + ChecksumLength;

    private byte[] _pubKeyBytes;


    public PublicKey25519Proposition(byte[] pubKeyBytes)
    {
        if(pubKeyBytes.length != Curve25519.KeyLength())
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", Curve25519.KeyLength(), pubKeyBytes.length));

        _pubKeyBytes = pubKeyBytes;
    }

    public byte[] pubKeyBytes() {
        return _pubKeyBytes;
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public PropositionSerializer serializer() {
        return new PublicKey25519PropositionSerializer();
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
        return Bytes.concat(new byte[] { AddressVersion }, pubKeyBytes());
    }

    public static byte[] calcCheckSum(byte[] bytes) {
        return Arrays.copyOf(Blake2b256.hash(bytes), ChecksumLength);
    }

    public String address() {
        return encoder().encode(Bytes.concat(pubKeyBytesWithVersion(), calcCheckSum(pubKeyBytesWithVersion())));
    }

    @Override
    public String toString() {
        return address();
    }

    public boolean verify(byte[] message, byte[] signature) {
        return Curve25519.verify(signature, message, pubKeyBytes());
    }

    // TO DO: should we return null if something going wrong or throw exception?
    public static PublicKey25519Proposition parseAddress(String address) {
        Try<byte[]> res = encoder().decode(address);
        if(res.isFailure())
            throw new IllegalArgumentException("Wrong address encoding");

        byte[] addressBytes = res.get();
        if(addressBytes.length != AddressLength)
            throw new IllegalArgumentException("Wrong address length");

        byte[] bytesWithVersion = Arrays.copyOf(addressBytes, addressBytes.length - ChecksumLength);
        byte[] checksum = Arrays.copyOfRange(addressBytes, addressBytes.length - ChecksumLength, addressBytes.length);

        byte[] checkSumGenerated = calcCheckSum(bytesWithVersion);

        if(!Arrays.equals(checksum, checkSumGenerated))
            throw new IllegalArgumentException("Wrong checksum");
        else
            return new PublicKey25519Proposition(Arrays.copyOfRange(bytesWithVersion, 1,bytesWithVersion.length));
    }
}

