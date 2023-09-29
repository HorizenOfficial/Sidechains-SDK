#!/usr/bin/env python3
import json
import logging
import pprint
from decimal import Decimal

from eth_abi import decode
from eth_utils import add_0x_prefix, remove_0x_prefix, event_signature_to_log_topic, encode_hex, \
    function_signature_to_4byte_selector, to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, estimate_gas, format_eoa
from SidechainTestFramework.account.httpCalls.transaction.createLegacyEIP155Transaction import \
    createLegacyEIP155Transaction
from SidechainTestFramework.account.httpCalls.transaction.getKeysOwnership import getKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.removeKeysOwnership import removeKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.sendKeysOwnership import sendKeysOwnership
from SidechainTestFramework.account.simple_proxy_contract import SimpleProxyContract
from SidechainTestFramework.account.utils import MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, INTEROPERABILITY_FORK_EPOCH, \
    ZENDAO_FORK_EPOCH
from SidechainTestFramework.scutil import generate_next_block, EVM_APP_SLOT_TIME
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
    - Interoperability test: same tests as before but using a proxy evm smart contract
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
    assert_equal(expected_receipt_status, status)

    # if we have a successful receipt and valid func parameters, check the event
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
    abi_return_value_bytes = hex_str_to_bytes(abi_return_value)
    sc_associations_dict = extract_sc_associations_list(abi_return_value_bytes)

    pprint.pprint(sc_associations_dict)
    res = json.dumps(sc_associations_dict)
    assert_equal(res, json.dumps(dict(sorted(exp_dict.items()))))


def extract_sc_associations_list(abi_return_value_bytes):
    start_data_offset = decode(['uint32'], abi_return_value_bytes[0:32])[0]
    assert_equal(start_data_offset, 32)
    end_offset = start_data_offset + 32  # read 32 bytes
    list_size = decode(['uint32'], abi_return_value_bytes[start_data_offset:end_offset])[0]
    sc_associations_dict = {}
    for i in range(list_size):
        start_offset = end_offset
        end_offset = start_offset + 96  # read (32 + 32 + 32) bytes
        (address_pref, mca3, mca32) = decode(['address', 'bytes3', 'bytes32'],
                                             abi_return_value_bytes[start_offset:end_offset])
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
    return sc_associations_dict


