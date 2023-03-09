#!/usr/bin/env python3

import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_2, DEFAULT_API_KEY
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import assert_true, bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, start_sc_node, wait_for_sc_node_initialization
from httpCalls.block.best import http_block_best
from httpCalls.transaction.createCoreTransaction import http_create_core_transaction
from httpCalls.transaction.sendCoinsToAddress import sendCointsToMultipleAddress
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.wallet.allBoxesOfType import http_wallet_allBoxesOfType
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_equal, fail

"""
Verify successful node termination during active sync
Configuration:
    Start 1 MC node and 2 SC node).
Test:
    Create 997 blocks (this number is not important)
    Connect sc node 2 to sc node 1
    When sync process started, stop sc node 2
    Verify node is terminated
    Restart sc node 2, reconnect it to sc node 2
    Verify that node is able to sync all the remaining blocks
"""


class NodeTerminationDuringSync(SidechainTestFramework):
    MAX_TRANSACTION_OUTPUT = 998
    number_of_sidechain_nodes = 2

    def setup_nodes(self):
        # Start 1 MC node
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 and SC node 2 connection to MC node 1
        mc_node_1 = self.nodes[0]

        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            True,
            automatic_fee_computation=False,
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node_1, 500, sc_creation_version=SC_CREATION_VERSION_2,
                           is_non_ceasing=True),
            sc_node_configuration,
            sc_node_configuration)
        self.options.restapitimeout = 20
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def find_boxes_of_address(self, boxes, address):
        address_boxes = []
        for box in boxes:
            if box["proposition"]["publicKey"] == address:
                address_boxes.append(box)
        return address_boxes

    def run_test(self):
        mc_nodes = self.nodes
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Declare SC Addresses
        ft_address = http_wallet_createPrivateKey25519(sc_node1)

        # create 1 FT to node 1
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                      ft_address, 1000, mc_nodes[0].getnewaddress())
        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_node1, "first node", 1)

        # Verify that we received the FT
        zen_boxes = http_wallet_allBoxesOfType(sc_node1, "ZenBox")
        ft_box = self.find_boxes_of_address(zen_boxes, ft_address)
        assert_equal(len(ft_box), 1)

        # Create 10000 UTXOs
        receiver_address = http_wallet_createPrivateKey25519(sc_node1)
        utxo_amount = 1000 * 1e8 / 10000
        utxo_to_create = 9980
        tx_to_create = int(utxo_to_create / self.MAX_TRANSACTION_OUTPUT)
        created_utxos = 0

        for _ in range(tx_to_create):
            outputs = [{"publicKey": receiver_address, "value": utxo_amount} for _ in
                       range(self.MAX_TRANSACTION_OUTPUT)]
            outputs.append(
                {"publicKey": ft_address, "value": ft_box[0]["value"] - utxo_amount * self.MAX_TRANSACTION_OUTPUT})

            raw_tx = http_create_core_transaction(sc_node1, [{"boxId": ft_box[0]["id"]}], outputs)["transactionBytes"]
            res = sendTransaction(sc_node1, raw_tx)["result"]
            assert_true("transactionId" in res)
            generate_next_blocks(sc_node1, "first node", 1)
            zen_boxes = http_wallet_allBoxesOfType(sc_node1, "ZenBox")
            ft_box = self.find_boxes_of_address(zen_boxes, ft_address)

            created_utxos += self.MAX_TRANSACTION_OUTPUT

        zen_boxes = http_wallet_allBoxesOfType(sc_node1, "ZenBox")
        filtered_boxes = self.find_boxes_of_address(zen_boxes, receiver_address)
        assert_equal(len(filtered_boxes), utxo_to_create)
        address_node2 = http_wallet_createPrivateKey25519(sc_node2)

        for i in range(997):
            res = sendCointsToMultipleAddress(sc_node1, [address_node2 for _ in range(10)],
                                              [utxo_amount for _ in range(10)], 0)
            logging.info(f"Created tx {i} out of 997: {res}")
            generate_next_blocks(sc_node1, "first node", 1)

        init_height = sc_node2.block_currentHeight()["result"]["height"]
        connect_sc_nodes(sc_node1, 1)
        while True:
            new_height = sc_node2.block_currentHeight()["result"]["height"]
            if new_height > init_height:
                sc_node2.node_stop()
                break

        start = time.time()
        while True:
            if time.time() - start >= 30:
                fail("Node is not stopped during 30 seconds")
            try:
                http_wallet_allBoxesOfType(sc_node2, "ZenBox")
            except:
                break
            time.sleep(1)
        time.sleep(30)  # some non-http actors might be still waiting for timeout
        sc_node2 = start_sc_node(1, self.options.tmpdir, auth_api_key=DEFAULT_API_KEY)
        self.sc_nodes[1] = sc_node2
        wait_for_sc_node_initialization(self.sc_nodes)
        connect_sc_nodes(sc_node1, 1)
        self.sc_sync_all()
        block_node1 = http_block_best(sc_node1)
        block_node2 = http_block_best(sc_node2)
        assert_equal(block_node1, block_node2)


if __name__ == "__main__":
    NodeTerminationDuringSync().main()
