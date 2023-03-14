#!/usr/bin/env python3
from curses import raw
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_false
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.transaction.withdrawCoins import withdrawMultiCoins
from httpCalls.block.forgingInfo import http_block_forging_info

"""
Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.
    SC node has ENABLED certificate submitter.
    WithdrawalEpochLength = 11
    WithdrawalRequestBox slots open per MC block reference = 3999 / (11 - 1) = 399

Note:
    This test can be executed in two modes:
    1. using no key rotation circuit (by default)
    2. using key rotation circuit (with --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation)
    With key rotation circuit can be executed in two modes:
    1. ceasing (by default)
    2. non-ceasing (with --nonceasing flag)
    
Test:
    For the SC node:
        - ############## WITHDRAWAL EPOCH 0 #####################
        - Send 1 FT to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FT in the sidechain.
        - Generate a transaction that has 999 WithdrawalRequestBoxes
        - Generate a SC block that reach the SC Fork 1 and verify that it doesn't includes this transaction since we didn't open enough slots yet.
        - Mine a new MC block in order to open up more slots
        - Generate a SC block and verify that now it includes the transaction.
"""


class ScBtLimitAcrossForkTest(SidechainTestFramework):
    sidechain_id = None
    sc_withdrawal_epoch_length = 11
    FEE = 5

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=True,  # enable submitter
            cert_signing_enabled=True  # enable signer
        )

        if self.options.certcircuittype == KEY_ROTATION_CIRCUIT:
            sc_creation_version = SC_CREATION_VERSION_2  # non-ceasing could be only SC_CREATION_VERSION_2>=2
        else:
            sc_creation_version = SC_CREATION_VERSION_1

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 1000, self.sc_withdrawal_epoch_length,
                                                        sc_creation_version=sc_creation_version,
                                                        is_non_ceasing=self.options.nonceasing,
                                                        circuit_type=self.options.certcircuittype),
                                         sc_node_configuration)
        self.sidechain_id = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5).sidechain_id

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # ******************** WITHDRAWAL EPOCH 0 START ********************
        print("******************** WITHDRAWAL EPOCH 0 START ********************")

        # Verify we didn't reach the SC fork1 that includes BT limit
        consensusEpochData = http_block_forging_info(sc_node)
        assert_equal(consensusEpochData["bestEpochNumber"], 1)

        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1

        # create 1 FTs in the same MC block to SC
        sc_address_1 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_1 = 100
        mc_return_address_1 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_1, ft_amount_1, mc_return_address_1)

        # Sleep for 1 second to let MC synchronize wallet
        time.sleep(1)

        epoch_mc_blocks_left -= 1
        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Create a transaction that generates 999 WBs 
        bt_address = mc_node.getnewaddress()
        bt_addresses = [bt_address for i in range(999)]
        amounts = [54 for i in range(999)]
        withdrawMultiCoins(sc_node, bt_addresses, amounts)
        consensusEpochData = http_block_forging_info(sc_node)
        assert_equal(consensusEpochData["bestEpochNumber"], 2)

        # Reach the SC fork 1
        sc_block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        consensusEpochData = http_block_forging_info(sc_node)
        assert_equal(consensusEpochData["bestEpochNumber"], 3)
        block_json = http_block_findById(sc_node, sc_block_id)

        # Verify that we didn't include the transaction since we still don't have enough open WB slots (2 MC block 799 slots)
        assert_equal(len(block_json["block"]["sidechainTransactions"]), 0)

        # Mine a new MC block
        mc_node.generate(1)

        # Forge a new SC block and verify that now it includes the transaction with the WBs.
        sc_block_id = generate_next_block(sc_node, "first node")
        block_json = http_block_findById(sc_node, sc_block_id)
        assert_equal(len(block_json["block"]["sidechainTransactions"]), 1)


if __name__ == "__main__":
    ScBtLimitAcrossForkTest().main()
