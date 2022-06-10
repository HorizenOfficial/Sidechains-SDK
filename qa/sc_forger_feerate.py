
#!/usr/bin/env python3
import json
import pprint
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_equal, assert_false, start_nodes, \
    websocket_port_by_mc_node_index, COIN
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, check_box_balance, check_wallet_coins_balance, generate_next_blocks, generate_next_block, \
    assert_true
from SidechainTestFramework.sc_forging_util import *

"""
Check forger txes sorting algorithm based on feerate.
Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.
Test:
    Perform several transactions with different size and fee and verify that they are ordered by FeeRate.
"""
class SCForgerFeerate(SidechainTestFramework):

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, forward_amount=100, withdrawal_epoch_length=self.sc_withdrawal_epoch_length), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        #return start_sc_nodes(1, self.options.tmpdir, extra_args=['-agentlib'])
        return start_sc_nodes(1, self.options.tmpdir)


    def send_coins(self, sc_node, receiver, amount, fee, numberOfOutputs=1):
        assert_true(numberOfOutputs > 0, "Invalid number of outputs {}".format(numberOfOutputs))
        am = amount/numberOfOutputs
        outputs = []
        for i in range(0, numberOfOutputs):
            if (i == numberOfOutputs-1):
                am = amount - i*am
            outputs.append({"publicKey": receiver, "value": am})

        j = {"outputs": outputs, "fee": fee}

        request = json.dumps(j)
        ret = sc_node.transaction_sendCoinsToAddress(request)
        pprint.pprint(ret)
        try:
            txid = ret["result"]["transactionId"]
            return txid
        except Exception as e:
            print("Exception: " + e.error)


    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        print(scblock_id)

        # create FT to SC for creating some utxo to spend
        sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        mc_return_address = mc_node.getnewaddress()

        ft_args = [{
            "toaddress": sc_address,
            "amount": 10,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }, {
            "toaddress": sc_address,
            "amount": 20,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }, {
            "toaddress": sc_address,
            "amount": 30,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        },
         {
            "toaddress": sc_address,
            "amount": 40,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        },
         {
            "toaddress": sc_address,
            "amount": 50,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }]

        mc_node.sc_send(ft_args)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block
        mc_node.generate(1)
        generate_next_blocks(sc_node, "first node", 1)
        check_box_balance(sc_node, Account("", sc_address), "ZenBox", 5, 150)

        sc_send_amount = 1  # Zen
        print("\nSending {} satoshi ({} Zen) inside sidechain...".format(sc_send_amount * COIN, sc_send_amount))
        sc_address = sc_node.wallet_allPublicKeys()["result"]["propositions"][-1]["publicKey"]

        tx1 = self.send_coins(sc_node, sc_address, amount=111111, fee=100, numberOfOutputs=5)

        tx2 = self.send_coins(sc_node, sc_address, amount=222222, fee=300, numberOfOutputs=5)

        tx3 = self.send_coins(sc_node, sc_address, amount=333333, fee=100, numberOfOutputs=1)

        tx4 = self.send_coins(sc_node, sc_address, amount=333333, fee=101, numberOfOutputs=1)

        tx4 = self.send_coins(sc_node, sc_address, amount=333333, fee=99, numberOfOutputs=1)

        print("Generating SC Block with send coins transaction...")
        scblock_id2 = generate_next_blocks(sc_node, "first node", 1)[0]
        res = sc_node.block_findById(blockId=scblock_id2)
        txList = res['result']['block']['sidechainTransactions']


        minFeeRate = 100000000
        for tx in txList:
            feeRate = tx['fee'] / tx['size']
            print("tx feeRate = {}".format(feeRate))
            assert_true(feeRate < minFeeRate)
            minFeeRate = feeRate



if __name__ == "__main__":
    SCForgerFeerate().main()