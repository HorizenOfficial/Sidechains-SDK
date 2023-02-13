#!/usr/bin/env python3

import logging

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from httpCalls.block.best import http_block_best
from httpCalls.transaction.sendCoinsToAddress import sendCointsToMultipleAddress
from httpCalls.transaction.createCoreTransaction import http_create_core_transaction
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.transaction.findTransactionByID import http_transaction_findById
from httpCalls.wallet.allBoxesOfType import http_wallet_allBoxesOfType
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_equal
from SidechainTestFramework.scutil import assert_true, bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes

"""
Check forger txes sorting algorithm based on feerate.
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
Test:
    Try to create a big SC block and verify that we are able to send to other nodes even if it has size > 1MB
"""
class BigBlockTest(SidechainTestFramework):
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

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 500),
                                         sc_node_configuration,
                                         sc_node_configuration)
        self.options.restapitimeout = 20
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)


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
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        # Declare SC Addresses
        ft_address = http_wallet_createPrivateKey25519(sc_node1)
        ft_amount = 1000
        mc_return_address = mc_nodes[0].getnewaddress()

        # create 1 FT to node 1
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                ft_address, ft_amount, mc_return_address)
        self.sc_sync_all()

        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_node1, "first node", 1)[0]
        self.sc_sync_all()

        # Verify that we received the FT
        zen_boxes = http_wallet_allBoxesOfType(sc_node1, "ZenBox")
        assert_equal(len(zen_boxes), 1)
        assert_equal(zen_boxes[0]["value"], ft_amount * 1e8)

        # Get FT box id
        ft_box = self.find_boxes_of_address(zen_boxes, ft_address)
        assert_true(len(ft_box), 0)

        #Create 10000 UTXOs
        receiver_address = http_wallet_createPrivateKey25519(sc_node1)
        utxo_amount = ft_amount * 1e8 / 10000
        utxo_to_create = 10000
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

            self.sc_sync_all()
            generate_next_blocks(sc_node1, "first node", 1)[0]
            self.sc_sync_all()

            created_utxos += self.MAX_TRANSACTION_OUTPUT

            zen_boxes = http_wallet_allBoxesOfType(sc_node1, "ZenBox")
            ft_box = self.find_boxes_of_address(zen_boxes, ft_address)
            assert_true(len(ft_box), 0)

        remaining_utxos = utxo_to_create - created_utxos
        if remaining_utxos > 0:
            raw_tx = http_create_core_transaction(sc_node1, [{"boxId": ft_box[0]["id"]}],
                                                    [{"publicKey": receiver_address, "value": utxo_amount} for
                                                    _ in range(remaining_utxos)])["transactionBytes"]
            res = sendTransaction(sc_node1, raw_tx)["result"]
            assert_true("transactionId" in res)

            self.sc_sync_all()
            generate_next_blocks(sc_node1, "first node", 1)[0]
            self.sc_sync_all()

        zen_boxes = http_wallet_allBoxesOfType(sc_node1, "ZenBox")
        filtered_boxes = self.find_boxes_of_address(zen_boxes, receiver_address)
        assert_equal(len(filtered_boxes), utxo_to_create)

        address_node2 = http_wallet_createPrivateKey25519(sc_node2)
        transactions_bytes = 0

        for i in range(1000):
            res = sendCointsToMultipleAddress(sc_node1, [address_node2 for _ in range(10)], [utxo_amount for _ in range(10)], 0)
            logging.info("Created tx: "+res)
            tx = http_transaction_findById(sc_node1, res)
            tx_bytes = http_transaction_findById(sc_node1, res, False)
            transactions_bytes += len(tx_bytes["transactionBytes"])
            assert_equal(len(tx["transaction"]["newBoxes"]), 10)
            assert_equal(len(tx["transaction"]["unlockers"]), 10)
            self.sc_sync_all()

        logging.info("Total created transactions bytes "+str(transactions_bytes))
        #Verify that our transactions exceed 1MB of size
        assert_true(transactions_bytes > 1048576)
        generate_next_blocks(sc_node1, "first node", 1)[0]
        self.sc_sync_all()

        block_node1 = http_block_best(sc_node1)
        block_node2 = http_block_best(sc_node2)
        assert_equal(block_node1, block_node2)
        assert_equal(len(block_node1["sidechainTransactions"]), 1000)

if __name__ == "__main__":
    BigBlockTest().main()