class SCEvmMcAddressOwnership(AccountChainSetup):
    def __init__(self):
        super().__init__(block_timestamp_rewind=1500 * EVM_APP_SLOT_TIME * INTEROPERABILITY_FORK_EPOCH,
                         number_of_sidechain_nodes=2)

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

        # check the balance of native smart contract is null
        native_contract_address = MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
        nsc_bal = int(
            sc_node.rpc_eth_getBalance(format_evm(native_contract_address), 'latest')['result'], 16)
        assert_equal(nsc_bal, 0)

        # send funds to native smart contract before the zendao fork is reached
        eoa_nsc_amount = 123456
        tx_hash_eoa = createLegacyEIP155Transaction(sc_node2,
                                                    fromAddress=sc_address2,
                                                    toAddress=native_contract_address,
                                                    value=eoa_nsc_amount
                                                    )
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # get mempool contents and check tx has been forged even if the fork is not active yet. Check the receipt
        response = allTransactions(sc_node, False)
        assert_true(tx_hash_eoa not in response['transactionIds'])
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_eoa))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status)
        gas_used = int(receipt['result']['gasUsed'], 16)
        assert_equal(gas_used, 21000)

        # check the address has the expected balance
        nsc_bal = int(
            sc_node.rpc_eth_getBalance(format_evm(native_contract_address), 'latest')['result'], 16)
        assert_equal(nsc_bal, eoa_nsc_amount)

        mc_signature1 = mc_node.signmessage(taddr1, sc_address_checksum_fmt)
        print("scAddr: " + sc_address_checksum_fmt)
        print("mcAddr: " + taddr1)
        print("mcSignature: " + mc_signature1)

        # try adding sc/mc ownership sending a transaction with data invoking native smart contract before fork point
        ret = sendKeysOwnership(sc_node, nonce=0,
                                sc_address=sc_address,
                                mc_addr=taddr1,
                                mc_signature=mc_signature1)

        tx_hash = ret['transactionId']
        self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # get mempool contents and check tx has been forged even if the fork is not active yet since it is processed
        # by the eoa msg processor. Check also the receipt and gas used greater than an eoa (due to contract code)
        response = allTransactions(sc_node, False)
        assert_true(tx_hash not in response['transactionIds'])
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)
        gas_used = int(receipt['result']['gasUsed'], 16)
        assert_true(gas_used > 21000)

        # reach the ZenDao fork
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        for i in range(0, ZENDAO_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        # add the same sc/mc ownership , as abovesending a transaction with data invoking native smart contract
        ret = sendKeysOwnership(sc_node, nonce=1,
                                sc_address=sc_address,
                                mc_addr=taddr1,
                                mc_signature=mc_signature1)

        tx_hash = ret['transactionId']
        self.sc_sync_all()

        # check the tx adding an ownership is included in the block and the receipt is succesful after fork activation
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=taddr1)

        # get another address (note that mc recycles addresses)
        while True:
            taddr2 = mc_node.getnewaddress()
            if taddr2 != taddr1:
                break

        mc_signature2 = mc_node.signmessage(taddr2, sc_address_checksum_fmt)
        print("scAddr: " + sc_address_checksum_fmt)
        print("mcAddr: " + taddr2)
        print("mcSignature: " + mc_signature2)

        # add a second owned mc address linked to the same sc address, and use a different sc address format, we should
        # support that
        ret = sendKeysOwnership(sc_node, nonce=2,
                                sc_address=to_checksum_address(sc_address),
                                mc_addr=taddr2,
                                mc_signature=mc_signature2)

        tx_hash = ret['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=taddr2)

        # check we have both association and only them (and we support different sc address formats)
        ret = getKeysOwnership(sc_node, sc_address=sc_address)
        ret2 = getKeysOwnership(sc_node, sc_address=to_checksum_address(sc_address))
        assert_equal(ret, ret2)

        # check we have just one sc address association
        assert_true(len(ret['keysOwnership']) == 1)
        # check we have two mc address associated to sc address
        assert_true(len(ret['keysOwnership'][sc_address_checksum_fmt]) == 2)
        # check we have exactly those mc address
        assert_true(taddr1 in ret['keysOwnership'][sc_address_checksum_fmt])
        assert_true(taddr2 in ret['keysOwnership'][sc_address_checksum_fmt])

        ret = removeKeysOwnership(sc_node,
                                  sc_address=sc_address,
                                  mc_addr=taddr2)
        pprint.pprint(ret)

        tx_hash = ret['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=taddr2, evt_op="remove")

        ret = getKeysOwnership(sc_node, sc_address=sc_address)

        # check we have just one sc address association
        assert_true(len(ret['keysOwnership']) == 1)
        # check we have only one mc address associated to sc address
        assert_true(len(ret['keysOwnership'][sc_address_checksum_fmt]) == 1)
        # check we have exactly that mc address
        assert_true(taddr1 in ret['keysOwnership'][sc_address_checksum_fmt])
        assert_true(taddr2 not in ret['keysOwnership'][sc_address_checksum_fmt])

        # get association for a sc address not yet associated to any mc address
        ret = getKeysOwnership(sc_node, sc_address=sc_address2)
        # check we have no association for this sc address
        assert_true(len(ret['keysOwnership']) == 0)

        # negative cases
        # 1. try to add the ownership already there
        try:
            sendKeysOwnership(sc_node,
                              sc_address=sc_address,
                              mc_addr=taddr1,
                              mc_signature=mc_signature1)
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("already associated" in str(err))
        else:
            fail("duplicate association should not work")

        # 2. try to add a not owned ownership. The tx is executed but the receipt has a failed status
        taddr3 = mc_node.getnewaddress()

        ret = sendKeysOwnership(sc_node, nonce=4,
                                sc_address=sc_address,
                                mc_addr=taddr3,
                                mc_signature=mc_signature1)
        tx_hash = ret['transactionId']

        forge_and_check_receipt(self, sc_node, tx_hash, expected_receipt_status=0)

        # 3. try to use invalid parameters
        # 3.1 illegal sc address
        invalid_sc_addr = "1234h"
        try:
            sendKeysOwnership(sc_node,
                              sc_address=invalid_sc_addr,
                              mc_addr=taddr2,
                              mc_signature=mc_signature2)
        except Exception as err:
            print("Expected exception thrown: {}".format(str(err)))
            assert_true("Account " + invalid_sc_addr + " is invalid " in str(err))
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

        # 4. try to remove an ownership not stored in db.
        try:
            removeKeysOwnership(sc_node,
                                sc_address=sc_address,
                                mc_addr=taddr3)
        except Exception as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("not linked" in str(err))
        else:
            fail("duplicate association should not work")

        # 5. try to remove an ownership passing a null mc addr (not yet supported).
        try:
            removeKeysOwnership(sc_node,
                                sc_address=sc_address,
                                mc_addr=None)
        except SCAPIException as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("MC address must be specified" in str(err.error))
        else:
            fail("duplicate association should not work")

        # re-add the mc address we removed
        ret = sendKeysOwnership(sc_node, nonce=5,
                                sc_address=sc_address,
                                mc_addr=taddr2,
                                mc_signature=mc_signature2)

        tx_hash = ret['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=taddr2)

        # try adding 10 mc addresses and forge a block after that
        taddr_list = []
        tx_hash_list = []
        for i in range(10):
            taddr = mc_node.getnewaddress()
            taddr_list.append(taddr)
            mc_signature = mc_node.signmessage(taddr, sc_address_checksum_fmt)
            print("scAddr: " + sc_address_checksum_fmt)
            print("mcAddr: " + taddr)
            print("mcSignature: " + mc_signature)

            tx_hash_list.append(sendKeysOwnership(sc_node, nonce=6 + i,
                                                  sc_address=sc_address,
                                                  mc_addr=taddr,
                                                  mc_signature=mc_signature)['transactionId'])
            self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        for i, txHash in enumerate(tx_hash_list):
            print("Index = ", i)
            check_receipt(sc_node, txHash, sc_addr=sc_address, mc_addr=taddr_list[i])

        list_associations_sc_address = getKeysOwnership(sc_node, sc_address=sc_address)

        # check we have just one sc address association
        assert_true(len(list_associations_sc_address['keysOwnership']) == 1)
        # check we have the previous 2 mc addresses plus 10 just associated
        assert_true(len(list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt]) == 12)
        for taddr in taddr_list:
            assert_true(taddr in list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt])

        # remove a mc addr and check we have 11 of them
        taddr_rem = taddr_list[4]

        assert_true(len(taddr_list) == 10)
        taddr_list.remove(taddr_rem)
        assert_true(len(taddr_list) == 9)

        # use a different sc address format, we should support that
        removeKeysOwnership(sc_node, nonce=16,
                            sc_address=to_checksum_address(sc_address),
                            mc_addr=taddr_rem)

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        list_associations_sc_address = getKeysOwnership(sc_node, sc_address=sc_address)

        # check we have just one sc address association
        assert_true(len(list_associations_sc_address['keysOwnership']) == 1)
        # check we have the expected number
        assert_true(len(list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt]) == 11)
        for taddr in taddr_list:
            assert_true(taddr in list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt])

        # add an association for a different sc address
        taddr_sc2_1 = mc_node.getnewaddress()
        sc_address2_checksum_fmt = to_checksum_address(sc_address2)
        mc_signature_sc2 = mc_node.signmessage(taddr_sc2_1, sc_address2_checksum_fmt)
        print("scAddr: " + sc_address2_checksum_fmt)
        print("mcAddr: " + taddr_sc2_1)
        print("mcSignature: " + mc_signature_sc2)

        ret = sendKeysOwnership(sc_node2,
                                sc_address=sc_address2,
                                mc_addr=taddr_sc2_1,
                                mc_signature=mc_signature_sc2)
        self.sc_sync_all()
        tx_hash = ret['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address2, mc_addr=taddr_sc2_1)

        # associate to sc address 2 a mc addr already associated to sc address 1, the tx is rejected
        mc_signature_sc2 = mc_node.signmessage(taddr1, sc_address2_checksum_fmt)
        print("### associate to sc address 2 a mc addr already associated to sc address 1")
        print("scAddr: " + sc_address2_checksum_fmt)
        print("mcAddr: " + taddr1)
        print("mcSignature: " + mc_signature_sc2)

        try:
            sendKeysOwnership(sc_node2,
                                    sc_address=sc_address2,
                                    mc_addr=taddr1,
                                    mc_signature=mc_signature_sc2)
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("is already associated" in str(err))
        else:
            fail("duplicate association should not work")

        list_all_associations = getKeysOwnership(sc_node)
        pprint.pprint(list_all_associations)

        # check we have two sc address associations
        assert_equal(2, len(list_all_associations['keysOwnership']))
        # check we have the expected numbers
        assert_equal(11, len(list_all_associations['keysOwnership'][sc_address_checksum_fmt]))
        assert_equal(1, len(list_all_associations['keysOwnership'][sc_address2_checksum_fmt]))
        assert_true(taddr_sc2_1 in list_all_associations['keysOwnership'][sc_address2_checksum_fmt])

        # execute native smart contract for getting all associations
        method = 'getAllKeyOwnerships()'
        abi_str = function_signature_to_4byte_selector(method)
        req = {
            "from": format_evm(sc_address),
            "to": format_evm(native_contract_address),
            "nonce": 3,
            "gasLimit": 2300000,
            "gasPrice": 850000000,
            "value": 0,
            "data": encode_hex(abi_str)
        }
        response = sc_node2.rpc_eth_call(req, 'latest')
        abi_return_value = remove_0x_prefix(response['result'])
        print(abi_return_value)
        result_string_length = len(abi_return_value)
        # we have an offset of 64 bytes and 12 records with 3 chunks of 32 bytes
        exp_len = 32 + 32 + 12 * (3 * 32)
        assert_equal(result_string_length, 2 * exp_len)

        check_get_key_ownership(abi_return_value, list_all_associations['keysOwnership'])

        # execute native smart contract for getting sc address associations
        method = 'getKeyOwnerships(address)'
        abi_str = function_signature_to_4byte_selector(method)
        addr_padded_str = "000000000000000000000000" + sc_address
        req = {
            "from": format_evm(sc_address),
            "to": format_evm(native_contract_address),
            "nonce": 3,
            "gasLimit": 2300000,
            "gasPrice": 850000000,
            "value": 0,
            "data": encode_hex(abi_str) + addr_padded_str
        }
        response = sc_node2.rpc_eth_call(req, 'latest')
        abi_return_value = remove_0x_prefix(response['result'])
        print(abi_return_value)
        result_string_length = len(abi_return_value)
        # we have an offset of 64 bytes and 11 records with 3 chunks of 32 bytes
        exp_len = 32 + 32 + 11 * (3 * 32)
        assert_equal(result_string_length, 2 * exp_len)

        check_get_key_ownership(abi_return_value, list_associations_sc_address['keysOwnership'])

        # add one more association and check gas estimation
        taddr_sc2_2 = mc_node.getnewaddress()
        mc_signature_sc2 = mc_node.signmessage(taddr_sc2_2, sc_address2_checksum_fmt)
        print("scAddr: " + sc_address2_checksum_fmt)
        print("mcAddr: " + taddr_sc2_2)
        print("mcSignature: " + mc_signature_sc2)

        ret = sendKeysOwnership(sc_node2,
                                sc_address=sc_address2,
                                mc_addr=taddr_sc2_2,
                                mc_signature=mc_signature_sc2)
        self.sc_sync_all()
        tx_hash_check = ret['transactionId']

        response = allTransactions(sc_node, True)
        est_gas_nsc_data = response['transactions'][0]['data']

        response = estimate_gas(sc_node2, sc_address2_checksum_fmt,
                                to_address=to_checksum_address(native_contract_address),
                                data="0x" + est_gas_nsc_data,
                                gasPrice='0x35a4e900',
                                nonce=0)

        est_gas_used = response['result']

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_check))
        status = int(receipt['result']['status'], 16)
        assert_true(status == 1)
        gas_used = receipt['result']['gasUsed']
        assert_equal(est_gas_used, gas_used)

        #######################################################################################################
        # Interoperability test with an EVM smart contract calling MC address ownership native contract
        #######################################################################################################

        # Create and deploy evm proxy contract
        # Create a new sc address to be used for the interoperability tests
        evm_address_interop = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        new_ft_amount_in_zen = Decimal('50.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_interop,
                                      new_ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        generate_next_block(sc_node, "first node")

        # Deploy proxy contract
        proxy_contract = SimpleProxyContract(sc_node, evm_address_interop)
        # Create native contract interface, useful encoding/decoding params. Note this doesn't deploy a contract.
        native_contract = SmartContract("McAddrOwnership")

        # Test before interoperability fork
        method = 'getAllKeyOwnerships()'
        native_input = format_eoa(native_contract.raw_encode_call(method))
        try:
            proxy_contract.do_static_call(evm_address_interop, 1, native_contract_address, native_input)
            fail("Interoperability call should fail before fork point")
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            # error is raised from API since the address has no balance
            assert_true("reverted" in str(err))


        # reach the Interoperability fork
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        for i in range(0, INTEROPERABILITY_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        # Test getAllKeyOwnerships()

        res = proxy_contract.do_static_call(evm_address_interop, 1, native_contract_address, native_input)

        sc_associations_list = extract_sc_associations_list(res)
        logging.info("res: {}".format(sc_associations_list))
        assert_equal(2, len(sc_associations_list), "wrong number of sidechain addresses")
        assert_equal(11, len(sc_associations_list[sc_address_checksum_fmt]),
                     " wrong number of associations for sc_address_1")
        assert_equal(2, len(sc_associations_list[sc_address2_checksum_fmt]),
                     "wrong number of associations for sc_address_2")

        # Test 'getKeyOwnerships(address)'
        method = 'getKeyOwnerships(address)'

        native_input = format_eoa(native_contract.raw_encode_call(method, sc_address))

        res = proxy_contract.do_static_call(evm_address_interop, 1, native_contract_address, native_input)

        sc_associations_list = extract_sc_associations_list(res)
        logging.info("res: {}".format(sc_associations_list))
        assert_equal(1, len(sc_associations_list), "wrong number of sidechain addresses")
        assert_equal(11, len(sc_associations_list[sc_address_checksum_fmt]),
                     " wrong number of associations for sc_address_1")

        # Test getKeyOwnerScAddresses()
        method = 'getKeyOwnerScAddresses()'
        native_input = format_eoa(native_contract.raw_encode_call(method))

        res = proxy_contract.do_static_call(evm_address_interop, 1, native_contract_address, native_input)

        sc_address_list = native_contract.raw_decode_call_result(method, res)[0]
        logging.info("sc_address_list: {}".format(sc_address_list))
        assert_equal(2, len(sc_address_list), "wrong number of sidechain addresses")
        assert_true("0x" + sc_address in sc_address_list)
        assert_true("0x" + sc_address2 in sc_address_list)

        # Test 'sendKeysOwnership(bytes3,bytes32,bytes24,bytes32,bytes32)'
        # For this function I need a signature. Because I don't have a way to create it in this test, I'll create a
        # non-executable transaction using the HTTP API and steal its data.

        taddr_interop = mc_node.getnewaddress()
        sc_address_checksum_interop = to_checksum_address(evm_address_interop)
        mc_signature_interop = mc_node.signmessage(taddr_interop, sc_address_checksum_interop)

        # Create a non-executable transaction with the data invoking native smart contract
        ret = sendKeysOwnership(sc_node, nonce=10,
                                sc_address=evm_address_interop,
                                mc_addr=taddr_interop,
                                mc_signature=mc_signature_interop)

        # Get the transaction from the mempool
        response = allTransactions(sc_node, True)
        logging.info("response {}".format(response))
        native_input = response['transactions'][0]['data']

        # Estimate gas. The result will be compared with the actual used gas
        exp_gas = proxy_contract.estimate_gas(evm_address_interop, 2, native_contract_address,
                                              0, native_input)

        # Check callTrace
        trace_response = proxy_contract.do_call_trace(evm_address_interop, 1, native_contract_address, 0,
                                                      native_input)

        logging.info("trace_result: {}".format(trace_response))
        assert_false("error" in trace_response)
        assert_true("result" in trace_response)
        trace_result = trace_response["result"]

        assert_equal(proxy_contract.contract_address.lower(), trace_result["to"].lower())
        assert_equal(1, len(trace_result["calls"]))
        native_call = trace_result["calls"][0]
        assert_equal("CALL", native_call["type"])
        assert_equal(proxy_contract.contract_address.lower(), native_call["from"].lower())
        assert_equal("0x" + native_contract_address, native_call["to"])
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal("0x" + native_input, native_call["input"])
        assert_false("calls" in native_call)

        tx_hash = proxy_contract.call_transaction(evm_address_interop, 1, native_contract_address,
                                                  0, native_input)
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=evm_address_interop, mc_addr=taddr_interop)

        # Compare estimated gas with actual used gas. They are not equal because, during the tx execution, more gas than
        # actually needed is removed from the gas pool and then refunded. This causes the gas estimation algorithm to
        # overestimate the gas.
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))

        gas_used = int(receipt['result']['gasUsed'], 16)
        estimated_gas = int(exp_gas['result'], 16)
        assert_true(estimated_gas >= gas_used, "Wrong estimated gas")

        gas_used_tracer = int(trace_result['gasUsed'], 16)
        # TODO There is a bug so that the gas_used_tracer is not correct (see JIRA 1446)
        # assert_equal(gas_used, gas_used_tracer, "Wrong gas")

        # Check traceTransaction
        trace_response = sc_node.rpc_debug_traceTransaction(tx_hash, {"tracer": "callTracer"})
        logging.info("rpc_debug_traceTransaction {}".format(trace_response))

        assert_false("error" in trace_response)
        assert_true("result" in trace_response)
        trace_result = trace_response["result"]

        assert_equal(proxy_contract.contract_address.lower(), trace_result["to"].lower())
        assert_equal(1, len(trace_result["calls"]))
        native_call = trace_result["calls"][0]
        assert_equal("CALL", native_call["type"])
        assert_equal(proxy_contract.contract_address.lower(), native_call["from"].lower())
        assert_equal("0x" + native_contract_address, native_call["to"])
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal("0x" + native_input, native_call["input"])
        assert_false("calls" in native_call)

        gas_used_tracer = int(trace_result['gasUsed'], 16)
        # TODO There is a bug so that the gas_used_tracer is not correct (see JIRA 1446)
        # assert_equal(gas_used, gas_used_tracer, "Wrong gas")

        # Test 'removeKeysOwnership(bytes3,bytes32)'
        method = 'removeKeysOwnership(bytes3,bytes32)'

        taddr_interop_bytes = bytes(taddr_interop, 'utf-8')
        native_input = format_eoa(native_contract.raw_encode_call(method, taddr_interop_bytes[0:3],
                                                                  taddr_interop_bytes[3:]))

        # Estimate gas. The result will be compared with the actual used gas
        exp_gas = proxy_contract.estimate_gas(evm_address_interop, 2, native_contract_address,
                                              0, native_input)

        # Check callTrace
        trace_response = proxy_contract.do_call_trace(evm_address_interop, 2, native_contract_address, 0,
                                                      native_input)

        logging.info("trace_result for remove: {}".format(trace_response))
        assert_false("error" in trace_response)
        assert_true("result" in trace_response)
        trace_result = trace_response["result"]

        assert_equal(proxy_contract.contract_address.lower(), trace_result["to"].lower())
        assert_equal(1, len(trace_result["calls"]))
        native_call = trace_result["calls"][0]
        assert_equal("CALL", native_call["type"])
        assert_equal(proxy_contract.contract_address.lower(), native_call["from"].lower())
        assert_equal("0x" + native_contract_address, native_call["to"])
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal("0x" + native_input, native_call["input"])
        assert_false("calls" in native_call)

        tx_hash = proxy_contract.call_transaction(evm_address_interop, 2, native_contract_address,
                                                  0, native_input)
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=evm_address_interop, mc_addr=taddr_interop, evt_op="remove")

        # Compare estimated gas with actual used gas. They are not equal because, during the tx execution, more gas than
        # actually needed is removed from the gas pool and then refunded. This causes the gas estimation algorithm to
        # overestimate the gas.
        receipt = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))

        gas_used = int(receipt['result']['gasUsed'], 16)
        estimated_gas = int(exp_gas['result'], 16)
        assert_true(estimated_gas >= gas_used, "Wrong estimated gas")

        gas_used_tracer = int(trace_result['gasUsed'], 16)
        # TODO There is a bug so that the gas_used_tracer is not correct (see JIRA 1446)
        # assert_equal(gas_used, gas_used_tracer, "Wrong gas")

        # Check traceTransaction
        trace_response = sc_node.rpc_debug_traceTransaction(tx_hash, {"tracer": "callTracer"})
        logging.info(trace_response)

        assert_false("error" in trace_response)
        assert_true("result" in trace_response)
        trace_result = trace_response["result"]

        assert_equal(proxy_contract.contract_address.lower(), trace_result["to"].lower())
        assert_equal(1, len(trace_result["calls"]))
        native_call = trace_result["calls"][0]
        assert_equal("CALL", native_call["type"])
        assert_equal(proxy_contract.contract_address.lower(), native_call["from"].lower())
        assert_equal("0x" + native_contract_address, native_call["to"])
        assert_true(int(native_call["gas"], 16) > 0)
        assert_true(int(native_call["gasUsed"], 16) > 0)
        assert_equal("0x" + native_input, native_call["input"])
        assert_false("calls" in native_call)

        gas_used_tracer = int(trace_result['gasUsed'], 16)
        # TODO There is a bug so that the gas_used_tracer is not correct (see JIRA 1446)
        # assert_equal(gas_used, gas_used_tracer, "Wrong gas")


if __name__ == "__main__":
    SCEvmMcAddressOwnership().main()

