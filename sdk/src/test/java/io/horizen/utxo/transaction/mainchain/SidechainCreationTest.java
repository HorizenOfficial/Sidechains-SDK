package io.horizen.utxo.transaction.mainchain;

import io.horizen.block.MainchainTransaction;
import io.horizen.block.MainchainTxSidechainCreationCrosschainOutput;
import io.horizen.consensus.ForgingStakeInfo;
import io.horizen.proposition.VrfPublicKey;
import io.horizen.transaction.mainchain.SidechainCreation;
import io.horizen.utils.BytesUtils;
import io.horizen.utxo.box.ForgerBox;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class SidechainCreationTest {


    @Test
    public void getBoxTest() throws IOException {
        byte[] bytes;
        ClassLoader classLoader = getClass().getClassLoader();
        FileReader file = new FileReader(classLoader.getResource("mctx_v-4_sc_creation").getFile());
        bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        MainchainTransaction mcTx = MainchainTransaction.create(bytes, 0).get();
        MainchainTxSidechainCreationCrosschainOutput scTx = (MainchainTxSidechainCreationCrosschainOutput) mcTx.getCrosschainOutputs().head();
        SidechainCreation sc = new SidechainCreation(scTx, mcTx.hash(), 0);

        ForgerBox box = sc.getBox();
        assertArrayEquals("Forger address and blockSignProposition are different", BytesUtils.reverseBytes(scTx.address()), box.blockSignProposition().bytes());
        assertArrayEquals("Custom creation data should only contain vrfPubKey", scTx.customCreationData(), box.vrfPubKey().pubKeyBytes());
        assertEquals(scTx.amount(), box.value());
    }


//    @Test
//    public void getBoxWithAccountSidechainCreationDataTest() throws IOException {
//        byte[] bytes;
//        ClassLoader classLoader = getClass().getClassLoader();
//        FileReader file = new FileReader(classLoader.getResource("mctx_v-4_account_sc_creation").getFile());
//        bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
//        MainchainTransaction mcTx = MainchainTransaction.create(bytes, 0).get();
//        MainchainTxSidechainCreationCrosschainOutput scTx = (MainchainTxSidechainCreationCrosschainOutput) mcTx.getCrosschainOutputs().head();
//        SidechainCreation sc = new SidechainCreation(scTx, mcTx.hash(), 0);
//
//        assertThrows(Exception.class,() ->sc.getBox());
//    }


    @Test
    public void getAccountForgerStakeInfoTest() throws IOException {
        byte[] bytes;
        ClassLoader classLoader = getClass().getClassLoader();
        FileReader file = new FileReader(classLoader.getResource("mctx_v-4_account_sc_creation").getFile());
        bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        MainchainTransaction mcTx = MainchainTransaction.create(bytes, 0).get();
        MainchainTxSidechainCreationCrosschainOutput scTx = (MainchainTxSidechainCreationCrosschainOutput) mcTx.getCrosschainOutputs().head();
        SidechainCreation sc = new SidechainCreation(scTx, mcTx.hash(), 0);

        ForgingStakeInfo stakeInfo = sc.getAccountForgerStakeInfo();
        byte[] vrfPubKey = Arrays.copyOfRange(scTx.customCreationData(), 0, VrfPublicKey.KEY_LENGTH);
        assertArrayEquals("Wrong vrfPubKey", vrfPubKey, stakeInfo.vrfPublicKey().pubKeyBytes());

        byte[] blockSign = Arrays.copyOfRange(scTx.customCreationData(), VrfPublicKey.KEY_LENGTH, scTx.customCreationData().length);
        assertArrayEquals("Wrong blockSignProposition", blockSign, stakeInfo.blockSignPublicKey().pubKeyBytes());
        assertEquals(scTx.amount(), stakeInfo.stakeAmount());
    }


    @Test
    public void getAccountForgerStakeInfoWithUTXOSidechainCreationDataTest() throws IOException {
        byte[] bytes;
        ClassLoader classLoader = getClass().getClassLoader();
        FileReader file = new FileReader(classLoader.getResource("mctx_v-4_sc_creation").getFile());
        bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        MainchainTransaction mcTx = MainchainTransaction.create(bytes, 0).get();
        MainchainTxSidechainCreationCrosschainOutput scTx = (MainchainTxSidechainCreationCrosschainOutput) mcTx.getCrosschainOutputs().head();
        SidechainCreation sc = new SidechainCreation(scTx, mcTx.hash(), 0);
        assertThrows(IllegalArgumentException.class,() ->sc.getAccountForgerStakeInfo());
    }

}