package io.horizen.account.proof;

import io.horizen.proof.ProofSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

import java.math.BigInteger;

import static io.horizen.account.utils.BigIntegerUInt256.getUnsignedByteArray;

public final class SignatureSecp256k1Serializer implements ProofSerializer<SignatureSecp256k1> {

    private static final SignatureSecp256k1Serializer serializer;

    static {
        serializer = new SignatureSecp256k1Serializer();
    }

    private SignatureSecp256k1Serializer() {
        super();
    }

    public static SignatureSecp256k1Serializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SignatureSecp256k1 signature, Writer writer) {
        var byteArray_v = getUnsignedByteArray(signature.getV());
        writer.putUShort(byteArray_v.length);
        writer.putBytes(byteArray_v);

        var byteArray_r = getUnsignedByteArray(signature.getR());
        writer.putUShort(byteArray_r.length);
        writer.putBytes(byteArray_r);

        var byteArray_s = getUnsignedByteArray(signature.getS());
        writer.putUShort(byteArray_s.length);
        writer.putBytes(byteArray_s);
    }

    @Override
    public SignatureSecp256k1 parse(Reader reader) {

        int byteArray_v_len = reader.getUShort();
        var v = new BigInteger(1, reader.getBytes(byteArray_v_len));

        int byteArray_r_len = reader.getUShort();
        var r = new BigInteger(1, reader.getBytes(byteArray_r_len));

        int byteArray_s_len = reader.getUShort();
        var s = new BigInteger(1, reader.getBytes(byteArray_s_len));

        return new SignatureSecp256k1(v, r, s);
    }
}
