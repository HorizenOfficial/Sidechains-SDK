#!/usr/bin/env python3
import json
import pprint
from decimal import Decimal
from eth_abi import decode
from eth_utils import add_0x_prefix, remove_0x_prefix, event_signature_to_log_topic, encode_hex, \
    function_signature_to_4byte_selector, to_checksum_address
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import format_evm, estimate_gas
from SidechainTestFramework.account.httpCalls.transaction.createLegacyEIP155Transaction import \
    createLegacyEIP155Transaction
from SidechainTestFramework.account.httpCalls.transaction.getKeysOwnership import getKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.removeKeysOwnership import removeKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.sendKeysOwnership import sendKeysOwnership
from SidechainTestFramework.account.utils import MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
from SidechainTestFramework.scutil import generate_next_block, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import (assert_equal, assert_true, fail, hex_str_to_bytes, assert_false,
                                 forward_transfer_to_sidechain)

"""
Configuration: 
    - 2 SC node
    - 1 MC node

Test:
    Do some test for handling relations between owned SC/MC addresses via native smart contract call:
    - Add ownership relation and check event
    - Get the list of MC addresses associated to a SC address
    - Remove an association and check event
    Do some negative tests     
"""


def get_address_with_balance(input_list):
    """
    Assumes the list in input is obtained via the RPC cmd listaddressgroupings()
    """
    for group in input_list:
        for record in group:
            addr = record[0]
            val = record[1]
            if val > 0:
                return addr, val
    return None, 0


def check_add_ownership_event(event, sc_addr, mc_addr, op="add"):
    if op == "add":
        sig = 'AddMcAddrOwnership(address,bytes3,bytes32)'
    elif op == "remove":
        sig = 'RemoveMcAddrOwnership(address,bytes3,bytes32)'
    else:
        sig = ""
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


def forge_and_check_receipt(self, sc_node, tx_hash, expected_receipt_status=1, sc_addr=None, mc_addr=None,
                            evt_op="add"):
    generate_next_block(sc_node, "first node")
    self.sc_sync_all()

    check_receipt(sc_node, tx_hash, expected_receipt_status, sc_addr, mc_addr, evt_op)


def check_receipt(sc_node, tx_hash, expected_receipt_status=1, sc_addr=None, mc_addr=None, evt_op="add"):
    # check receipt
    receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
    if 'result' not in receipt or receipt['result'] is None:
        raise Exception('Rpc eth_getTransactionReceipt cmd failed:{}'.format(json.dumps(receipt, indent=2)))

    status = int(receipt['result']['status'], 16)
    assert_true(status == expected_receipt_status)

    # if we have a succesful receipt and valid func parameters, check the event
    if expected_receipt_status == 1:
        if (sc_addr is not None) and (mc_addr is not None):
            assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
            event = receipt['result']['logs'][0]
            check_add_ownership_event(event, sc_addr, mc_addr, evt_op)
    else:
        assert_equal(0, len(receipt['result']['logs']), "No events should be in receipt")


def check_get_key_ownership(abi_return_value, exp_dict):
    # the location of the data part of the first (the only one in this case) parameter (dynamic type), measured in bytes
    # from the start of the return data block. In this case 32 (0x20)
    start_data_offset = decode(['uint32'], hex_str_to_bytes(abi_return_value[0:64]))[0] * 2
    assert_equal(start_data_offset, 64)

    end_offset = start_data_offset + 64  # read 32 bytes
    list_size = decode(['uint32'], hex_str_to_bytes(abi_return_value[start_data_offset:end_offset]))[0]

    sc_associations_dict = {}
    for i in range(list_size):
        start_offset = end_offset
        end_offset = start_offset + 192  # read (32 + 32 + 32) bytes
        (address_pref, mca3, mca32) = decode(['address', 'bytes3', 'bytes32'],
                                             hex_str_to_bytes(abi_return_value[start_offset:end_offset]))
        sc_address_checksum_fmt = to_checksum_address(address_pref)
        print("sc addr=" + sc_address_checksum_fmt)
        if sc_address_checksum_fmt in sc_associations_dict:
            mc_addr_list = sc_associations_dict.get(sc_address_checksum_fmt)
        else:
            sc_associations_dict[sc_address_checksum_fmt] = []
            mc_addr_list = []
        mc_addr = (mca3 + mca32).decode('utf-8')
        mc_addr_list.append(mc_addr)
        print("mc addr=" + mc_addr)
        sc_associations_dict[sc_address_checksum_fmt] = mc_addr_list

    pprint.pprint(sc_associations_dict)
    res = json.dumps(sc_associations_dict)
    assert_equal(res, json.dumps(dict(sorted(exp_dict.items()))))


