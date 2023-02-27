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
Tests:
    - Create 3 MC blocks and 3 corresponding SC blocks, containing some transactions
    - Add as many transactions as needed to reach the maximum mempool size. Add an additional tx and verify that the oldest
    tx in the mempool is evicted. This eviction in turn will transform some txs from executable to non executable so
    the non exec sub pool size will be exceeded too. A second tx will then be evicted.
    - Revert the 3 MC blocks. This will revert the SC blocks and the contained transactions will be reinserted in the mempool.
    The sum of the txs already in the mempool and of the reinserted txs will exceeds the maximum size of the mempool and 
    the oldest txs will be evicted, including some of the reinserted txs. Check that the reinserted txs are evicted starting
    from the biggest nonce.

The second SC node is used to check that locally and remotely generated transactions/blocks are treated in the same way.
"""


class SCEvmMempoolSize(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, max_nonce_gap=100, max_mempool_slots=16, max_nonexec_pool_slots=15)

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
        Test that adding a transaction when the mem pool is already full will evict the oldest tx.
        First an exec tx (oldest_tx) will be created. This will become the oldest tx that will be evicted.
        After having generated some blocks (that will be reverted later), the mem pool is filled with txs. 
        Verify that when an exceeding tx is added to the mempool, oldest_tx is evicted.
        """
        oldest_tx = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc2, toAddress=evm_address_sc2,
                                             nonce=14, gasLimit=23000, maxPriorityFeePerGas=900000000,
                                             maxFeePerGas=900000000, value=1)

        sc_block_height_before_fork = sc_node_1.block_best()["result"]["height"]

        max_num_of_blocks = 3
        nonce = 0
        list_of_mc_block_hash_to_be_reverted = []
        reinjected_txs = []
        for j in range(max_num_of_blocks):
            for i in range(j * self.max_account_slots, (j + 1) * (self.max_account_slots - 1)):
                reinjected_txs.append(createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1,
                                                               toAddress=evm_address_sc1, nonce=nonce,
                                                               gasLimit=230000, maxPriorityFeePerGas=900000000,
                                                               maxFeePerGas=900000000, value=1))
                nonce += 1
            list_of_mc_block_hash_to_be_reverted.append(mc_node.generate(1)[0])
            generate_next_block(sc_node_1, "first node")

        # Check that oldest_tx ist still in mem pool on both nodes
        response = allTransactions(sc_node_1, False)
        assert_equal(1, len(response['transactionIds']), "Wrong number of txs in mem pool of node 1")
        assert_true(oldest_tx in response['transactionIds'], "oldest_tx is missing from mem pool of node 1")

        self.sc_sync_all()

        response = allTransactions(sc_node_2, False)
        assert_equal(1, len(response['transactionIds']), "Wrong number of txs in mem pool of node 2")
        assert_true(oldest_tx in response['transactionIds'], "oldest_tx is missing from mem pool of node 2")


        # Create exec txs to fill the mempool. They won't be included in next block because the next block
        # will be the one with the fork and this kind of blocks doesn't contain txs.
        # The txs will be used later in the test.

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


        #Now an additional non exec tx will be added to the mempool, so the size of the mempool exceeds the allowed max size
        # by one slot (that corresponds to 1 tx in this test). This will evict the current oldest tx in the mempool,
        # that is an exec tx. This will cause in turn the other exec txs to become non exec, so in the mempool there will be
        # only non exec txs. Their number will exceed the max non exec size. A second tx will be then evicted.
        nonExecTx = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc2, toAddress=evm_address_sc2,
                                            nonce=15, gasLimit=23000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=900000000, value=1)

        response = allTransactions(sc_node_1, False)
        assert_equal(self.max_nonexec_pool_slots, len(response['transactionIds']), "Wrong number of txs in mempool")
        assert_true(nonExecTx in response['transactionIds'], "new tx is not in mem pool")
        txs_in_mempool_before_fork = response['transactionIds']

        # Now the mainchain will revert to the block created with the second FT. All the txs in the sidechain blocks
        # created after that will be readded to the mem pool. The total number of reinserted txs is bigger than the max
        # mem pool size so all the txs that were already in the mempool are evicted and then some reinserted too.
        # Verify that only max_mempool_slots txs will be in the mem pool in the end. Verify that the oldest txs are evicted,
        # that are the txs that were in the mempool, then that some reinserted txs are evicted too, starting from the ones
        # with higher nonces.

        # Create a fork on MC: invalidate the old MC blocks and create new ones
        mc_node.invalidateblock(list_of_mc_block_hash_to_be_reverted[0])
        time.sleep(5)
        mc_node.generate(max_num_of_blocks + 1)

        # Generate a new sc block, in order to see the MC fork
        generate_next_block(sc_node_1, "first node")
        # Verify that the SC has rejected the blocks related to the invalid MC blocks
        new_block_height = sc_node_1.block_best()["result"]["height"]
        assert_equal(sc_block_height_before_fork + 1, new_block_height, "Wrong block height after revert")

        response = allTransactions(sc_node_1, False)
        txs_in_mempool_after_fork = response['transactionIds']
        assert_equal(self.max_mempool_slots, len(txs_in_mempool_after_fork),
                     "Wrong number of txs in mempool of node 1")

        # Check that all txs_in_mempool_before_fork txs were evicted
        for tx in txs_in_mempool_before_fork:
            assert_false(tx in txs_in_mempool_after_fork, "txs_in_mempool_before_fork is still in mem pool of node 1"")

        # Check that in the mempool were just kept the reinjected txs with the lowest nonce

        for i in range(self.max_mempool_slots):
            assert_true(reinjected_txs[i] in txs_in_mempool_after_fork, "tx is not in mem pool of node 1")

        for i in range(self.max_mempool_slots, len(reinjected_txs)):
            assert_false(reinjected_txs[i] in txs_in_mempool_after_fork,
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
