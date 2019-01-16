package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.RegularBox;
import com.horizen.box.RegularBoxSerializer;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.proposition.Signature25519;
import com.horizen.proposition.Signature25519Serializer;
import javafx.util.Pair;
import scala.util.Success;
import scala.util.Failure;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

class RegularTransactionSerializer implements TransactionSerializer<RegularTransaction>
{

    private static RegularTransactionSerializer serializer;

    static {
        serializer = new RegularTransactionSerializer();
    }

    private RegularTransactionSerializer() {
        super();
    }

    public static RegularTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public byte[] toBytes(RegularTransaction transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<RegularTransaction> parseBytes(byte[] bytes) {
        return RegularTransaction.parseBytes(bytes);
    }
}

