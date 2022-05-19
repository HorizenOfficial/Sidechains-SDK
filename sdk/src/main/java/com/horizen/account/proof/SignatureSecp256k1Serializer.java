package com.horizen.account.proof;

import com.horizen.account.utils.Secp256k1;
import com.horizen.proof.ProofSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

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
        writer.putBytes(signature.getV());
        writer.putBytes(signature.getR());
        writer.putBytes(signature.getS());
    }

    @Override
    public SignatureSecp256k1 parse(Reader reader) {
        var v = reader.getBytes(Secp256k1.SIGNATURE_V_SIZE);
        var r = reader.getBytes(Secp256k1.SIGNATURE_RS_SIZE);
        var s = reader.getBytes(Secp256k1.SIGNATURE_RS_SIZE);
        return new SignatureSecp256k1(v, r, s);
    }
}
