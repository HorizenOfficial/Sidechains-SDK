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

Configuration:
    - 1 SC node
    - 1 MC node
Tests:
    - Add some non-exec txs (so cannot be mined) to the mempool
    - Wait until the tx lifetime has expired ant then forge a block
    - Check that the non-exec txs are no more in the mempool
"""


class SCEvmMempoolTimeout(AccountChainSetup):
    def __init__(self):
        super().__init__(tx_lifetime=1)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('3000.0')
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        self.sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        num_of_txs_in_mempool = 5
        nonce = 2
        for i in range(num_of_txs_in_mempool):
            createEIP1559Transaction(sc_node, fromAddress=evm_address,
                                     toAddress=evm_address, nonce=nonce, value=1)
            nonce += 1

        # Check the mem pool
        response = allTransactions(sc_node, False)
        assert_equal(num_of_txs_in_mempool, len(response['transactionIds']), "Wrong number of txs in mem pool")

        # Wait for the timeout to expire
        time.sleep(self.tx_lifetime)

        # Generate a block.
        generate_next_block(sc_node, "first node")

        # Check the mem pool
        response = allTransactions(sc_node, False)
        assert_equal(0, len(response['transactionIds']), "Wrong number of txs in mem pool")


if __name__ == "__main__":
    SCEvmMempoolTimeout().main()
