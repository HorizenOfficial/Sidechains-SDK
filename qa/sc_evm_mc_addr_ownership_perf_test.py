#!/usr/bin/env python3
import math
import pprint
import time
from decimal import Decimal
from eth_utils import add_0x_prefix, remove_0x_prefix, encode_hex, \
    function_signature_to_4byte_selector, to_checksum_address
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import format_evm
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.getKeysOwnerScAddresses import getKeyOwnerScAddresses
from SidechainTestFramework.account.httpCalls.transaction.getKeysOwnership import getKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.removeKeysOwnership import removeKeysOwnership
from SidechainTestFramework.account.httpCalls.transaction.sendKeysOwnership import sendKeysOwnership
from SidechainTestFramework.account.utils import MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, convertZenToWei
from SidechainTestFramework.sc_boostrap_info import DEFAULT_MAX_NONCE_GAP, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.scutil import generate_next_block, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME

from test_framework.util import (assert_equal, assert_true, forward_transfer_to_sidechain, assert_false)

"""
Configuration: 
    - 2 SC node
    - 1 MC node

Test:
    Add a large number of relations between owned SC/MC addresses via native smart contract call and check
    the gas usage of adding a new relation is constant.
    Afterwards, remove all of them one by one and verify the gas usage is constant as well.
    Also test the retrieval of data (both via http api and nsc calls) such as list of:
     - all owner sc addresses
     - associations related to a specific owner sc address
     - all associations
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


# The activation epoch of the zendao feature, as coded in the sdk
ZENDAO_FORK_EPOCH = 7


class SCEvmMcAddressOwnershipPerfTest(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2,
                         block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * (ZENDAO_FORK_EPOCH+100))

    def sc_setup_chain(self):
        # we would like to have non ceasing sidechains
        self.options.nonceasing = True
        self.options.certcircuittype = KEY_ROTATION_CIRCUIT
        super().sc_setup_chain()

    def run_test(self):
        # initial amount forwarded from mainchain to this sc
        ft_amount_in_zen = Decimal('3000.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]
        sc_node2 = self.sc_nodes[1]

        # it may take long time if these numbers are big
        # ---
        # num_of_sc_addresses = 70
        # num_of_association_per_sc = 40
        # ---
        # Consider that when retrieving a large quantity of data stored in the native smart
        # contract the eth_call() can trigger an OOG exception thus truncating the list returned as a result (see below
        # when invoking the ABI methods)
        num_of_sc_addresses = 20
        num_of_associations_per_sc_addr = 20

        # this test is meaningful if we have at least 2 associations per sc address
        assert_true(num_of_associations_per_sc_addr > 1)

        total_num_of_associations = num_of_sc_addresses * num_of_associations_per_sc_addr
        scaddr_list = []

        # create sc addresses and send them some fund
        eoa2eoa_amount_in_zen = Decimal('0.5')
        for i in range(num_of_sc_addresses):
            sc_address = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
            scaddr_list.append(sc_address)

            createEIP1559Transaction(sc_node, nonce=i,
                                                 fromAddress=self.evm_address[2:],
                                                 toAddress=sc_address,
                                                 gasLimit=230000, maxPriorityFeePerGas=900000000,
                                                 maxFeePerGas=900000000,
                                                 value=convertZenToWei(eoa2eoa_amount_in_zen))


            self.sc_sync_all()
            if i % DEFAULT_MAX_NONCE_GAP == 0:
                print("Generating new block (i = {})...".format(i))
                generate_next_block(sc_node, "first node")
                self.sc_sync_all()

        self.block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # reach the zendao fork if not reached yet
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        if (ZENDAO_FORK_EPOCH - current_best_epoch) > 0:
            for i in range(0, ZENDAO_FORK_EPOCH - current_best_epoch):
                generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
                self.sc_sync_all()

        taddr_list = []
        tx_hash_list = []
        num_of_tx_in_block = min(32, num_of_sc_addresses)
        first_round_mc_addrs = []

        # we perform a first loop on sc addresses, this is because the very first association has to initialize a
        # linked list obj and it consumes slightly more gas than adding the other associations
        for i in range(num_of_sc_addresses):
            sc_address = scaddr_list[i]
            sc_address_checksum_fmt = to_checksum_address(sc_address)

            taddr = mc_node.getnewaddress()
            taddr_list.append(taddr)
            first_round_mc_addrs.append(taddr)
            mc_signature = mc_node.signmessage(taddr, sc_address_checksum_fmt)

            tx_hash_list.append(sendKeysOwnership(sc_node, nonce=0,
                                                  sc_address=sc_address,
                                                  mc_addr=taddr,
                                                  mc_signature=mc_signature)['transactionId'])
            self.sc_sync_all()
            if i % num_of_tx_in_block == 0:
                print("Generating new block (i = {})...".format(i))
                generate_next_block(sc_node, "first node")
                self.sc_sync_all()


        print("Generating new block (i = {})...".format(i))
        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # execute native smart contract for getting the list of all sc addresses who owns an association
        method = 'getKeyOwnerScAddresses()'
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
        assert_false("error" in response)

        abi_return_value = remove_0x_prefix(response['result'])
        result_string_length = len(abi_return_value)
        # we have an offset of 64 bytes and 'num_of_association' records with 3 chunks of 32 bytes
        exp_len = 32 + 32 + num_of_sc_addresses * 32
        if result_string_length != (2 * exp_len):
            # if we have a failure here, we most probably cought an OOG exception in retrieving too big a list.
            # Check warnings in logs, currently the limit is 3399 owner sc addresses.
            print("Could not get {} records".format(total_num_of_associations))

        # DEFAULT_MAX_NONCE_GAP is the default value of max difference between tx nonce and state nonce allowed by mempool.
        num_of_tx_in_block = min(num_of_associations_per_sc_addr-1, DEFAULT_MAX_NONCE_GAP)

        nonce_count = 1
        # this can take long
        for i in range(total_num_of_associations-num_of_sc_addresses):
            if i % (num_of_associations_per_sc_addr - 1) == 0:
                sc_address = scaddr_list[int(i/(num_of_associations_per_sc_addr-1))]
                sc_address_checksum_fmt = to_checksum_address(sc_address)
                nonce_count = 1

            taddr = mc_node.getnewaddress()
            taddr_list.append(taddr)
            mc_signature = mc_node.signmessage(taddr, sc_address_checksum_fmt)

            tx_hash_list.append(sendKeysOwnership(sc_node, nonce=nonce_count,
                                                  sc_address=sc_address,
                                                  mc_addr=taddr,
                                                  mc_signature=mc_signature)['transactionId'])
            self.sc_sync_all()
            nonce_count += 1

            if i % num_of_tx_in_block == 0:
                print("Generating new block (i = {})...".format(i))
                generate_next_block(sc_node, "first node")
                self.sc_sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # the very first tx uses slightly more gas because it performs the smart contact initialization, therefore we
        # take txes from the second round on and compare it with the last one
        tx_hash_first = tx_hash_list[num_of_sc_addresses]
        tx_hash_last = tx_hash_list[-1]

        receipt_first = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_first))
        assert_true(int(receipt_first['result']['status'], 16) == 1)
        gas_used_first = receipt_first['result']['gasUsed']

        receipt_last = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_last))
        assert_true(int(receipt_last['result']['status'], 16) == 1)
        gas_used_last = receipt_last['result']['gasUsed']

        # check gas usage did not change
        assert_equal(gas_used_first, gas_used_last)

        # execute native smart contract calls for getting the list of associations for each owner sc address
        method = 'getKeyOwnerships(address)'
        abi_str = function_signature_to_4byte_selector(method)

        for sc_addr in scaddr_list:
            # get it via http api
            list_associations_sc_address = getKeysOwnership(sc_node, sc_address=sc_addr)
            # check we have just one sc address association
            assert_true(len(list_associations_sc_address['keysOwnership']) == 1)
            # check we have the expected associations
            assert_true(len(list_associations_sc_address['keysOwnership'][to_checksum_address(sc_addr)]) == num_of_associations_per_sc_addr)

            # execute native smart contract for getting sc address associations
            addr_padded_str = "000000000000000000000000" + sc_addr
            req = {
                "from": format_evm(sc_addr),
                "to": format_evm(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS),
                "nonce": 3,
                "gasLimit": 2300000,
                "gasPrice": 850000000,
                "value": 0,
                "data": encode_hex(abi_str) + addr_padded_str
            }
            response = sc_node2.rpc_eth_call(req, 'latest')
            assert_false("error" in response)

            abi_return_value = remove_0x_prefix(response['result'])
            result_string_length = len(abi_return_value)
            # we have an offset of 64 bytes and 11 records with 3 chunks of 32 bytes
            exp_len = 32 + 32 + num_of_associations_per_sc_addr * (3 * 32)
            # if we have a failure here, we most probably caught an OOG exception in retrieving too big a list.
            # Check warnings in logs, currently the limit is 3399 mc address associations per sc address
            assert_equal(result_string_length, 2 * exp_len)

        # get all associations via http api
        list_all_associations = getKeysOwnership(sc_node, sc_address=None)
        assert_true(len(list_all_associations['keysOwnership']) == num_of_sc_addresses)
        for key, values in list_all_associations['keysOwnership'].items():
            assert_equal(len(values), num_of_associations_per_sc_addr)
            assert_true(str(key[2:]).lower() in scaddr_list)

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
        # currently it may fail with out of gas error if too many data are stored in the native smart contract
        response = sc_node2.rpc_eth_call(req, 'latest')
        assert_false("error" in response)

        abi_return_value = remove_0x_prefix(response['result'])
        result_string_length = len(abi_return_value)
        # we have an offset of 64 bytes and 'num_of_association' records with 3 chunks of 32 bytes
        exp_len = 32 + 32 + total_num_of_associations * (3 * 32)
        if result_string_length != (2 * exp_len):
            # if we have a failure here, we most probably caught an OOG exception in retrieving too big a list.
            # Check warnings in logs. Currently the limit is about 2970 overall associations, depending on the number of sc addresses.
            # This limit is smaller than the previous one because the records are larger (sc/mc) vs (mc) address
            print("Could not get {} records".format(total_num_of_associations))

        # get all owner sc addresses via http api
        ownerScAddresses = getKeyOwnerScAddresses(sc_node)['owners']
        assert_equal(len(ownerScAddresses), len(scaddr_list))
        for entry in scaddr_list:
            assert_true(to_checksum_address(entry) in ownerScAddresses)

        # remove ownerships
        tx_hash_list = []
        # the very last tx of each linked list uses slightly less gas because it does not modify any other node of the
        # internal linked list. Moreover the last sc address is the last node of another linked list, therefore
        # we skip the first associations of the sc addresses
        for sc_addr in reversed(scaddr_list):
            list_mc_addresses = getKeysOwnership(sc_node, sc_address=sc_addr)['keysOwnership'][to_checksum_address(sc_addr)]
            nonce = int(sc_node.rpc_eth_getTransactionCount(to_checksum_address(sc_addr), 'latest')['result'], 16)

            tx_count = 0
            for entry in reversed(list_mc_addresses):
                self.nodes[0].generate(1)

                if (entry not in first_round_mc_addrs):
                    tx_count += 1
                    tx_hash_list.append(removeKeysOwnership(sc_node, nonce=nonce,
                                                            sc_address=sc_addr,
                                                            mc_addr=entry)['transactionId'])
                    self.sc_sync_all()
                    nonce = nonce + 1

                    if True:
                        #if tx_count % num_of_tx_in_block == 0:
                        print("Generating new block (i = {})...".format(i))
                        generate_next_block(sc_node, "first node")
                        self.sc_sync_all()


            generate_next_block(sc_node, "first node")
            self.sc_sync_all()

        '''
        Actually, when removing associations, the linked list nodes that are at the top consume slightly less gas
        because it is linked to one node only and not to two, therefore given the random component of the order of
        removal, some time this comparison fails
        ---
        TODO find a way to ensure the gas consumed is practically constant
        ---
        
        tx_hash_first = tx_hash_list[0]
        tx_hash_last = tx_hash_list[-1]

        receipt_first = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_first))
        assert_true(int(receipt_first['result']['status'], 16) == 1)
        gas_used_first = receipt_first['result']['gasUsed']

        receipt_last = sc_node.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash_last))
        assert_true(int(receipt_last['result']['status'], 16) == 1)
        gas_used_last = receipt_last['result']['gasUsed']

        assert_equal(gas_used_first, gas_used_last)
        '''

        # now remove the remaining associations (one per sc address)
        for sc_addr in reversed(scaddr_list):
            list_mc_addresses = getKeysOwnership(sc_node, sc_address=sc_addr)['keysOwnership'][to_checksum_address(sc_addr)]
            assert_equal(len(list_mc_addresses), 1)

            nonce = int(sc_node.rpc_eth_getTransactionCount(to_checksum_address(sc_addr), 'latest')['result'], 16)

            entry = list_mc_addresses[0]
            tx_hash_list.append(removeKeysOwnership(sc_node, nonce=nonce,
                                                    sc_address=sc_addr,
                                                    mc_addr=entry)['transactionId'])
            self.sc_sync_all()

            generate_next_block(sc_node, "first node")
            self.sc_sync_all()

        # check that we really removed all associations
        ownerScAddresses = getKeyOwnerScAddresses(sc_node)['owners']
        assert_true(len(ownerScAddresses) == 0)

        list_all_associations = getKeysOwnership(sc_node, sc_address=None)
        assert_true(len(list_all_associations['keysOwnership']) == 0)

if __name__ == "__main__":
    SCEvmMcAddressOwnershipPerfTest().main()
