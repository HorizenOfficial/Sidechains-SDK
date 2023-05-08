#!/usr/bin/env python3
import json
import pprint
from decimal import Decimal
from eth_abi import decode
from eth_utils import add_0x_prefix, remove_0x_prefix, event_signature_to_log_topic, encode_hex
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.getKeysOwnership import getKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.removeKeysOwnership import removeKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.sendKeysOwnership import sendKeysOwnership
from SidechainTestFramework.scutil import generate_next_block
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from test_framework.util import (assert_equal, assert_true, fail, hex_str_to_bytes, assert_false)

"""
Configuration: 
    - 1 SC node
    - 1 MC node

Test:
    xxx
     
"""

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


def check_add_ownership_event(event, sc_addr, mc_addr, op="add"):
    if op == "add":
        sig = 'AddMcAddrOwnership(address,bytes3,bytes32)'
    elif op == "remove":
        sig = 'RemoveMcAddrOwnership(address,bytes3,bytes32)'
    else:
        assert_false("Invalid op = " + op)

    assert_equal(2, len(event['topics']), "Wrong number of topics in event")
    event_id = remove_0x_prefix(event['topics'][0])
    event_signature = remove_0x_prefix(
        encode_hex(event_signature_to_log_topic(sig)))
    assert_equal(event_signature, event_id, "Wrong event signature in topics")

    evt_sc_addr = decode(['address'], hex_str_to_bytes(event['topics'][1][2:]))[0][2:]
    assert_equal(sc_addr, evt_sc_addr, "Wrong sc_addr address in topics")

    (mca3, mca32) = decode(['bytes3', 'bytes32'], hex_str_to_bytes(event['data'][2:]))
    evt_mc_addr = (mca3 + mca32).decode('utf-8')
    assert_equal(mc_addr, evt_mc_addr, "Wrong mc_addr string in topics")


def forge_and_check_receipt(self, sc_node, tx_hash, expected_receipt_status=1, sc_addr=None, mc_addr=None, evt_op="add"):
    generate_next_block(sc_node, "first node")
    self.sc_sync_all()

    # check receipt
    receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
    if not 'result' in receipt or receipt['result'] == None:
        raise Exception('Rpc eth_getTransactionReceipt cmd failed:{}'.format(json.dumps(receipt, indent=2)))

    status = int(receipt['result']['status'], 16)
    assert_true(status == expected_receipt_status)

    # if we have a succesful receipt and valid func parameters, check the event
    if (expected_receipt_status == 1):
        if (sc_addr is not None) and (mc_addr is not None) :
            assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
            event = receipt['result']['logs'][0]
            check_add_ownership_event(event, sc_addr, mc_addr, evt_op)
    else:
        assert_equal(0, len(receipt['result']['logs']), "No events should be in receipt")



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
        taddr1, val = get_address_with_balance(lag_list)

        assert_true(taddr1 is not None)

        mc_signature1 = mc_node.signmessage(taddr1, sc_address)
        print("scAddr: " + sc_address)
        print("mcAddr: " + taddr1)
        print("mcSignature: " + mc_signature1)


        ret1 = sendKeysOwnership(sc_node,
                                 sc_address=sc_address,
                                 mc_addr=taddr1,
                                 mc_signature=mc_signature1)
        pprint.pprint(ret1)

        tx_hash1 = ret1['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash1, sc_addr=sc_address, mc_addr=taddr1)


        # mc recycles addresses
        while True:
            taddr2 = mc_node.getnewaddress()
            if taddr2 != taddr1:
                break
            else:
                print(taddr2, "...", taddr1)

        mc_signature2 = mc_node.signmessage(taddr2, sc_address)
        print("mcAddr: " + taddr2)
        print("mcSignature: " + mc_signature2)

        ret2 = sendKeysOwnership(sc_node,
                                 sc_address=sc_address,
                                 mc_addr=taddr2,
                                 mc_signature=mc_signature2)
        pprint.pprint(ret2)

        tx_hash2 = ret2['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash2, sc_addr=sc_address, mc_addr=taddr2)

        ret3 = getKeysOwnership(sc_node, sc_address=sc_address)
        pprint.pprint(ret3)

        assert_true(taddr1 in ret3['mcAddresses'])
        assert_true(taddr2 in ret3['mcAddresses'])


        # negative cases
        # 1. try to add the same ownership
        try:
            sendKeysOwnership(sc_node,
                                     sc_address=sc_address,
                                     mc_addr=taddr2,
                                     mc_signature=mc_signature2)
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("already linked" in str(err) )
        else:
            fail("duplicate association should not work")


        # 2. try to add a not owned ownership. The tx is executed but the receipt has a failed status
        taddr3 = mc_node.getnewaddress()

        ret4 = sendKeysOwnership(sc_node,
                                 sc_address=sc_address,
                                 mc_addr=taddr3,
                                 mc_signature=mc_signature1)
        tx_hash4 = ret4['transactionId']
        pprint.pprint(ret4)

        forge_and_check_receipt(self, sc_node, tx_hash4, expected_receipt_status=0)

        # 3. try to use invalid parameters
        # 3.1 illegal sc address
        try:
            sendKeysOwnership(sc_node,
                              sc_address="1234",
                              mc_addr=taddr2,
                              mc_signature=mc_signature2)
        except SCAPIException as err:
            print("Expected exception thrown: {}".format(str(err.error)))
            assert_true("Invalid SC address" in str(err.error))
        else:
            fail("invalid sc address should not work")


        # 3.2 illegal mc address
        try:
            sendKeysOwnership(sc_node,
                              sc_address=sc_address,
                              mc_addr="1LMcKyPmwebfygoeZP8E9jAMS2BcgH3Yip",
                              mc_signature=mc_signature2)
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(str(err)))
            assert_true("Invalid input parameters" in str(err))
        else:
            fail("invalid mc address should not work")


        # 3.3 illegal mc signature
        try:
            sendKeysOwnership(sc_node,
                              sc_address=sc_address,
                              mc_addr=taddr3,
                              mc_signature="xyz")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(str(err)))
            assert_true("Invalid input parameters" in str(err))
        else:
            fail("invalid mc signature should not work")


        ret2 = removeKeysOwnership(sc_node,
                                 sc_address=sc_address,
                                 mc_addr=taddr2)
        pprint.pprint(ret2)

        tx_hash2 = ret2['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash2, sc_addr=sc_address, mc_addr=taddr2, evt_op="remove")

        ret3 = getKeysOwnership(sc_node, sc_address=sc_address)
        pprint.pprint(ret3)

if __name__ == "__main__":
    SCEvmMcAddressOwnership().main()
