package io.horizen.account.proof;

import io.horizen.account.utils.Secp256k1;
import io.horizen.proof.ProofSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        // variable length, as per EIP155
        writer.putInt(signature.getV().length);
        writer.putBytes(signature.getV());
        writer.putBytes(signature.getR());
        writer.putBytes(signature.getS());
    }

    @Override
    public SignatureSecp256k1 parse(Reader reader) {
        var vl = reader.getInt();
        var v = reader.getBytes(vl);
        var r = reader.getBytes(Secp256k1.SIGNATURE_RS_SIZE);
        var s = reader.getBytes(Secp256k1.SIGNATURE_RS_SIZE);
        return new SignatureSecp256k1(v, r, s);
    }
}
