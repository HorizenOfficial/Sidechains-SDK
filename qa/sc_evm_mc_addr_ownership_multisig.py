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
from SidechainTestFramework.account.httpCalls.transaction.getKeysOwnership import getKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.removeKeysOwnership import removeKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.sendKeysOwnership import sendKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.sendMultisigKeysOwnership import sendMultisigKeysOwnership
from SidechainTestFramework.account.simple_proxy_contract import SimpleProxyContract
from SidechainTestFramework.account.utils import MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, \
    INTEROPERABILITY_FORK_EPOCH, ZENDAO_FORK_EPOCH
from SidechainTestFramework.scutil import generate_next_block, EVM_APP_SLOT_TIME
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


class SCEvmMcMultisigAddressOwnership(AccountChainSetup):
    def __init__(self):
        super().__init__(block_timestamp_rewind=1500 * EVM_APP_SLOT_TIME * INTEROPERABILITY_FORK_EPOCH,
                         number_of_sidechain_nodes=2)

    def run_test(self):
        ft_amount_in_zen = Decimal('500.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]

        sc_address = remove_0x_prefix(self.evm_address)
        sc_address_checksum_fmt = to_checksum_address(self.evm_address)

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

        # reach the ZenDao fork
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        for i in range(0, ZENDAO_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        addresses = []
        addr_objects = []
        mc_signatures = []
        rpc_array_param = []

        NUM_OF_ADDR_IN_MULTISIG = 3
        THRESHOLD_SIGNATURE_VALUE = 2
        assert_true(THRESHOLD_SIGNATURE_VALUE <= NUM_OF_ADDR_IN_MULTISIG)

        # Generate some new address and prepare rpc command input
        for i in range(NUM_OF_ADDR_IN_MULTISIG):
            addresses.append(mc_node.getnewaddress())
            addr_objects.append(mc_node.validateaddress(addresses[i]))
            rpc_array_param.append(addr_objects[i]['pubkey'])

        # Create the multi-sig address as combination of the single addresses created above
        m_sig_obj = mc_node.createmultisig(THRESHOLD_SIGNATURE_VALUE, rpc_array_param)
        mc_multisig_address_1 = m_sig_obj["address"]
        redeemScript_1 = m_sig_obj["redeemScript"]

        print("  val mcMultiSigAddr1: String = \"{}\"".format(mc_multisig_address_1))
        print("  val redeemScript1: String = \"{}\"".format(redeemScript_1))

        for i in range(NUM_OF_ADDR_IN_MULTISIG):
            if i <= THRESHOLD_SIGNATURE_VALUE:
                mc_signatures.append(mc_node.signmessage(addresses[i], mc_multisig_address_1 + sc_address_checksum_fmt))
                print("  val mcAddrStr: String = \"{}\"".format(addresses[i]))
                print("  val mcSignatureStr: String = \"{}\"".format(mc_signatures[i]))

        ret = sendMultisigKeysOwnership(sc_node,
                                        sc_address=sc_address,
                                        mc_addr=mc_multisig_address_1,
                                        mc_signatures=mc_signatures,
                                        redeemScript=redeemScript_1)


        tx_hash = ret['transactionId']
        self.sc_sync_all()

        # check the tx adding an ownership is included in the block and the receipt is succesful
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=mc_multisig_address_1)

        # use an uncompressed pub key for generating a multisig address
        addresses = []
        mc_signatures = []
        rpc_array_param = []

        # a priv key with the corrisponding taddr and uncompressed pub key. Thos test vectors are used for creating a redeem script
        # containing this pub key format
        priv_key_unc = "cUHcJUdzjNceFfUhLQtrrbjPtHo5NBP9ZofNxdSFnQqXijao761c"
        addr_unc = "ztTw2K532ewo9gynBJv7FFUgbD19Wpifv8G"
        uncomp_pubkey = "049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d4023"

        # import the key in wallet so that we can sign
        mc_node.importprivkey(priv_key_unc)

        addresses.append(addr_unc)
        rpc_array_param.append(uncomp_pubkey)

        # Generate some new address and prepare rpc command input
        for i in range(NUM_OF_ADDR_IN_MULTISIG - 1):
            addresses.append(mc_node.getnewaddress())
            rpc_array_param.append(mc_node.validateaddress(addresses[i + 1])['pubkey'])

        # Create the multi-sig address as combination of the single addresses created above
        m_sig_obj = mc_node.createmultisig(THRESHOLD_SIGNATURE_VALUE, rpc_array_param)
        mc_multisig_address_2 = m_sig_obj["address"]
        redeemScript_2 = m_sig_obj["redeemScript"]


        for i in range(NUM_OF_ADDR_IN_MULTISIG):
            if i <= THRESHOLD_SIGNATURE_VALUE:
                mc_signatures.append(mc_node.signmessage(addresses[i], mc_multisig_address_2 + sc_address_checksum_fmt))


        print("  val mcMultiSigAddr2: String = \"{}\"".format(mc_multisig_address_2))
        print("  val redeemScript2: String = \"{}\"".format(redeemScript_2))

        for i in range(NUM_OF_ADDR_IN_MULTISIG):
            if i <= THRESHOLD_SIGNATURE_VALUE:
                mc_signatures.append(mc_node.signmessage(addresses[i], mc_multisig_address_2 + sc_address_checksum_fmt))
                print("  val mcAddrStr: String = \"{}\"".format(addresses[i]))
                print("  val mcSignatureStr: String = \"{}\"".format(mc_signatures[i]))

        ret = sendMultisigKeysOwnership(sc_node,
                                        sc_address=sc_address,
                                        mc_addr=mc_multisig_address_2,
                                        mc_signatures=mc_signatures,
                                        redeemScript=redeemScript_2)

        tx_hash = ret['transactionId']
        self.sc_sync_all()

        # check the tx adding an ownership is included in the block and the receipt is succesful
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=mc_multisig_address_2)

        # add an ordinary owned mc address linked to the same sc address, and use a different sc address format, we should
        # support that
        taddr_1 = mc_node.getnewaddress()
        mc_signature_taddr_1 = mc_node.signmessage(taddr_1, sc_address_checksum_fmt)

        ret = sendKeysOwnership(sc_node,
                                sc_address=to_checksum_address(sc_address),
                                mc_addr=taddr_1,
                                mc_signature=mc_signature_taddr_1)

        tx_hash = ret['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=taddr_1)

        # check we have both association and only them (and we support different sc address formats)
        ret = getKeysOwnership(sc_node, sc_address=sc_address)
        ret2 = getKeysOwnership(sc_node, sc_address=to_checksum_address(sc_address))
        assert_equal(ret, ret2)

        # check we have just one sc address association
        assert_true(len(ret['keysOwnership']) == 1)
        # check we have two mc address associated to sc address
        assert_true(len(ret['keysOwnership'][sc_address_checksum_fmt]) == 3)
        # check we have exactly the expected mc address
        assert_true(mc_multisig_address_1 in ret['keysOwnership'][sc_address_checksum_fmt])
        assert_true(mc_multisig_address_2 in ret['keysOwnership'][sc_address_checksum_fmt])
        assert_true(taddr_1 in ret['keysOwnership'][sc_address_checksum_fmt])

        ret = removeKeysOwnership(sc_node,
                                  sc_address=sc_address,
                                  mc_addr=taddr_1)

        tx_hash = ret['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=taddr_1, evt_op="remove")

        ret = getKeysOwnership(sc_node, sc_address=sc_address)

        # check we have just one sc address association
        assert_true(len(ret['keysOwnership']) == 1)
        # check we have only one mc address associated to sc address
        assert_true(len(ret['keysOwnership'][sc_address_checksum_fmt]) == 2)
        # check we have exactly that mc address
        assert_true(mc_multisig_address_1 in ret['keysOwnership'][sc_address_checksum_fmt])
        assert_true(mc_multisig_address_2 in ret['keysOwnership'][sc_address_checksum_fmt])
        assert_true(taddr_1 not in ret['keysOwnership'][sc_address_checksum_fmt])

        removeKeysOwnership(sc_node,
                            sc_address=to_checksum_address(sc_address),
                            mc_addr=mc_multisig_address_1)

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        list_associations_sc_address = getKeysOwnership(sc_node, sc_address=sc_address)

        # check we have just one sc address association
        assert_true(len(list_associations_sc_address['keysOwnership']) == 1)
        # check we have the expected number
        assert_true(len(list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt]) == 1)
        assert_true(mc_multisig_address_2 in list_associations_sc_address['keysOwnership'][sc_address_checksum_fmt])

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

        list_all_associations = getKeysOwnership(sc_node)
        pprint.pprint(list_all_associations)

        # check we have two sc address associations
        assert_equal(2, len(list_all_associations['keysOwnership']))
        # check we have the expected numbers
        assert_equal(1, len(list_all_associations['keysOwnership'][sc_address_checksum_fmt]))
        assert_equal(1, len(list_all_associations['keysOwnership'][sc_address2_checksum_fmt]))
        assert_true(taddr_sc2_1 in list_all_associations['keysOwnership'][sc_address2_checksum_fmt])

        # negative cases
        # 1. try to use invalid parameters
        # 1.1 illegal sc address
        invalid_sc_addr = "1234h"
        try:
            sendMultisigKeysOwnership(sc_node,
                                      sc_address=invalid_sc_addr,
                                      mc_addr=mc_multisig_address_2,
                                      mc_signatures=mc_signatures,
                                      redeemScript=m_sig_obj["redeemScript"])
        except Exception as err:
            print("Expected exception thrown: {}".format(str(err)))
            assert_true("Account " + invalid_sc_addr + " is invalid " in str(err))
        else:
            fail("invalid sc address should not work")

        # 1.2 illegal mc address
        try:
            sendMultisigKeysOwnership(sc_node,
                                      sc_address=sc_address,
                                      mc_addr="1LMcKyPmwebfygoeZP8E9jAMS2BcgH3Yip",
                                      mc_signatures=mc_signatures,
                                      redeemScript=m_sig_obj["redeemScript"])
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(str(err)))
            assert_true("Invalid input parameters" in str(err))
        else:
            fail("invalid mc address should not work")

        # 1.3 illegal mc signature
        mc_signatures.append("xyz")
        try:
            sendMultisigKeysOwnership(sc_node,
                                      sc_address=sc_address,
                                      mc_addr=mc_multisig_address_2,
                                      mc_signatures=mc_signatures,
                                      redeemScript=m_sig_obj["redeemScript"])
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(str(err)))
            assert_true("Invalid input parameters" in str(err))
        else:
            fail("invalid mc signature should not work")

        # 2. Try to use a number of signatures minor that the threshold value
        # Generate some new address and prepare rpc command input
        addresses = []
        addr_objects = []
        mc_signatures_bad = []
        rpc_array_param = []

        for i in range(NUM_OF_ADDR_IN_MULTISIG):
            addresses.append(mc_node.getnewaddress())
            addr_objects.append(mc_node.validateaddress(addresses[i]))
            rpc_array_param.append(addr_objects[i]['pubkey'])

        # Create the multi-sig address as combination of the single addresses created above
        m_sig_obj = mc_node.createmultisig(THRESHOLD_SIGNATURE_VALUE, rpc_array_param)
        mc_multisig_address_3 = m_sig_obj["address"]
        redeem_script_2 = m_sig_obj["redeemScript"]

        mc_signatures_bad.append(mc_node.signmessage(addresses[0], mc_multisig_address_3 + sc_address_checksum_fmt))

        ret = sendMultisigKeysOwnership(sc_node,
                                        sc_address=sc_address,
                                        mc_addr=mc_multisig_address_3,
                                        mc_signatures=mc_signatures_bad,
                                        redeemScript=redeem_script_2)

        tx_hash = ret['transactionId']
        self.sc_sync_all()

        # check the tx adding an ownership is included in the block and the receipt is failed, since we have not reached
        # the threashold signature value
        forge_and_check_receipt(self, sc_node, tx_hash, expected_receipt_status=0, sc_addr=sc_address,
                                mc_addr=mc_multisig_address_3)

        # 3. Try using twice the same signature
        mc_signatures_bad.append(mc_node.signmessage(addresses[0], mc_multisig_address_3 + sc_address_checksum_fmt))

        ret = sendMultisigKeysOwnership(sc_node,
                                        sc_address=sc_address,
                                        mc_addr=mc_multisig_address_3,
                                        mc_signatures=mc_signatures_bad,
                                        redeemScript=redeem_script_2)

        tx_hash = ret['transactionId']
        self.sc_sync_all()

        # check the tx adding an ownership is included in the block and the receipt is failed, since we have reached
        # the threashold signature value with a repeated signature
        forge_and_check_receipt(self, sc_node, tx_hash, expected_receipt_status=0, sc_addr=sc_address,
                                mc_addr=mc_multisig_address_2)

        native_contract_address = MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS

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
        # we have an offset of 64 bytes and 2 records with 3 chunks of 32 bytes
        exp_len = 32 + 32 + 2 * (3 * 32)
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
        # we have an offset of 64 bytes and 1 records with 3 chunks of 32 bytes
        exp_len = 32 + 32 + 1 * (3 * 32)
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

        # generate a multisig address with the max number of pub keys
        addresses = []
        addr_objects = []
        mc_signatures = []
        rpc_array_param = []

        NUM_OF_ADDR_IN_MULTISIG = 15
        THRESHOLD_SIGNATURE_VALUE = 15
        assert_true(THRESHOLD_SIGNATURE_VALUE <= NUM_OF_ADDR_IN_MULTISIG)

        # Generate some new address and prepare rpc command input
        for i in range(NUM_OF_ADDR_IN_MULTISIG):
            addresses.append(mc_node.getnewaddress())
            addr_objects.append(mc_node.validateaddress(addresses[i]))
            rpc_array_param.append(addr_objects[i]['pubkey'])

        # Create the multi-sig address as combination of the single addresses created above
        m_sig_obj = mc_node.createmultisig(THRESHOLD_SIGNATURE_VALUE, rpc_array_param)
        mc_multisig_address_1 = m_sig_obj["address"]
        redeemScript_1 = m_sig_obj["redeemScript"]

        for i in range(NUM_OF_ADDR_IN_MULTISIG):
            if i < THRESHOLD_SIGNATURE_VALUE:
                mc_signatures.append(mc_node.signmessage(addresses[i], mc_multisig_address_1 + sc_address_checksum_fmt))

        ret = sendMultisigKeysOwnership(sc_node,
                                        sc_address=sc_address,
                                        mc_addr=mc_multisig_address_1,
                                        mc_signatures=mc_signatures,
                                        redeemScript=redeemScript_1)


        tx_hash = ret['transactionId']
        self.sc_sync_all()

        # check the tx adding an ownership is included in the block and the receipt is succesful
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=mc_multisig_address_1)


if __name__ == "__main__":
    SCEvmMcMultisigAddressOwnership().main()
