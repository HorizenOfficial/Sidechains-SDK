#!/usr/bin/env python3
import json
import logging
import time
from decimal import Decimal

from eth_utils import to_checksum_address, remove_0x_prefix

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.sc_boostrap_info import MCConnectionInfo, SCNodeConfiguration, SCNetworkConfiguration, \
    SCCreationInfo
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, generate_next_block, bootstrap_sidechain_nodes, \
    DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND, start_sc_nodes, EVM_APP_BINARY, AccountModelBlockVersion, \
    generate_next_blocks, convertZenToZennies, convertZenniesToWei, generate_account_proposition
from sc_evm_test_contract_contract_deployment_and_interaction import deploy_smart_contract
from test_framework.util import assert_equal, assert_true, forward_transfer_to_sidechain, start_nodes, \
    websocket_port_by_mc_node_index

"""
Check the Contract Deployment with CREATE2 and check solidity SELFDESTRUCT method

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the SC node:
        - verify the MC block is included
    For contract deployment and interaction:
        - Deploy Factory smart contract
        - Static call to get later deployed smart contract address upfront
        - EOA to EOA - transfer some funds to address that will become a smart contract later
        - Factory smart contract call to deploy Simple Wallet smart contract with CREATE2
        - Simple Wallet static call to get balance of Simple Wallet address
        - Simple Wallet call selfdestruct with sending funds back to our evm_address
        - Factory smart contract call to deploy Simple Wallet smart contract with CREATE2
        - Factory smart contract call to deploy Simple Wallet again, should fail, because it is already present
"""


def contract_function_static_call(node, smart_contract_type, smart_contract_address, from_address, method, *args):
    res = smart_contract_type.static_call(node, method, *args, fromAddress=from_address,
                                          toAddress=smart_contract_address)
    return res


def contract_function_call(node, smart_contract_type, smart_contract_address, from_address, method, *args):
    logging.info("Estimating gas for contract call...")
    estimated_gas = smart_contract_type.estimate_gas(node, method, *args,
                                                     fromAddress=from_address, toAddress=smart_contract_address)
    logging.info("Calling {}: using call function".format(method))
    res = smart_contract_type.call_function(node, method, *args, fromAddress=from_address,
                                            gasLimit=estimated_gas,
                                            toAddress=smart_contract_address)

    logging.info("generating next block...")
    generate_next_blocks(node, "first node", 1)

    status = node.rpc_eth_getTransactionReceipt(res)['result']['status']

    return status


def deploy_smart_contract(node, smart_contract, from_address):
    logging.info("Estimating gas for deployment...")
    estimated_gas = smart_contract.estimate_gas(node, 'constructor',
                                                fromAddress=from_address)
    logging.info("Estimated gas is {}".format(estimated_gas))
    logging.info("Deploying smart contract...")
    tx_hash, address = smart_contract.deploy(node,
                                             fromAddress=from_address,
                                             gasLimit=estimated_gas)
    logging.info("Generating next block...")
    generate_next_blocks(node, "first node", 1)
    tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
    assert_equal(tx_receipt['result']['contractAddress'], address.lower())
    logging.info("Smart contract deployed successfully to address 0x{}".format(address))
    return address


def eoa2eoa(node, fromAddress, toAddress, transferred_amount):
    transferred_amount_in_zennies = convertZenToZennies(transferred_amount)
    transferred_amount_in_wei = convertZenniesToWei(transferred_amount_in_zennies)

    recipient_keys = generate_account_proposition("seed3", 1)[0]
    recipient_proposition = recipient_keys.proposition
    logging.info("Trying to send {} zen to address {}".format(transferred_amount, recipient_proposition))

    j = {
        "from": fromAddress,
        "to": toAddress,
        "value": transferred_amount_in_zennies
    }
    request = json.dumps(j)
    response = node.transaction_sendCoinsToAddress(request)
    logging.info("tx sent:")
    logging.info(response)

    generate_next_block(node, "first node", force_switch_to_next_epoch=True)

    balance = node.wallet_getBalance(
        json.dumps({"address": str(toAddress)}))['result']['balance']

    return balance, transferred_amount_in_wei


