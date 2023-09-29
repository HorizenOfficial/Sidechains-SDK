#!/usr/bin/env python3
import logging
import re
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import eoa_transaction
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.scutil import (
    assert_true, generate_next_block,
)
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import assert_false, assert_equal

"""
Tests for eth namespace rpc methods.

Configuration:
    - 1 SC node
    - 1 MC node

Test:
    - test eth_getTransactionByHash

"""


class SCEvmRPCEth(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        sc_node = self.sc_nodes[0]

        ft_amount_in_zen = Decimal('3000.0')
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        evm_address_sc1 = self.evm_address[2:]
        evm_address_sc2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        #######################################################################################
        # eth_getTransactionByHash tests
        #######################################################################################

        # Test with invalid input transaction id

        res = sc_node.rpc_eth_getTransactionByHash("0xcccbbb")
        assert_true("error" in res)
        assert_false("result" in res)
        assert_equal("Invalid params", res['error']['message'])

        # Test with valid input transaction id but not existing tx
        res = sc_node.rpc_eth_getTransactionByHash("0xcccbbbaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        assert_false("error" in res)
        assert_true("result" in res)
        assert_true(res["result"] is None)

        # Test with a transaction still in the mempool

        tx_id = createEIP1559Transaction(sc_node,
                                         fromAddress=evm_address_sc1,
                                         toAddress=evm_address_sc2,
                                         value=1,
                                         nonce=0
                                         )

        res = sc_node.rpc_eth_getTransactionByHash("0x" + tx_id)
        logging.info(res)
        assert_false("error" in res)
        assert_true("result" in res)
        assert_false(res["result"] is None)

        assert_true(res["result"]['blockHash'] is None)
        assert_true(res["result"]['blockNumber'] is None)
        assert_true(res["result"]['transactionIndex'] is None)
        assert_equal("0x" + tx_id, res["result"]['hash'])

        # Test with a transaction in the blockchain

        block_id = generate_next_block(sc_node, "first node")

        # Verify that the mempool is empty
        response = allTransactions(sc_node, False)
        assert_equal(0, len(response['transactionIds']))

        res = sc_node.rpc_eth_getTransactionByHash("0x" + tx_id)
        assert_false("error" in res)
        assert_true("result" in res)
        assert_false(res["result"] is None)

        res_block = sc_node.block_findById(blockId=block_id)

        assert_equal("0x" + block_id, res["result"]['blockHash'])
        assert_equal(res_block['result']['height'], int(res["result"]['blockNumber'][2:], 16))
        assert_equal("0x0", res["result"]['transactionIndex'])
        assert_equal("0x" + tx_id, res["result"]['hash'])


if __name__ == "__main__":
    SCEvmRPCEth().main()
