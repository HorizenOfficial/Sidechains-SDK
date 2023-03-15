#!/usr/bin/env python3
import time
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.scutil import generate_next_block, \
    assert_equal
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import forward_transfer_to_sidechain

"""
Check that transactions staying too long in the mempool are evicted for timeout.
2 SC nodes are used in order to verify that both locally and remotely generated transactions are
treated the same way.

Configuration:
    - 2 SC nodes
    - 1 MC node
Tests:
    - Add some non-exec txs (so cannot be mined) to the mempool node 1
    - Wait until the tx lifetime has expired and then forge a block
    - Check that the non-exec txs are no more in the mempool of both nodes
"""


class SCEvmMempoolTimeout(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, tx_lifetime=1)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('3000.0')
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        num_of_txs_in_mempool = 5
        nonce = 2
        # Creates txs on node 1
        for _ in range(num_of_txs_in_mempool):
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_1,
                                     toAddress=evm_address_1, nonce=nonce, value=1)
            nonce += 1
        generate_next_block(sc_node_1, "first node")

        # Check node 1 mem pool
        response = allTransactions(sc_node_1, False)
        assert_equal(num_of_txs_in_mempool, len(response['transactionIds']), "Wrong number of txs in mem pool")

        # Wait until both nodes are in sync
        self.sc_sync_all()

        # Check node 2 mem pool
        response = allTransactions(sc_node_2, False)
        assert_equal(num_of_txs_in_mempool, len(response['transactionIds']), "Wrong number of txs in mem pool")

        # Wait for the timeout to expire
        time.sleep(self.tx_lifetime)

        # Generate a block.
        generate_next_block(sc_node_1, "first node")
        # Wait until both nodes are in sync
        self.sc_sync_all()

        # Check the mem pool on node 1
        response = allTransactions(sc_node_1, False)
        assert_equal(0, len(response['transactionIds']), "Wrong number of txs in mem pool")

        # Check the mem pool on node 2
        response = allTransactions(sc_node_2, False)
        assert_equal(0, len(response['transactionIds']), "Wrong number of txs in mem pool")


if __name__ == "__main__":
    SCEvmMempoolTimeout().main()
