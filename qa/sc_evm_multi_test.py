#!/usr/bin/env python3
from decimal import Decimal

from SidechainTestFramework.multi_sc_test_framework import MultiSidechainTestFramework, AccountSidechainInfo, \
    UTXOSidechainInfo
from SidechainTestFramework.scutil import (
    generate_next_block,
)
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.findTransactionByID import http_transaction_findById
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import forward_transfer_to_sidechain, assert_equal

"""
This is an example test to show how to use MultiSidechainTestFramework
"""


class SCEvmMultiTest(MultiSidechainTestFramework):
    def __init__(self):
        super().__init__()

    def sc_create_sidechains(self):
        self.sidechains.append(AccountSidechainInfo(self.options, number_of_sidechain_nodes=2, max_nonce_gap=5))
        self.sidechains.append(UTXOSidechainInfo(self.options, number_of_sidechain_nodes=2))

    def run_test(self):
        mc_node = self.nodes[0]
        mc_return_address = mc_node.getnewaddress()
        sc_utxo = self.sidechains[1]
        sc_evm = self.sidechains[0]
        sc_evm_node_1 = sc_evm.sc_nodes[0]
        sc_evm_node_2 = sc_evm.sc_nodes[1]

        sc_evm_address_1 = sc_evm_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('100.0')

        forward_transfer_to_sidechain(sc_evm.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_evm_address_1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_return_address)
        generate_next_block(sc_evm_node_1, "first node")
        sc_evm.sc_sync_all()

        createEIP1559Transaction(sc_evm_node_1, fromAddress=sc_evm_address_1, toAddress=sc_evm_address_1,
                                 nonce=0, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                 maxFeePerGas=900000000, value=1)
        sc_evm.sc_sync_all()
        assert_equal(allTransactions(sc_evm_node_1, False)['transactionIds'], allTransactions(sc_evm_node_2, False)['transactionIds'])

        sc_utxo_node_1 = sc_utxo.sc_nodes[0]
        sc_utxo_node_2 = sc_utxo.sc_nodes[1]

        sc_utxo_address_1 = http_wallet_createPrivateKey25519(sc_utxo_node_1)
        sc_utxo_address_2 = http_wallet_createPrivateKey25519(sc_utxo_node_2)
        forward_transfer_to_sidechain(sc_utxo.sc_nodes_bootstrap_info.sidechain_id,
                                                                  mc_node,
                                                                  sc_utxo_address_1,
                                                                  sc_utxo.sc_nodes_bootstrap_info.genesis_account_balance,
                                                                  mc_return_address)

        generate_next_block(sc_utxo_node_1, "first node")
        sc_utxo.sc_sync_all()

        txid = sendCoinsToAddress(sc_utxo_node_1, sc_utxo_address_2, 37, fee=0)

        assert_equal(http_transaction_findById(sc_utxo_node_1, txid), http_transaction_findById(sc_utxo_node_2, txid))


if __name__ == "__main__":
    SCEvmMultiTest().main()
