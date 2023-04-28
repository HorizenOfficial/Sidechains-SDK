#!/usr/bin/env python3
import logging
import pprint
from binascii import a2b_hex, b2a_hex
from decimal import Decimal

import rlp
from eth_utils import add_0x_prefix, remove_0x_prefix
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createRawEIP1559Transaction import createRawEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createRawLegacyEIP155Transaction import \
    createRawLegacyEIP155Transaction
from SidechainTestFramework.account.httpCalls.transaction.createRawLegacyTransaction import createRawLegacyTransaction
from SidechainTestFramework.account.httpCalls.transaction.decodeTransaction import decodeTransaction
from SidechainTestFramework.account.httpCalls.transaction.sendKeysOwnership import sendKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.scutil import generate_next_block
from httpCalls.block.best import http_block_best
from httpCalls.transaction.allTransactions import allTransactions
from SidechainTestFramework.account.utils import convertZenToWei
from test_framework.util import (assert_equal, assert_true, fail)

"""
Configuration: 
    - 1 SC node
    - 1 MC node

Test:
    xxx
     
"""


def b2x(b):
    return b2a_hex(b).decode('ascii')

def get_address_with_balance(input_list):
    '''
    Assumes the list in input is obtained via the RPC cmd listaddressgroupings()
    '''
    for group in input_list:
        for record in group:
            addr = record[0]
            val  = record[1]
            if val > 0:
                return (addr, val)
    return (None, 0)

class SCEvmMcAddressOwnership(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=1)


    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]

        sc_address = remove_0x_prefix(self.evm_address)


        lag_list = mc_node.listaddressgroupings()
        taddr, val = get_address_with_balance(lag_list)

        assert_true(taddr is not None)

        mc_signature = mc_node.signmessage(taddr, sc_address)
        print("scAddr: " + sc_address)
        print("mcAddr: " + taddr)
        print("mcSignature: " + mc_signature)
        # TODO
        # negative case

        ret3 = sendKeysOwnership(sc_node,
                                 sc_address=sc_address,
                                 mc_addr=taddr,
                                 mc_signature=mc_signature)
        pprint.pprint(ret3)

        tx_hash = ret3['transactionId']
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # check receipt, meanwhile do some check on amounts
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)









if __name__ == "__main__":
    SCEvmMcAddressOwnership().main()
