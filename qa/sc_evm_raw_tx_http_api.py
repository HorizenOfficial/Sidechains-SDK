#!/usr/bin/env python3
from decimal import Decimal
from eth_utils import add_0x_prefix, remove_0x_prefix
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createRawEIP1559Transaction import createRawEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createRawLegacyEIP155Transaction import \
    createRawLegacyEIP155Transaction
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
    Test ethereum transactions of legacy, ieip155 and eip1559 types using these steps:
    - create unsigned raw tx
    - decode raw tx
    - sign raw tx
    - send raw tx to the network
    
    # TODO
    - Try send an unsigned tx to mempool
    - Try send a tx with bad chainid to mempool
    - try to forge a block with an unsigned tx (see api)
    - Try the same with bad chainid
     
"""


class SCEvmRawTxHttpApi(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def do_test_raw_tx(self, *, raw_tx, sc_node, evm_signer_address):
        self.sc_sync_all()

        # get mempool contents and check it is empty
        response = allTransactions(sc_node, False)
        assert_equal(0, len(response['transactionIds']))

        signed_raw_tx = signTransaction(sc_node, fromAddress=evm_signer_address, payload=raw_tx)

        tx_json = decodeTransaction(sc_node, payload=signed_raw_tx)
        assert_equal(tx_json['signed'], True)

        tx_hash = sendTransaction(sc_node, payload=signed_raw_tx)
        self.sc_sync_all()

        # get mempool contents and check tx is there
        response = allTransactions(sc_node, False)
        assert_true(tx_hash in response['transactionIds'])

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))

        status = int(receipt['result']['status'], 16)
        assert_equal(status, 1)

    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = remove_0x_prefix(self.evm_address)
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        transferred_amount_in_zen = Decimal('1.2')


        raw_tx = createRawLegacyTransaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))
        tx_json = decodeTransaction(sc_node_2, payload=raw_tx)
        assert_equal(tx_json['legacy'], True)
        assert_equal(tx_json['eip155'], False)
        assert_equal(tx_json['eip1559'], False)
        assert_equal(tx_json['signed'], False)

        self.do_test_raw_tx(
            raw_tx=raw_tx, sc_node=sc_node_1, evm_signer_address=evm_address_sc1)



        raw_tx = createRawLegacyEIP155Transaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))
        tx_json = decodeTransaction(sc_node_2, payload=raw_tx)
        assert_equal(tx_json['legacy'], True)
        assert_equal(tx_json['eip155'], True)
        assert_equal(tx_json['eip1559'], False)
        assert_equal(tx_json['signed'], False)

        self.do_test_raw_tx(
            raw_tx=raw_tx, sc_node=sc_node_1, evm_signer_address=evm_address_sc1)




        raw_tx = createRawEIP1559Transaction(sc_node_1,
                                            fromAddress=evm_address_sc1,
                                            toAddress=evm_address_sc2,
                                            value=convertZenToWei(transferred_amount_in_zen))
        tx_json = decodeTransaction(sc_node_2, payload=raw_tx)
        assert_equal(tx_json['legacy'], False)
        assert_equal(tx_json['eip155'], False)
        assert_equal(tx_json['eip1559'], True)
        assert_equal(tx_json['signed'], False)

        self.do_test_raw_tx(
            raw_tx=raw_tx, sc_node=sc_node_1, evm_signer_address=evm_address_sc1)




if __name__ == "__main__":
    SCEvmRawTxHttpApi().main()
