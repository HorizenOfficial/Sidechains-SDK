#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from eth_utils import remove_0x_prefix

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
    - 1 SC node
    - 1 MC node

Test:
    - Add some transactions to the SC nodes. 
    
"""


class SCEvmMempool(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        # Synchronize mc_node1, mc_node2 and mc_node3, then disconnect them.
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]

        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = remove_0x_prefix(self.evm_address)
        evm_address_sc2 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        nonce_addr_1 = 0

        assert_true(sc_node_1.rpc_eth_getTransactionCount(self.evm_address, 'pending')['result'] == '0x0')
        assert_true(sc_node_1.rpc_eth_getBlockTransactionCountByNumber('pending')['result'] == '0x0')

        common_tx_list = []
        for i in range(4):
            common_tx_list.append(
                createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                         nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1))
            nonce_addr_1 += 1

        logging.info("Mempool node 1")
        response = allTransactions(sc_node_1, False)
        logging.info(response)
        assert_true(len(response['transactionIds']) == 4)

        assert_true(len(sc_node_1.rpc_eth_getBlockByNumber('pending', True)['result']['transactions']) == 4)
        assert_true(sc_node_1.rpc_eth_getTransactionCount(self.evm_address, 'pending')['result'] == '0x4')


if __name__ == "__main__":
    SCEvmMempool().main()