class SCEvmContractDeploymentCreate2(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 50
    number_of_sidechain_nodes = 1
    API_KEY = "Horizen"

    def setup_nodes(self):
        number_of_sidechain_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(number_of_sidechain_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                        '-scproofqueuesize=0']] * number_of_sidechain_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir, binary=[EVM_APP_BINARY] * 2)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Checks that MC block with sc creation tx is referenced in the genesis sc block
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))

        sc_best_block = sc_node.block_best()["result"]
        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verifies MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        mc_return_address = mc_node.getnewaddress()

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

        sc_best_block = sc_node.block_best()["result"]
        logging.info(sc_best_block)

        factory_contract = 'Factory'
        logging.info(f"Creating smart contract utilities for {factory_contract}")
        factory_contract = SmartContract(factory_contract)
        logging.info(factory_contract)

        simple_wallet_contract = 'SimpleWallet'
        logging.info(f"Creating smart contract utilities for {simple_wallet_contract}")
        simple_wallet_contract = SmartContract(simple_wallet_contract)
        logging.info(simple_wallet_contract)

        # Salt used for contract deployment with CREATE2
        salt = 1337
        evm_hex_address = to_checksum_address(evm_address)

        # Deploy Factory smart contract
        factory_contract_address = deploy_smart_contract(sc_node, factory_contract, evm_hex_address)

        # Get address with salt, that is later a smart contract
        simple_wallet_contract_address = \
            contract_function_static_call(sc_node, factory_contract, factory_contract_address,
                                          evm_hex_address,
                                          'getAddress(uint256)', salt)[0]

        # Transfer some funds to the address, that is later a smart contract
        balance, transferred_amount_in_wei = eoa2eoa(sc_node, evm_address,
                                                     remove_0x_prefix(simple_wallet_contract_address),
                                                     Decimal(12.34))
        assert_equal(transferred_amount_in_wei, balance, "Check that EOA to EOA transfer was successful")

        # CREATE2 deployment of Simple Wallet via Factory contract call
        method = 'deploy(uint256)'
        method_args = salt
        res = contract_function_call(sc_node, factory_contract, factory_contract_address, evm_address, method,
                                     method_args)
        assert_equal('0x1', res, "Check that function call was successful")

        # Check balance of Simple Wallet contract has the amount sent before
        balance = contract_function_static_call(sc_node, simple_wallet_contract, simple_wallet_contract_address,
                                                evm_hex_address, 'getBalance()')

        assert_equal(str(transferred_amount_in_wei), str(balance[0]))

        # Call destroy method of Simple Wallet contract with sending all funds to evm_address
        method = 'destroy(address)'
        method_args = evm_hex_address
        res = contract_function_call(sc_node, simple_wallet_contract, simple_wallet_contract_address, evm_address,
                                     method, method_args)
        assert_equal('0x1', res, "Check that function call was successful")

        # CREATE2 deployment of Simple Wallet via Factory contract call
        method = 'deploy(uint256)'
        method_args = salt
        res = contract_function_call(sc_node, factory_contract, factory_contract_address, evm_address, method,
                                     method_args)
        assert_equal('0x1', res, "Check that function call was successful")

        # Check that we can not deploy to the same address again
        exception_occurs = False
        try:
            contract_function_call(sc_node, factory_contract, factory_contract_address, evm_address, method,
                                   method_args)
        except Exception as e:
            exception_occurs = True
            logging.info("We had an exception as expected: {}".format(str(e)))
        finally:
            assert_true(exception_occurs, "Deploying a contract to the same address again should fail")


if __name__ == "__main__":
    SCEvmContractDeploymentCreate2().main()
