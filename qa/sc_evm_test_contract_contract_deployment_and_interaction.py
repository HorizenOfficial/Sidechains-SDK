#!/usr/bin/env python3
import logging
import random
from decimal import Decimal

from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.address_util import format_evm
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, \
    AccountModelBlockVersion, EVM_APP_BINARY, generate_next_blocks, generate_next_block

"""
Check an EVM Contract which deploys smart contracts, and their interaction.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy the smart contract with initial secret
        - Deploy a child contract
        - Read the secret via child
        - Deploy a second child contract
        - Read the secret via second child
        - Update secret via second child
        - Read the secret via first and second child
"""


def deploy_smart_contract(node, smart_contract_type, from_address, initial_secret):
    logging.info("Estimating gas for deployment...")
    estimated_gas = smart_contract_type.estimate_gas(node, 'constructor', initial_secret,
                                                                fromAddress=from_address)
    logging.info("Estimated gas is {}".format(estimated_gas))
    logging.info("Deploying smart contract with initial secret {}...".format(initial_secret))
    tx_hash, address = smart_contract_type.deploy(node, initial_secret,
                                                  fromAddress=from_address,
                                                  gasLimit=estimated_gas)
    logging.info("Generating next block...")
    generate_next_blocks(node, "first node", 1)
    tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
    assert_equal(format_evm(tx_receipt['result']['contractAddress']), format_evm(address))
    logging.info("Smart contract deployed successfully to address 0x{}".format(address))
    return address
    
def deploy_child(node, smart_contract_type, smart_contract_address, from_address, *, static_call,
                 generate_block):
    method = 'deployContract()'
    if static_call:
        logging.info("Read-only calling {}: testing deployment of a child contract".format(method))
        res = smart_contract_type.static_call(node, method, fromAddress=from_address,
                                              toAddress=smart_contract_address)
    else:
        logging.info("Calling {}: deploying a child contract".format(method))
        estimated_gas = smart_contract_type.estimate_gas(node, method, fromAddress=from_address, toAddress=smart_contract_address)
        res = smart_contract_type.call_function(node, method, fromAddress=from_address, gasLimit=estimated_gas,
                                                toAddress=smart_contract_address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)

    return res


def update_parent_secret(node, smart_contract_type, smart_contract_address, from_address, *, new_secret, static_call,
                         generate_block):
    method = 'setParentSecret(string)'
    if static_call:
        logging.info("Read-only calling {}: testing setting the secret to {} via a child contract".format(method, new_secret))
        res = smart_contract_type.static_call(node, method, new_secret, fromAddress=from_address,
                                              toAddress=smart_contract_address)
    else:
        logging.info("Calling {}: setting the secret to {} via a child contract".format(method, new_secret))
        estimated_gas = smart_contract_type.estimate_gas(node, method, new_secret, fromAddress=from_address, toAddress=smart_contract_address)
        res = smart_contract_type.call_function(node, method, new_secret, fromAddress=from_address, gasLimit=estimated_gas, toAddress=smart_contract_address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)

    return res


def get_secret(node, smart_contract_type, smart_contract_address, from_address):
    method = 'checkParentSecret()'
    logging.info("Getting parent secret via function {} on contract {}".format(method, smart_contract_address))
    res = smart_contract_type.static_call(node, method, fromAddress=from_address,
                                          toAddress=smart_contract_address)[0]
    logging.info("Parent secret: {}".format(res))
    return res


def compare_secret(node, smart_contract_type, smart_contract_address, from_address, expected_secret):
    logging.info("Comparing secrets...")
    res = get_secret(node, smart_contract_type, format_evm(smart_contract_address), from_address)
    logging.info("Expected secret: {}, actual secret: {}".format(expected_secret, res))
    assert_equal(res, expected_secret)
    return res


def get_children(node, smart_contract_type, smart_contract_address, from_address):
    method = 'getChildren()'
    logging.info("Getting children via function {}".format(method))
    res = list(smart_contract_type.static_call(node, method, fromAddress=from_address,
                                               toAddress=smart_contract_address)[0])
    logging.info("Children: {}".format(res))
    return res


def assert_child_count(node, smart_contract_type, smart_contract_address, from_address, expected_count):
    logging.info("Asserting child count...")
    res = get_children(node, smart_contract_type, smart_contract_address, from_address)
    logging.info("Expected child count: {}, actual child count: {}".format(expected_count, len(res)))
    assert_equal(len(res), expected_count)
    return res


