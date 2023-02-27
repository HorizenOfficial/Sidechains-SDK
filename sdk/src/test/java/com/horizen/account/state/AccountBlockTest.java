package com.horizen.account.state;

import com.horizen.account.block.AccountBlock;
import com.horizen.account.state.receipt.EthereumConsensusDataReceipt;
import com.horizen.account.receipt.ReceiptFixture;
import io.horizen.evm.Address;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;

public class AccountBlockTest implements ReceiptFixture {
    public Seq<EthereumConsensusDataReceipt> convertListToSeq(List<EthereumConsensusDataReceipt> inputList) {
        return JavaConverters.asScalaIteratorConverter(inputList.iterator()).asScala().toSeq();
    }

    @Test
    public void receiptRoot() {
        // (txHash + address) for test receipt
        var txData = new String[][] {
            {
                "ce5c2ec59ee419da7716fc5b4cacfeac721b74c34422e64b6c89d326bea3eb70",
                "0x35348c6dabdd7a6ce31d80a277fb6374419ca07e"
            }, {
                "28ef52bffa96651fc6edcd9805d34db72816650cc1e5ae843c0dbf75efc7cd86",
                "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599"
            }, {
                "aace05db44ac4c317504c22f0c8535dcc88f54fce1064aab3be7b337414f0b43",
                "0xc5b24870bef4db2ca306d3627dfc4a9e5dce78bf"
            }, {
                "8d693c2dafe2a66f60b47985c510111c9dc69ee4abe41ad31e3308765e929826",
                "0x06012c8cf97BEaD5deAe237070F9587f8E7A266d"
            }
        };
        var receiptList = Arrays.stream(txData).map((d) -> {

            var txType = 2;
            var numLogs = 3;
            var contractAddressPresence = false;

            return createTestEthereumReceipt(txType, numLogs, contractAddressPresence, Option.apply(BytesUtils.fromHexString(d[0])), new Address(d[1]), "blockhash", 22, 33).consensusDataReceipt();
        }).collect(Collectors.toList());

        var precalculatedRoot = BytesUtils.fromHexString("895fd6302535b35fc2f4c903c4ea919e367587b72c98ee8a976af069d1794ce9");
        var root = AccountBlock.calculateReceiptRoot(convertListToSeq(receiptList));

        assertArrayEquals(precalculatedRoot, root);
    }
}
