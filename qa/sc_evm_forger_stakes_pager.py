#!/usr/bin/env python3
import json
import logging
from binascii import hexlify
from decimal import Decimal

from eth_abi import decode
from eth_utils import function_signature_to_4byte_selector, encode_hex, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake, contract_function_call, format_evm
from SidechainTestFramework.account.utils import convertZenToZennies, VERSION_1_3_FORK_EPOCH, \
    FORGER_STAKE_SMART_CONTRACT_ADDRESS
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain, hex_str_to_bytes
)

"""
Configuration: 
    - 3 SC nodes connected with each other
    - 1 MC node
    - SC1 node owns a stakeAmount made out of cross chain creation output

Test:
    - xxx


"""
NUM_OF_STAKES = 51
NUM_OF_STAKE_TXES = 50


def get_forger_stake_amount_from_abi(abi_return_value):
    # each byte is 2 ascii chars,therefore when dealing with offsets we multiply bytes by 2
    start_offset = 0

    # first record (32 bytes) is the next array position in the paging strategy
    end_offset = 2 * 32
    next_pos = decode(['int32'], hex_str_to_bytes(abi_return_value[start_offset:end_offset]))[0]

    # next we have the offset at which dynamic data start, should be 64 (0x40)
    start_offset = end_offset
    end_offset = end_offset + 2 * 32
    dynamic_data_start_offset = decode(['uint32'], hex_str_to_bytes(abi_return_value[start_offset:end_offset]))[0]
    assert_equal(dynamic_data_start_offset, 64)

    # size of the dynamic array of stakes
    start_offset = end_offset
    end_offset = end_offset + 2 * 32  # read 32 bytes
    list_size = decode(['uint32'], hex_str_to_bytes(abi_return_value[start_offset:end_offset]))[0]

    list_result = []
    for i in range(list_size):
        entry_dict = {}

        start_offset = end_offset
        end_offset = end_offset + 2 * 6 * 32  # read 6 32-bytes-long chunks of data
        (stake_id_bytes,
         value,
         address,
         block_sign_pub_key_bytes,
         vrf32_bytes,
         vrf1_bytes
         ) = decode(['bytes32', 'uint256', 'address', 'bytes32', 'bytes32', 'bytes1'],
                    hex_str_to_bytes(abi_return_value[start_offset:end_offset]))

        stake_id = hexlify(stake_id_bytes).decode('ascii')
        block_sign_pub_key = hexlify(block_sign_pub_key_bytes).decode('ascii')
        vrf_key = hexlify(vrf32_bytes + vrf1_bytes).decode('ascii')

        # fill an entry exactly as HTTP API would do
        entry_dict['stakeId'] = stake_id
        entry_dict['forgerStakeData'] = {
            'forgerPublicKeys': {
                'blockSignPublicKey': {'publicKey': block_sign_pub_key},
                'vrfPublicKey': {'publicKey': vrf_key}},
            'ownerPublicKey': {'address': remove_0x_prefix(address)},
            'stakedAmount': value
        }

        list_result.append(entry_dict)

    return next_pos, list_result


def padded_32_hex(s):
    return remove_0x_prefix(s).zfill(64)


def get_paged_forging_stakes_via_eth_call(sc_node, from_address, start_pos, page_size):
    # execute native smart contract for getting all associations
    method = 'getPagedForgersStakes(uint32,uint32)'
    abi_str = function_signature_to_4byte_selector(method)
    start_pos = padded_32_hex(hex(start_pos))
    size_padded_str = padded_32_hex(hex(page_size))

    req = {
        "from": format_evm(from_address),
        "to": format_evm(FORGER_STAKE_SMART_CONTRACT_ADDRESS),
        "gasLimit": 2300000,
        "gasPrice": 850000000,
        "value": 0,
        "data": encode_hex(abi_str) + start_pos + size_padded_str
    }
    response = sc_node.rpc_eth_call(req, 'latest')
    if 'error' in response:
        raise RuntimeError("Something went wrong, see {}".format(str(response)))

    abi_return_value = remove_0x_prefix(response['result'])
    # print(abi_return_value)
    return get_forger_stake_amount_from_abi(abi_return_value)