# The activation epoch of the zendao feature, as coded in the sdk
ZENDAO_FORK_EPOCH = 7


class SCEvmMcAddressOwnership(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2,
                         block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * ZENDAO_FORK_EPOCH)

    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]

        sc_node2 = self.sc_nodes[1]
        sc_address2 = sc_node2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # transfer some fund from MC to SC2
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      sc_address2,
                                      ft_amount_in_zen,
                                      self.mc_return_address)
        self.sc_sync_all()

        self.block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_address = remove_0x_prefix(self.evm_address)
        sc_address_checksum_fmt = to_checksum_address(self.evm_address)

        lag_list = mc_node.listaddressgroupings()
        taddr1, val = get_address_with_balance(lag_list)
        assert_true(taddr1 is not None)


        # reach the fork
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestEpochNumber"]

        for i in range(0, ZENDAO_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()


        # try adding many mc addresses and forge a block after that
        num_of_association = 10000
        num_of_tx_in_block = 16
        taddr_list = []
        tx_hash_list = []
        for i in range(num_of_association):
            taddr = mc_node.getnewaddress()
            taddr_list.append(taddr)
            mc_signature = mc_node.signmessage(taddr, sc_address_checksum_fmt)
            '''print("scAddr: " + sc_address_checksum_fmt)
            print("mcAddr: " + taddr)
            print("mcSignature: " + mc_signature)'''

            tx_hash_list.append(sendKeysOwnership(sc_node, nonce= i,
                                                  sc_address=sc_address,
                                                  mc_addr=taddr,
                                                  mc_signature=mc_signature)['transactionId'])
            self.sc_sync_all()

            if (i % num_of_tx_in_block == 0):
                print("Generating new block (i = {})...".format(i))
                generate_next_block(sc_node, "first node")
                self.sc_sync_all()
                #pprint.pprint(allTransactions(sc_node, True))

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        for i, txHash in enumerate(tx_hash_list):
            #print("Index = ", i)
            check_receipt(sc_node, txHash, sc_addr=sc_address, mc_addr=taddr_list[i])

        list_associations_sc_address = getKeysOwnership(sc_node, sc_address=sc_address)

        # check we have just one sc address association
        assert_true(len(list_associations_sc_address['keysOwnership']) == 1)
        # check we have the expected associations
        assert_true(len(list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt]) == num_of_association)
        for taddr in taddr_list:
            assert_true(taddr in list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt])

        # execute native smart contract for getting all associations
        method = 'getAllKeyOwnerships()'
        abi_str = function_signature_to_4byte_selector(method)
        req = {
            "from": format_evm(sc_address),
            "to": format_evm(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS),
            "nonce": 3,
            "gasLimit": 230000000,
            "gasPrice": 850000000,
            "value": 0,
            "data": encode_hex(abi_str)
        }
        response = sc_node2.rpc_eth_call(req, 'latest')
        pprint.pprint(response)
        abi_return_value = remove_0x_prefix(response['result'])
        #print(abi_return_value)
        result_string_length = len(abi_return_value)
        # we have an offset of 64 bytes and 12 records with 3 chunks of 32 bytes
        exp_len = 32 + 32 + 12 * (3 * 32)
        assert_equal(result_string_length, 2 * exp_len)



if __name__ == "__main__":
    SCEvmMcAddressOwnership().main()