def random_byte_string(*, length=20):
    return '0x' + bytes([random.randrange(0, 256) for _ in range(0, length)]).hex()


class SCEvmDeployingContract(SidechainTestFramework):
    sc_nodes_bootstrap_info = None

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()
        logging.info("...skip sync since it would timeout as of now")
        # self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=720 * 120 * 5,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(num_nodes=1, dirname=self.options.tmpdir,
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
        evm_address = ret["result"]["proposition"]["address"]
        logging.info("pubkey = {}".format(evm_address))

        # call a legacy wallet api
        ret = sc_node.wallet_allPublicKeys()
        logging.info(ret)

        ft_amount_in_zen = Decimal("3000")

        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_address,
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_best_block = sc_node.block_best()["result"]
        logging.info(sc_best_block)

        parent_smart_contract_type = 'TestDeployingContract'
        logging.info(f"Creating smart contract utilities for {parent_smart_contract_type}")
        parent_smart_contract_type = SmartContract(parent_smart_contract_type)
        logging.info(parent_smart_contract_type)

        child_smart_contract_type = 'TestDeployedContract'
        logging.info(f"Creating smart contract utilities for {child_smart_contract_type}")
        child_smart_contract_type = SmartContract(child_smart_contract_type)
        logging.info(child_smart_contract_type)

        # testing deployment
        initial_secret = random_byte_string(length=20)
        number_of_children = 0
        evm_hex_address = to_checksum_address(evm_address)
        parent_contract_address = deploy_smart_contract(sc_node, parent_smart_contract_type, evm_hex_address,
                                                        initial_secret)
        # asserting that there are no initial children
        children = assert_child_count(sc_node, parent_smart_contract_type, parent_contract_address, evm_address,
                                      number_of_children)

        # DEPLOYING FIRST CHILD

        # TODO when evm_call is implemented - check result indicates success
        res = deploy_child(sc_node, parent_smart_contract_type, parent_contract_address, evm_address,
                           static_call=True, generate_block=False)
        # TODO when receipts are implemented - check receipt indicates success and emits logs
        tx_hash = deploy_child(sc_node, parent_smart_contract_type, parent_contract_address, evm_hex_address,
                               static_call=False, generate_block=True)

        number_of_children += 1
        # asserting that there is now one child
        children = assert_child_count(sc_node, parent_smart_contract_type, parent_contract_address, evm_address,
                                      number_of_children)
        compare_secret(sc_node, child_smart_contract_type, children[-1], evm_address, initial_secret)

        # DEPLOYING SECOND CHILD

        # TODO when evm_call is implemented - check result indicates success
        res = deploy_child(sc_node, parent_smart_contract_type, parent_contract_address, evm_address,
                           static_call=True, generate_block=False)
        # TODO when receipts are implemented - check receipt indicates success and emits logs
        tx_hash = deploy_child(sc_node, parent_smart_contract_type, parent_contract_address, evm_hex_address,
                               static_call=False, generate_block=True)

        number_of_children += 1
        # asserting that there is now one child
        children = assert_child_count(sc_node, parent_smart_contract_type, parent_contract_address, evm_address,
                                      number_of_children)
        compare_secret(sc_node, child_smart_contract_type, children[-1], evm_address, initial_secret)

        # SETTING SECRET VIA SECOND CHILD, CONFIRMING CHANGE VIA FIRST CHILD

        new_secret = random_byte_string()
        # TODO when evm_call is implemented - check result indicates success
        res = update_parent_secret(sc_node, child_smart_contract_type, children[-1], evm_address,
                                   new_secret=new_secret, static_call=True, generate_block=False)
        # TODO when receipts are implemented - check receipt indicates success and emits logs
        tx_hash = update_parent_secret(sc_node, child_smart_contract_type, children[-1], evm_hex_address,
                                       new_secret=new_secret, static_call=False, generate_block=True)

        compare_secret(sc_node, child_smart_contract_type, children[-1], evm_address, new_secret)
        compare_secret(sc_node, child_smart_contract_type, children[0], evm_address, new_secret)


if __name__ == "__main__":
    SCEvmDeployingContract().main()
