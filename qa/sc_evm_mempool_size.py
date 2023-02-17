#!/usr/bin/env python3
import logging
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
    - 1 SC node
    - 1 MC node

"""


class SCEvmMempoolSize(AccountChainSetup):
    def __init__(self):
        super().__init__(max_nonce_gap=100, max_mempool_slots=16, max_nonexec_pool_slots=15)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

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

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        """
        Test that adding a transaction when the mem pool is already full will evict the oldest tx.
        First an exec tx (oldestTx) will be created. This will become the oldest tx that will be evicted.
        After having generated some blocks (that will be reverted later), the mem pool is filled with txs. 
        Verify that when an exceeding tx is added to the mempool, oldestTx is evicted.
        """
        oldestTx = createEIP1559Transaction(sc_node, fromAddress=evm_address_sc2, toAddress=evm_address_sc2,
                                            nonce=14, gasLimit=23000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=900000000, value=1)

        initial_block_height = sc_node.block_best()["result"]["height"]

        max_num_of_blocks = 3
        nonce = 0
        mc_block_hash = []
        reinjected_txs = []
        for j in range(max_num_of_blocks):
            for i in range(j * self.max_account_slots, (j + 1) * (self.max_account_slots - 1)):
                reinjected_txs.append(createEIP1559Transaction(sc_node, fromAddress=evm_address_sc1,
                                                               toAddress=evm_address_sc1, nonce=nonce,
                                                               gasLimit=230000, maxPriorityFeePerGas=900000000,
                                                               maxFeePerGas=900000000, value=1))
                nonce += 1
            mc_block_hash.append(mc_node.generate(1)[0])
            generate_next_block(sc_node, "first node")

        # Check that oldestTx ist still in mem pool
        response = allTransactions(sc_node, False)
        assert_equal(1, len(response['transactionIds']), "Wrong number of txs in mem pool")
        assert_true(oldestTx in response['transactionIds'], "oldestTx is missing from mem pool")

        # Create exec txs to fill the mempool. They won't be included in next block because the next block
        # will be the one with the fork and this kind of blocks doesn't contain txs.
        # The tx will be used later in the test
      #  nonce += 1
        for i in range(self.max_mempool_slots - 1):
            createEIP1559Transaction(sc_node, fromAddress=evm_address_sc1, toAddress=evm_address_sc1, nonce=nonce,
                                     gasLimit=230000, maxPriorityFeePerGas=900000000,
                                     maxFeePerGas=900000000, value=1)
            nonce += 1

        # Check that the mem pool is full
        response = allTransactions(sc_node, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']), "Wrong number of txs in mempool")
        # Check that oldestTx ist still in mem pool
        assert_true(oldestTx in response['transactionIds'], "oldestTx should still be in mem pool")

        # Create an additional tx and verify that oldestTx is evicted
        createEIP1559Transaction(sc_node, fromAddress=evm_address_sc1, toAddress=evm_address_sc1, nonce=nonce,
                                 gasLimit=230000, maxPriorityFeePerGas=900000000,
                                 maxFeePerGas=900000000, value=1)

        response = allTransactions(sc_node, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']), "Wrong number of txs in mempool")
        assert_false(oldestTx in response['transactionIds'], "oldestTx is still in mem pool")
        txsInMempool = response['transactionIds']

        #Now an additional non exec tx will be added to the mempool. This will evict the current oldest tx in the mempool,
        # that is an exec tx. This will cause the other txs to become non exec and this will exceeds the max non exec size.
        # A second tx will be evicted.
        nonExecTx = createEIP1559Transaction(sc_node, fromAddress=evm_address_sc2, toAddress=evm_address_sc2,
                                            nonce=15, gasLimit=23000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=900000000, value=1)

        response = allTransactions(sc_node, False)
        assert_equal(self.max_nonexec_pool_slots, len(response['transactionIds']), "Wrong number of txs in mempool")
        assert_true(nonExecTx in response['transactionIds'], "oldestTx is still in mem pool")
        txsInMempool = response['transactionIds']


        # Now the mainchain will revert to the block created with the second FT. All the txs in the sidechain blocks
        # created after that will be readded to the mem pool. Verify that only max_mempool_slots txs will be in the mem pool
        # and that oldestTxs are evicted

        mc_node.invalidateblock(mc_block_hash[0])
        time.sleep(5)
        rs = mc_node.generate(max_num_of_blocks + 1)

        # Generate a new sc block, in order to see the mc fork
        generate_next_block(sc_node, "first node")
        new_block_height = sc_node.block_best()["result"]["height"]
        assert_equal(initial_block_height + 1, new_block_height, "Wrong block height after revert")

        response = allTransactions(sc_node, False)
        assert_equal(self.max_mempool_slots, len(response['transactionIds']), "Wrong number of txs in mempool")

        # Check that all txsInMempool txs were evicted
        for tx in txsInMempool:
            assert_false(tx in response['transactionIds'], "txsInMempool is still in mem pool")

       # Check that in the mempool were just kept the reinjected txs with lowest nonce

        for i in range(self.max_mempool_slots):
            assert_true(reinjected_txs[i] in response['transactionIds'], "tx is not in mem pool")

        for i in range(self.max_mempool_slots, len(reinjected_txs)):
            assert_false(reinjected_txs[i] in response['transactionIds'], "tx should have been evicted from mem pool")



if __name__ == "__main__":
    SCEvmMempoolSize().main()
