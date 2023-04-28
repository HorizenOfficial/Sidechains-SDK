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
        addr, val = get_address_with_balance(lag_list)

        assert_true(addr is not None)

        ret1 = mc_node.validateaddress(addr)
        pub_key = ret1['pubkey']

        mc_signature = mc_node.signmessage(addr, sc_address)
        pprint.pprint(sc_address)
        pprint.pprint(pub_key)
        pprint.pprint(mc_signature)
        # TODO
        # negative case
        # pub_key = "0275e40b8af8b7e8e8d30a821ab659dae06d819d13672826e785953fb037a31d5c"
        '''sc_address = "00c8f107a09cd4f463afc2f1e6e5bf6022ad4600"
        mc_signature = "IDgebBBTdzx10ItOJGbOsd6hfNVSjo/kGI7QxX/fDBcPdzJ3AUQ7TZ2pv401N2SyaIckJ4nrZisR9O/n6pX7Qyw="
        '''
        ret3 = sendKeysOwnership(sc_node,
                                 fromAddress=sc_address,
                                 mc_pub_key=pub_key,
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
