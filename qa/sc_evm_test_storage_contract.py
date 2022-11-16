#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract, EvmExecutionError
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, websocket_port_by_mc_node_index, \
    forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, AccountModelBlockVersion, \
    EVM_APP_BINARY, generate_next_blocks, generate_next_block, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND

"""
Check an EVM Storage Smart Contract.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy the smart contract with initial data
        - Check initial value
        - Set the storage to a string
        - Read the string in a read-only call
        - Set the storage to a different string
        - Read the different string in a read only call
"""


def set_storage_value(node, smart_contract, address, tx_sender, new_value, *, static_call=False, generate_block=True):
    if static_call:
        logging.info("Testing setting smart contract storage to {} in a static call".format(new_value))
        res = smart_contract.static_call(node, 'set(string)', new_value, fromAddress=tx_sender, toAddress=address,
                                         gasPrice=900000000)
    else:
        logging.info("Setting smart contract storage to {}".format(new_value))
        res = smart_contract.call_function(node, 'set(string)', new_value, fromAddress=tx_sender,
                                           gasLimit=10000000, toAddress=address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)

    if not static_call:
        tx_receipt = node.rpc_eth_getTransactionReceipt(res)
        logging.info(tx_receipt)
    return res


def check_storage_value(node, smart_contract, address, tx_sender, expected_value):
    logging.info("Checking stored value...")
    res = smart_contract.static_call(node, 'get()', fromAddress=tx_sender, toAddress=address,gasPrice=900000000)
    logging.info("Expected stored value: \"{}\", actual stored value: \"{}\"".format(expected_value, res[0]))
    return res[0]


class SCEvmStorageContract(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    API_KEY = "Horizen"

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()
        logging.info("...skip sync since it would timeout as of now")
        # self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            api_key = self.API_KEY
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(num_nodes=1, dirname=self.options.tmpdir,
                              auth_api_key=self.API_KEY,
                              binary=[EVM_APP_BINARY])  # , extra_args=['-agentlib'])

    def run_test(self):
        sc_node = self.sc_nodes[0]
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        logging.info("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node.block_best()["result"]

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        mc_return_address = self.nodes[0].getnewaddress()
        # evm_address = generate_account_proposition("seed2", 1)[0]

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        logging.info(ret)
        evm_address = format_evm(ret["result"]["proposition"]["address"])
        logging.info("pubkey = {}".format(evm_address))

        # call a legacy wallet api
        ret = sc_node.wallet_allPublicKeys()
        logging.info(ret)

        ft_amount_in_zen = Decimal("3000")
        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(evm_address),
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_best_block = sc_node.block_best()["result"]
        logging.info(sc_best_block)

        smart_contract_type = 'StorageTestContract'
        logging.info(f"Creating smart contract utilities for {smart_contract_type}")
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)
        test_message = 'Initial message'

        estimated_gas = smart_contract.estimate_gas(sc_node, 'constructor', test_message,
                                                                fromAddress=evm_address)
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, test_message,
                                                                fromAddress=evm_address,
                                                                gasLimit=estimated_gas)
        generate_next_blocks(sc_node, "first node", 1)
        logging.info("Blocks mined - tx receipt will contain address")
        # TODO check logs (events)
        tx_receipt = sc_node.rpc_eth_getTransactionReceipt(tx_hash)

        # TODO check receipt address in checksum format
        assert_equal(format_evm(tx_receipt['result']['contractAddress']), smart_contract_address)

        generate_next_blocks(sc_node, "first node", 1)
        res = check_storage_value(sc_node, smart_contract, smart_contract_address, evm_address, test_message)

        block = sc_node.rpc_eth_getBlockByNumber('0x3', 'false')
        logging.info(block)

        tx_hash = block['result']['transactions'][0]
        logging.info(sc_node.rpc_eth_getTransactionReceipt(tx_hash))
        logging.info(sc_node.rpc_eth_getTransactionByHash(tx_hash))
        was_exception = False
        test_message = 'This is a message'
        try:
            was_exception = False
            res = set_storage_value(sc_node, smart_contract, smart_contract_address, evm_address, test_message,
                                    static_call=True, generate_block=False)
        except EvmExecutionError as err:
            logging.info(err)
            was_exception = True
        finally:
            assert_true(not was_exception, "static call failed but should not have")
        # TODO when receipts are fixed - check tx -> should indicate success and emit log
        tx_hash = set_storage_value(sc_node, smart_contract, smart_contract_address, evm_address, test_message,
                                    static_call=False, generate_block=True)
        res = check_storage_value(sc_node, smart_contract, smart_contract_address, evm_address, test_message)

        test_message = 'This is a different message'
        # TODO when evm_call is fixed - check result -> should indicate success
        res = set_storage_value(sc_node, smart_contract, smart_contract_address, evm_address, test_message,
                                static_call=True, generate_block=False)
        # TODO when receipts are fixed - check tx -> should indicate success and emit log
        tx_hash = set_storage_value(sc_node, smart_contract, smart_contract_address, evm_address, test_message,
                                    static_call=False, generate_block=True)
        res = check_storage_value(sc_node, smart_contract, smart_contract_address, evm_address, test_message)


if __name__ == "__main__":
    SCEvmStorageContract().main()
