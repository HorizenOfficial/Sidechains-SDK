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

    abi_list = abi_return_value[end_offset:]
    start_offset = 0

    sc_associations_dict = {}
    for i in range(list_size):
        # read offset of the i-dynamic struct
        dyn_str_offset = (decode(['uint32'], hex_str_to_bytes(abi_list[start_offset:start_offset + 64]))[0])*2
        start_offset = start_offset + 64
        (address_pref, mc_addr) = decode(['address', 'string'], hex_str_to_bytes(abi_list[dyn_str_offset:dyn_str_offset+(5*64)]))

        sc_address_checksum_fmt = to_checksum_address(address_pref)
        print("sc addr=" + sc_address_checksum_fmt)
        if sc_address_checksum_fmt in sc_associations_dict:
            mc_addr_list = sc_associations_dict.get(sc_address_checksum_fmt)
        else:
            sc_associations_dict[sc_address_checksum_fmt] = []
            mc_addr_list = []
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

        # check the balance of native smart contract is null
        nsc_bal = int(
            sc_node.rpc_eth_getBalance(format_evm(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS), 'latest')['result'], 16)
        assert_equal(nsc_bal, 0)

        # send funds to native smart contract before the zendao fork is reached
        eoa_nsc_amount = 123456
        tx_hash_eoa = createLegacyEIP155Transaction(sc_node2,
                                                    fromAddress=sc_address2,
                                                    toAddress=MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS,
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
        assert_true(status == 1)
        gas_used = int(receipt['result']['gasUsed'], 16)
        assert_equal(gas_used, 21000)

        # check the address has the expected balance
        nsc_bal = int(
            sc_node.rpc_eth_getBalance(format_evm(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS), 'latest')['result'], 16)
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

        # reach the fork
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestEpochNumber"]

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

        # add a second owned mc address linked to the same sc address
        ret = sendKeysOwnership(sc_node, nonce=2,
                                sc_address=sc_address,
                                mc_addr=taddr2,
                                mc_signature=mc_signature2)

        tx_hash = ret['transactionId']
        forge_and_check_receipt(self, sc_node, tx_hash, sc_addr=sc_address, mc_addr=taddr2)

        # check we have both association and only them
        ret = getKeysOwnership(sc_node, sc_address=sc_address)

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
        # 1. try to add the an ownership already there
        try:
            sendKeysOwnership(sc_node,
                              sc_address=sc_address,
                              mc_addr=taddr1,
                              mc_signature=mc_signature1)
        except RuntimeError as err:
            print("Expected exception thrown: {}".format(err))
            assert_true("already linked" in str(err))
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

        # remove an mc addr and check we have 11 of them
        taddr_rem = taddr_list[4]

        assert_true(len(taddr_list) == 10)
        taddr_list.remove(taddr_rem)
        assert_true(len(taddr_list) == 9)

        removeKeysOwnership(sc_node, nonce=16,
                            sc_address=sc_address,
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

        ret = sendKeysOwnership(sc_node2,
                                sc_address=sc_address2,
                                mc_addr=taddr1,
                                mc_signature=mc_signature_sc2)
        self.sc_sync_all()
        tx_hash = ret['transactionId']
        # check the receipt has a status failed
        forge_and_check_receipt(self, sc_node, tx_hash, expected_receipt_status=0)

        list_all_associations = getKeysOwnership(sc_node)
        pprint.pprint(list_all_associations)

        # check we have two sc address associations
        assert_true(len(list_all_associations['keysOwnership']) == 2)
        # check we have the expected numbers
        assert_true(len(list_all_associations['keysOwnership'][sc_address_checksum_fmt]) == 11)
        assert_true(len(list_all_associations['keysOwnership'][sc_address2_checksum_fmt]) == 1)
        assert_true(taddr_sc2_1 in list_all_associations['keysOwnership'][sc_address2_checksum_fmt])

        # execute native smart contract for getting all associations
        method = 'getAllKeyOwnerships()'
        abi_str = function_signature_to_4byte_selector(method)
        req = {
            "from": format_evm(sc_address),
            "to": format_evm(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS),
            "nonce": 3,
            "gasLimit": 2300000,
            "gasPrice": 850000000,
            "value": 0,
            "data": encode_hex(abi_str)
        }
        response = sc_node2.rpc_eth_call(req, 'latest')
        abi_return_value = remove_0x_prefix(response['result'])
        print(abi_return_value)
        resultStringLength = len(abi_return_value)
        # we have:
        #  - an offset of 64 bytes (byte alignment and list size)
        #  - 12 offsets (32 bytes) pointing to the dynamic structs
        #  - 12 dynamic structs:
        #     - offset (32 bytes) pointing to dynamic part (string)
        #     - address (20 bytes padded to 32)
        #     - string length (padded to 32)
        #     - 35 string bytes (2 * 32 bytes chunks)
        exp_len = (32 + 32) + 12 * 32 + 12*(5 * 32)
        assert_equal(resultStringLength, 2*exp_len)

        check_get_key_ownership(abi_return_value, list_all_associations['keysOwnership'])

        # execute native smart contract for getting sc address associations
        method = 'getKeyOwnerships(address)'
        abi_str = function_signature_to_4byte_selector(method)
        addr_padded_str = "000000000000000000000000" + sc_address
        req = {
            "from": format_evm(sc_address),
            "to": format_evm(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS),
            "nonce": 3,
            "gasLimit": 2300000,
            "gasPrice": 850000000,
            "value": 0,
            "data": encode_hex(abi_str) + addr_padded_str
        }
        response = sc_node2.rpc_eth_call(req, 'latest')
        abi_return_value = remove_0x_prefix(response['result'])
        print(abi_return_value)
        resultStringLength = len(abi_return_value)
        # we have:
        #  - an offset of 64 bytes (byte alignment and list size)
        #  - 11 offset (32 bytes) pointing to the dynamic structs
        #  - 11 dynamic structs:
        #     - offset (32 bytes) pointing to dynamic part (string)
        #     - address (20 bytes padded to 32)
        #     - string length (padded to 32)
        #     - 35 string bytes (2 * 32 bytes chunks)
        exp_len = (32 + 32) + 11 * 32 + 11*(5 * 32)
        assert_equal(resultStringLength, 2 * exp_len)

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
                                to_address=to_checksum_address(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS),
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


if __name__ == "__main__":
    SCEvmMcAddressOwnership().main()
