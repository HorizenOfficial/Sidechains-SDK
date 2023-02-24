#!/usr/bin/env python3
import time
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.scutil import generate_next_block, \
    assert_equal, \
    assert_true
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import forward_transfer_to_sidechain, assert_false

"""
Check mem pool behaviour when it reached its maximum size.

Configuration:
    - 2 SC nodes
    - 1 MC node

The second SC node is used to check that locally and remotely generated transactions/blocks are treated in the same way.
"""


class SCEvmMempoolSize(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, max_nonce_gap=100, max_mempool_slots=16)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen_1 = Decimal('3000.0')
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen_1,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        ft_amount_in_zen_2 = Decimal('10.0')
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc2,
                                      ft_amount_in_zen_2,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        """
        Test that adding a transaction when the mem pool is already full will evict the oldest tx
        First a non exec tx (oldest_tx) will be created. This will become the oldest tx that will be evicted.
        After having generated some blocks (that will be reverted later), the mem pool is filled with txs. 
        Verify that when an exceeding tx is added to the mempool, oldest_tx is evicted.
        """
        oldest_tx = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc2, toAddress=evm_address_sc2,
                                             nonce=14, gasLimit=23000, maxPriorityFeePerGas=900000000,
                                             maxFeePerGas=900000000, value=1)

        initial_block_height = sc_node_1.block_best()["result"]["height"]

        max_num_of_blocks = 3
        nonce = 0
        mc_block_hash = []
        reinjected_txs = []
        for j in range(max_num_of_blocks):
            for i in range(j * self.max_account_slots, (j + 1) * (self.max_account_slots - 1)):
                reinjected_txs.append(createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1,
                                                               toAddress=evm_address_sc1, nonce=nonce,
                                                               gasLimit=230000, maxPriorityFeePerGas=900000000,
                                                               maxFeePerGas=900000000, value=1))
                nonce += 1
            mc_block_hash.append(mc_node.generate(1)[0])
            generate_next_block(sc_node_1, "first node")

        # Check that oldest_tx ist still in mem pool on both nodes
        response = allTransactions(sc_node_1, False)
        assert_equal(1, len(response['transactionIds']), "Wrong number of txs in mem pool of node 1")
        assert_true(oldest_tx in response['transactionIds'], "oldest_tx is missing from mem pool of node 1")

        self.sc_sync_all()

        response = allTransactions(sc_node_2, False)
        assert_equal(1, len(response['transactionIds']), "Wrong number of txs in mem pool of node 2")
        assert_true(oldest_tx in response['transactionIds'], "oldest_tx is missing from mem pool of node 2")

        # Create non exec txs, so they won't be included in next block. They will be used later in the test
        nonce += 1
        for i in range(self.max_mempool_slots - 1):
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1, nonce=nonce,
                                     gasLimit=230000, maxPriorityFeePerGas=900000000,
                                     maxFeePerGas=900000000, value=1)
            nonce += 1

        # Check that the mem pool is full
        response = allTransactions(sc_node_1, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']),
                     "Wrong number of txs in mempool of node 1")
        # Check that oldest_tx ist still in mem pool
        assert_true(oldest_tx in response['transactionIds'], "oldest_tx should still be in mem pool of node 1")

        self.sc_sync_all()

        response = allTransactions(sc_node_2, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']),
                     "Wrong number of txs in mempool of node 2")
        # Check that oldest_tx ist still in mem pool
        assert_true(oldest_tx in response['transactionIds'], "oldest_tx should still be in mem pool of node 2")

        # Create an additional tx and verify that oldest_tx is evicted
        createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1, nonce=nonce,
                                 gasLimit=230000, maxPriorityFeePerGas=900000000,
                                 maxFeePerGas=900000000, value=1)

        response = allTransactions(sc_node_1, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']),
                     "Wrong number of txs in mempool of node 1")
        assert_false(oldest_tx in response['transactionIds'], "oldest_tx is still in mem pool of node 1")
        txs_in_mempool = response['transactionIds']

        self.sc_sync_all()
        response = allTransactions(sc_node_2, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']),
                     "Wrong number of txs in mempool of node 2")
        assert_false(oldest_tx in response['transactionIds'], "oldest_tx is still in mem pool of node 2")

        # Now the mainchain will revert to the block created with the second FT. All the txs in the sidechain blocks
        # created after that will be readded to the mem pool. Verify that only max_mempool_slots txs will be in the mem
        # pool and that oldestTxs are evicted

        #  for mc_block_id in mc_block_hash:
        mc_node.invalidateblock(mc_block_hash[0])
        time.sleep(5)
        mc_node.generate(max_num_of_blocks + 1)

        # Generate a new sc block, in order to see the mc fork
        generate_next_block(sc_node_1, "first node")
        new_block_height = sc_node_1.block_best()["result"]["height"]
        assert_equal(initial_block_height + 1, new_block_height, "Wrong block height after revert")

        response = allTransactions(sc_node_1, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']),
                     "Wrong number of txs in mempool of node 1")

        # Check that all txs_in_mempool txs were evicted
        for tx in txs_in_mempool:
            assert_false(tx in response['transactionIds'], "txs_in_mempool is still in mem pool of node 1")

        # Check that in the mempool were just kept the reinjected txs with the lowest nonce

        for i in range(self.max_mempool_slots):
            assert_true(reinjected_txs[i] in response['transactionIds'], "tx is not in mem pool of node 1")

        for i in range(self.max_mempool_slots, len(reinjected_txs)):
            assert_false(reinjected_txs[i] in response['transactionIds'],
                         "tx should have been evicted from mem pool of node 1")

        # Perform the same checks on node 2
        self.sc_sync_all()

        response = allTransactions(sc_node_2, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']),
                     "Wrong number of txs in mempool of node 2")

        # Check that all txs_in_mempool txs were evicted
        for tx in txs_in_mempool:
            assert_false(tx in response['transactionIds'], "txs_in_mempool is still in mem pool of node 2")

        # Check that in the mempool were just kept the reinjected txs with the lowest nonce

        for i in range(self.max_mempool_slots):
            assert_true(reinjected_txs[i] in response['transactionIds'], "tx is not in mem pool of node 2")

        for i in range(self.max_mempool_slots, len(reinjected_txs)):
            assert_false(reinjected_txs[i] in response['transactionIds'],
                         "tx should have been evicted from mem pool of node 2")


if __name__ == "__main__":
    SCEvmMempoolSize().main()