class SCEvmForgerStakesPager(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=3, max_account_slots=NUM_OF_STAKE_TXES,
                         max_nonce_gap=NUM_OF_STAKE_TXES,
                         forward_amount=99, block_timestamp_rewind=720 * 120 * 10)

    def run_test(self):

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        sc_node_3 = self.sc_nodes[2]

        evm_address_sc_node_1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc_node_3 = sc_node_3.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # get stake info from genesis block
        ft_amount_in_zen = Decimal('100.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_3,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        # Generate SC block
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # reach the SHANGHAI fork
        current_best_epoch = sc_node_1.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        if VERSION_1_3_FORK_EPOCH > current_best_epoch:
            for i in range(0, VERSION_1_3_FORK_EPOCH - current_best_epoch):
                generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
                self.sc_sync_all()

        '''
        #####################################################################################
        '''
        native_contract = SmartContract("ForgerStakes")
        method = 'upgrade()'
        # Execute upgrade
        contract_function_call(sc_node_2, native_contract, FORGER_STAKE_SMART_CONTRACT_ADDRESS,
                               evm_address_sc_node_2, method)
        generate_next_block(sc_node_1, "first node")
        '''
        #####################################################################################
        '''

        # SC1 delegates SC2 multiple times
        staked_amount = 0.000001
        on_chain_nonce = int(
            sc_node_1.rpc_eth_getTransactionCount(format_evm(evm_address_sc_node_1), 'latest')['result'], 16)

        sc2_block_sign_pub_key = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrf_pub_key = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        for i in range(0, NUM_OF_STAKES):

            result = ac_makeForgerStake(sc_node_1, evm_address_sc_node_1, sc2_block_sign_pub_key, sc2_vrf_pub_key,
                                        convertZenToZennies(staked_amount), on_chain_nonce)
            if "result" not in result:
                fail("make forger stake failed: " + json.dumps(result))
            else:
                logging.info("Forger stake created: " + json.dumps(result))
                on_chain_nonce += 1

            if i % NUM_OF_STAKE_TXES == 0:
                generate_next_block(sc_node_1, "first node")
                self.sc_sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Test 1 -  get the whole list of stakes in a big page
        next_pos, stake_list = get_paged_forging_stakes_via_eth_call(sc_node_2, evm_address_sc_node_2, 0,
                                                                     NUM_OF_STAKES + 1)
        http_api_res = sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": NUM_OF_STAKES + 1, "startPos": 0}))[
            "result"]
        http_api_all_res = sc_node_1.transaction_allForgingStakes()["result"]

        assert_equal(http_api_res['nextPos'], next_pos)
        assert_equal(http_api_res['stakes'], stake_list)
        assert_equal(http_api_all_res['stakes'], stake_list)

        # Test 2 - get the whole list page by page
        next_pos = 0
        PAGE_SIZE = NUM_OF_STAKES // 3
        list_all = []
        list_all_http_api = []
        while next_pos != -1:
            start_pos = next_pos
            next_pos, stake_list = get_paged_forging_stakes_via_eth_call(sc_node_2, evm_address_sc_node_2, start_pos,
                                                                         PAGE_SIZE)
            http_api_res = \
            sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": PAGE_SIZE, "startPos": start_pos}))["result"]
            assert_equal(http_api_res['nextPos'], next_pos)

            list_all += stake_list
            list_all_http_api += http_api_res['stakes']

        assert_equal(http_api_all_res['stakes'], list_all)
        assert_equal(http_api_all_res['stakes'], list_all_http_api)

        # Test 3 - get the whole list page by page with size = 1
        next_pos = 0
        PAGE_SIZE = 1
        list_all = []
        list_all_http_api = []
        while next_pos != -1:
            start_pos = next_pos
            next_pos, stake_list = get_paged_forging_stakes_via_eth_call(sc_node_2, evm_address_sc_node_2, start_pos,
                                                                         PAGE_SIZE)
            http_api_res = \
            sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": PAGE_SIZE, "startPos": start_pos}))[
                "result"]
            assert_equal(http_api_res['nextPos'], next_pos)

            list_all += stake_list
            list_all_http_api += http_api_res['stakes']

        assert_equal(http_api_all_res['stakes'], list_all)
        assert_equal(http_api_all_res['stakes'], list_all_http_api)

        # Negative Test 4 - use bad start_pos value
        start_pos = 2 * NUM_OF_STAKES
        PAGE_SIZE = 10
        try:
            get_paged_forging_stakes_via_eth_call(sc_node_2, evm_address_sc_node_2,
                                                  start_pos, PAGE_SIZE)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")

        try:
            sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": PAGE_SIZE, "startPos": start_pos}))[
                "result"]
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")

        # Negative Test 5 - use bad size value
        start_pos = 0
        PAGE_SIZE = -2
        try:
            get_paged_forging_stakes_via_eth_call(sc_node_2, evm_address_sc_node_2,
                                                  start_pos, PAGE_SIZE)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")

        try:
            sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": PAGE_SIZE, "startPos": start_pos}))[
                "result"]
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")

        # Negative Test 6 - use bad size value
        start_pos = 0
        PAGE_SIZE = 0
        try:
            get_paged_forging_stakes_via_eth_call(sc_node_2, evm_address_sc_node_2,
                                                  start_pos, PAGE_SIZE)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")

        try:
            sc_node_1.transaction_pagedForgingStakes(json.dumps({"size": PAGE_SIZE, "startPos": start_pos}))[
                "result"]
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("No forging stakes expected for SC node 2.")

if __name__ == "__main__":
    SCEvmForgerStakesPager().main()
