package com.horizen.transaction;

import com.horizen.box.ForgerBox;
import com.horizen.box.data.BoxData;
import com.horizen.box.data.ForgerBoxData;
import com.horizen.box.data.RegularBoxData;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ForgingStakeTransactionSerializationTest extends BoxFixtureClass {

    private ForgingStakeTransaction transaction;

    @Before
    public void BeforeEachTest() {
        long fee = 10;
        long timestamp = 1547798549470L;
        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        PrivateKey25519 pk1 = creator.generateSecret("fs_test_seed1".getBytes());
        PrivateKey25519 pk2 = creator.generateSecret("fs_test_seed2".getBytes());
        PrivateKey25519 pk3 = creator.generateSecret("fs_test_seed3".getBytes());

        ArrayList<Pair<ForgerBox, PrivateKey25519>> from = new ArrayList<>();
        from.add(new Pair<>(getForgerBox(pk1.publicImage(), 1, 60, pk1.publicImage(), getVRFPublicKey(1L)), pk1));
        from.add(new Pair<>(getForgerBox(pk2.publicImage(), 1, 50, pk2.publicImage(), getVRFPublicKey(2L)), pk2));
        from.add(new Pair<>(getForgerBox(pk3.publicImage(), 1, 20, pk1.publicImage(), getVRFPublicKey(3L)), pk3));

        PrivateKey25519 pk4 = creator.generateSecret("fs_test_seed4".getBytes());
        PrivateKey25519 pk5 = creator.generateSecret("fs_test_seed5".getBytes());
        PrivateKey25519 pk6 = creator.generateSecret("fs_test_seed6".getBytes());

        List<BoxData> to = new ArrayList<>();
        to.add(new RegularBoxData(pk4.publicImage(), 10L));
        to.add(new RegularBoxData(pk5.publicImage(), 20L));
        to.add(new ForgerBoxData(pk6.publicImage(), 90L, pk6.publicImage(), getVRFPublicKey(6L)));

        // Note: current transaction bytes are also stored in "src/test/resources/forgingstaketransaction_hex"
        transaction = ForgingStakeTransaction.create(from, to, fee, timestamp);
        // Uncomment and run if you want to update regression data.

        /*try {
            BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/forgingstaketransaction_hex"));
            out.write(BytesUtils.toHexString(transaction.bytes()));
             out.close();
        } catch (Throwable e) {
        }*/

    }

    @Test
    public void serializationTest() {
        TransactionSerializer serializer = transaction.serializer();
        byte[] bytes = serializer.toBytes(transaction);

        // Test 1: Correct bytes deserialization
        Try<ForgingStakeTransaction> t = serializer.parseBytesTry(bytes);
        assertTrue("Transaction serialization failed.", t.isSuccess());
        assertEquals("Deserialized transactions expected to be equal", transaction.id(), t.get().id());


        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }

    @Test
    public void regressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("forgingstaketransaction_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            fail(e.toString());
            return;
        }

        TransactionSerializer serializer = transaction.serializer();
        Try<ForgingStakeTransaction> t = serializer.parseBytesTry(bytes);
        assertTrue("Transaction serialization failed.", t.isSuccess());

        ForgingStakeTransaction parsedTransaction = t.get();
        assertEquals("Transaction is different to origin.", transaction.id(), parsedTransaction.id());
        assertEquals("Transaction is different to origin.", transaction.fee(), parsedTransaction.fee());
        assertEquals("Transaction is different to origin.", transaction.timestamp(), parsedTransaction.timestamp());
    }
}
