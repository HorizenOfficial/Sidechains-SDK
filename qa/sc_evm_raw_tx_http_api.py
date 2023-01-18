#!/usr/bin/env python3
from decimal import Decimal
from eth_utils import add_0x_prefix, remove_0x_prefix
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createRawLegacyTransaction import createRawLegacyTransaction
from SidechainTestFramework.account.httpCalls.transaction.decodeTransaction import decodeTransaction
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.scutil import generate_next_block
from httpCalls.transaction.allTransactions import allTransactions
from SidechainTestFramework.account.utils import convertZenToWei
from test_framework.util import (assert_equal, assert_true)

"""
Configuration: 
    - 2 SC nodes NOT connected with each other
    - 1 MC node

Test:
    Test sending raw ethereum transactions
     
"""


class SCEvmRawTxHttpApi(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = remove_0x_prefix(self.evm_address)
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        transferred_amount_in_zen = Decimal('11')

        raw_tx = createRawLegacyTransaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))

        tx_json = decodeTransaction(sc_node_2, payload=raw_tx)
        assert_equal(tx_json['legacy'], True)
        assert_equal(tx_json['signed'], False)

        self.sc_sync_all()

        # get mempool contents and check it is empty
        response = allTransactions(sc_node_2, False)
        assert_equal(0, len(response['transactionIds']))

        signed_raw_tx = signTransaction(sc_node_1, fromAddress=evm_address_sc1, payload=raw_tx)

        tx_json = decodeTransaction(sc_node_2, payload=signed_raw_tx)
        assert_equal(tx_json['signed'], True)

        tx_hash = sendTransaction(sc_node_1, payload=signed_raw_tx)
        self.sc_sync_all()

        # get mempool contents and check tx is there
        response = allTransactions(sc_node_2, False)
        assert_true(tx_hash in response['transactionIds'])

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        receipt = sc_node_1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))

        status = int(receipt['result']['status'], 16)
        assert_equal(status, 1)


if __name__ == "__main__":
    SCEvmRawTxHttpApi().main()
