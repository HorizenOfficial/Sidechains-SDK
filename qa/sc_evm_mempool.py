#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.utils import convertZenToZennies
from SidechainTestFramework.scutil import generate_next_block, \
    connect_sc_nodes, disconnect_sc_nodes_bi, sync_sc_blocks, assert_equal, \
    assert_true
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import forward_transfer_to_sidechain, fail

"""
Check Mem Pool is correctly updated after:
1. Blocks are applied to the blockchain
2. Some blocks are applied to the blockchain and others are reverted (fork).

Configuration:
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    - Add some transactions to the SC nodes. 
    - Disconnect the SC nodes and create 2 different sets of txs
    on the 2 nodes, so they have different txs in their mempool.
    - Forge a block on node 1.
    - Connect the SC nodes. Verify that node 2 mempool doesn't contain anymore the txs included in the block
    but still contains the txs created only on node 2.
    - Add some transactions to the SC nodes. 
    - Disconnect the SC nodes
    - On node 1 create some txs and then create a block
    - On node 2 create some txs and then create 2 blocks
    - Connect the SC nodes. Verify that the nodes are aligned and the block created on node 1 is reverted. 
    Verify that node 1 mem pool contains only the txs created on itself.
    
"""


class SCEvmMempool(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def run_test(self):
        # Synchronize mc_node1, mc_node2 and mc_node3, then disconnect them.
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('500.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        nonce_addr_1 = 0
        nonce_addr_2 = 0

        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        forgerStake_amount = 300  # Zen

        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_1, evm_address_sc1, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey,
                                                    convertZenToZennies(forgerStake_amount))
        nonce_addr_1 += 1
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        # Generate SC blocks on SC node to make node 2 a forger
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        common_tx_list = []
        for i in range(4):
            common_tx_list.append(
                createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                         nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            common_tx_list.append(
                createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc2, toAddress=evm_address_sc1,
                                         nonce=nonce_addr_2, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_1 += 1
            nonce_addr_2 += 1

        self.sc_sync_all()
        logging.info("Mempool node 1")
        response = allTransactions(sc_node_1, False)
        logging.info(response)
        assert_true(len(response['transactionIds']) == 8)
        logging.info("Mempool node 2")
        response = allTransactions(sc_node_2, False)
        logging.info(response)
        assert_true(len(response['transactionIds']) == 8)

        # Disconnect SC nodes
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)

        # Create txs on node 1
        for i in range(3):
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                     nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                     maxFeePerGas=900000000, value=1)
            nonce_addr_1 += 1

        # Create txs on node 2
        node_2_tx_list = []
        for i in range(5):
            node_2_tx_list.append(
                createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc2, toAddress=evm_address_sc1,
                                         nonce=nonce_addr_2, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_2 += 1

        logging.info("Mempool node 1 after disconnection")
        response = allTransactions(sc_node_1, False)
        logging.info(response)
        assert_true(len(response['transactionIds']) == 11)
        logging.info("Mempool node 2 after disconnection")
        response = allTransactions(sc_node_2, False)
        logging.info(response)
        assert_true(len(response['transactionIds']) == 13)

        # Create a block on node 1
        generate_next_block(sc_node_1, "first node")
        response = allTransactions(sc_node_1, False)
        assert_equal(0, len(response['transactionIds']), "Wrong number of transactions in node 1 mempool")

        # Connect SC nodes
        connect_sc_nodes(sc_node_1, 1)
        # Sync SC nodes
        sync_sc_blocks(self.sc_nodes)

        logging.info("Mempool node 1 after nodes reconnection")
        response = allTransactions(sc_node_1, False)
        logging.info(response)
        assert_true(len(response['transactionIds']) == 0)
        logging.info("Mempool node 2 after nodes reconnection")
        response = allTransactions(sc_node_2, False)
        logging.info(response)
        assert_true(len(response['transactionIds']) == 5)

        # Check that node 2 mem pool doesn't contain common txs anymore but still contains its own txs
        assert_equal(len(node_2_tx_list), len(response['transactionIds']),
                     "Wrong number of transactions in node 2 mempool")
        for i in range(len(node_2_tx_list)):
            assert_true(node_2_tx_list[i] in response['transactionIds'])

        # Create a block on node 2 to reset the mem pool
        generate_next_block(sc_node_2, "second node")
        self.sc_sync_all()

        # Create new common txs
        common_tx_list = []
        for i in range(4):
            common_tx_list.append(
                createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                         nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            common_tx_list.append(
                createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc2, toAddress=evm_address_sc1,
                                         nonce=nonce_addr_2, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_1 += 1
            nonce_addr_2 += 1
        self.sc_sync_all()

        # Disconnect SC nodes
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)

        # Create txs on node 1
        node_1_tx_list = []
        for i in range(3):
            node_1_tx_list.append(
                createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                         nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_1 += 1

        # Create a block on node 1
        generate_next_block(sc_node_1, "first node")

        # Check that node 1 mem pool is empty
        response = allTransactions(sc_node_1, False)
        assert_equal(0, len(response['transactionIds']), "Wrong number of transactions in node 1 mempool")

        # Create a block on node 2
        node_2_tx_list = []
        for i in range(3):
            node_2_tx_list.append(
                createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc2, toAddress=evm_address_sc2,
                                         nonce=nonce_addr_2, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_2 += 1
        generate_next_block(sc_node_2, "second node")

        # Create additional txs on node 2
        node_2_tx_list_2 = []
        for i in range(5):
            node_2_tx_list_2.append(
                createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc2, toAddress=evm_address_sc1,
                                         nonce=nonce_addr_2, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_2 += 1

        # Create another block on node 2
        generate_next_block(sc_node_2, "second node")

        # Connect SC nodes
        connect_sc_nodes(sc_node_1, 1)
        # Sync SC nodes
        sync_sc_blocks(self.sc_nodes)

        # Check both nodes have the same tip
        best_block_node_1 = sc_node_1.block_best()["result"]["block"]["header"]["id"]

        best_block_node_2 = sc_node_2.block_best()["result"]["block"]["header"]["id"]
        assert_equal(best_block_node_1, best_block_node_2)

        # Check that node 1 mem pool contains only its own txs
        response = allTransactions(sc_node_1, False)
        assert_equal(len(node_1_tx_list), len(response['transactionIds']),
                     "Wrong number of transactions in node 1 mempool")
        for i in range(len(node_1_tx_list)):
            assert_true(node_1_tx_list[i] in response['transactionIds'])


if __name__ == "__main__":
    SCEvmMempool().main()
