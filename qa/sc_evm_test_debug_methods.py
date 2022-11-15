#!/usr/bin/env python3
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, eoa_transaction, CallMethod
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, websocket_port_by_mc_node_index, \
    forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, AccountModelBlockVersion, \
    EVM_APP_BINARY, generate_next_blocks, generate_next_block, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND

global_call_method = CallMethod.RPC_EIP155

"""
Check an EVM ERC20 Smart Contract.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy the smart contract without initial data
        - Check initial minting success (static_call)
        - Check initial supply (static_call)
        - Check a successful transfer (static_call + actual tx)
        - Check a reverting transfer (static_call + actual tx)
        - Check approval + transferFrom (static_call + actual tx)
"""


def eoa_transfer(node, sender, receiver, amount, call_method: CallMethod = global_call_method,
                 static_call: bool = False, generate_block: bool = True, tag: str = 'latest'):
    if static_call:
        res = eoa_transaction(node, from_addr=sender, to_addr=receiver, value=amount, static_call=True, tag=tag)
    else:
        res = eoa_transaction(node, from_addr=sender, to_addr=receiver, call_method=call_method, value=amount)
        if generate_block:
            logging.info("generating next block...")
            generate_next_blocks(node, "first node", 1)
    return res



def call_addr_uint_fn(node, smart_contract, contract_address, source_addr, addr, uint, static_call, generate_block,
                      method, overrideGas=None):
    if static_call:
        res = smart_contract.static_call(node, method, format_evm(addr), uint,
                                         fromAddress=source_addr, toAddress=contract_address)
    else:
        if overrideGas is None:
            estimated_gas = smart_contract.estimate_gas(node, method, format_evm(addr), uint,
                                                                    fromAddress=source_addr, toAddress=format_evm(contract_address))
        res = smart_contract.call_function(node, method, format_evm(addr), uint,
                                           fromAddress=source_addr,
                                           gasLimit=estimated_gas if overrideGas is None else overrideGas, toAddress=contract_address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def call_addr_addr_uint_fn(node, smart_contract, contract_address, source_addr, addr1, addr2, uint, static_call,
                           generate_block, method, overrideGas=None):
    if static_call:
        res = smart_contract.static_call(node, method, addr1, addr2, uint,
                                         fromAddress=source_addr, toAddress=contract_address)
    else:
        if overrideGas is None:
            estimated_gas = smart_contract.estimate_gas(node, method, addr1, addr2, uint,
                                                                        fromAddress=source_addr, toAddress=contract_address)
        res = smart_contract.call_function(node, method, addr1, addr2, uint,
                                           fromAddress=source_addr,
                                            gasLimit=estimated_gas if overrideGas is None else overrideGas, toAddress=contract_address)
    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def transfer_tokens(node, smart_contract, contract_address, source_account, target_account, amount, *,
                    static_call=False, generate_block=True, overrideGas = None):
    method = 'transfer(address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transfer of ".format(method) +
              "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
    else:
        logging.info("Calling {}: transferring {} tokens from 0x{} to 0x{}".format(method, amount, source_account,
                                                                            target_account))

    return call_addr_uint_fn(node, smart_contract, contract_address, source_account, target_account, amount,
                             static_call, generate_block, method, overrideGas)


def transfer_from_tokens(node, smart_contract, contract_address, tx_sender_account, source_account, target_account,
                         amount, *, static_call=False, generate_block=True):
    method = 'transferFrom(address,address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transfer of ".format(method) +
              "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
    else:
        logging.info("Calling {}: transferring {} tokens from 0x{} to 0x{}".format(method, amount, source_account,
                                                                            target_account))

    return call_addr_addr_uint_fn(node, smart_contract, contract_address, tx_sender_account, source_account,
                                  target_account, amount, static_call=static_call, generate_block=generate_block,
                                  method=method)


def approve(node, smart_contract, contract_address, source_account, target_account, amount, *, static_call=False,
            generate_block=True):
    method = 'approve(address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing approval of ".format(method) +
              "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
    else:
        logging.info(
            "Calling {}: approving {} tokens from 0x{} to 0x{}".format(method, amount, source_account, target_account))

    return call_addr_uint_fn(node, smart_contract, contract_address, source_account, target_account, amount,
                             static_call, generate_block, method)


def compare_balance(node, smart_contract, contract_address, account_address, expected_balance):
    logging.info("Checking balance of 0x{}...".format(account_address))
    res = smart_contract.static_call(node, 'balanceOf(address)', account_address,
                                     fromAddress=account_address, toAddress=contract_address)
    logging.info("Expected balance: '{}', actual balance: '{}'".format(expected_balance, res[0]))
    assert_equal(res[0], expected_balance)
    return res[0]


def compare_allowance(node, smart_contract, contract_address, owner_address, allowed_address, expected_balance):
    logging.info("Checking allowance of 0x{} from 0x{}...".format(allowed_address, owner_address))
    res = smart_contract.static_call(node, 'allowance(address,address)', owner_address, allowed_address,
                                     fromAddress=allowed_address, toAddress=contract_address)
    logging.info("Expected allowance: '{}', actual allowance: '{}'".format(expected_balance, res[0]))
    assert_equal(res[0], expected_balance)
    return res[0]


def compare_total_supply(node, smart_contract, contract_address, sender_address, expected_supply):
    logging.info("Checking total supply of token at 0x{}...".format(contract_address))
    res = smart_contract.static_call(node, 'totalSupply()', fromAddress=sender_address, toAddress=contract_address)
    logging.info("Expected supply: '{}', actual supply: '{}'".format(expected_supply, res[0]))
    assert_equal(res[0], expected_supply)
    return res[0]


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
    assert_equal(format_evm(tx_receipt['result']['contractAddress']), format_evm(address))
    logging.info("Smart contract deployed successfully to address 0x{}".format(address))
    return address


class SCEvmDebugMethods(SidechainTestFramework):
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
        evm_address = '0x' + ret["result"]["proposition"]["address"]
        logging.info("pubkey = {}".format(evm_address))

        # call a legacy wallet api
        ret = sc_node.wallet_allPublicKeys()
        logging.info(ret)

        ft_amount_in_zen = Decimal("33.22")
        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_address[2:],
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_best_block = sc_node.block_best()["result"]
        logging.info(sc_best_block)

        smart_contract_type = 'TestERC20'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)

        initial_balance = 100
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, evm_address)

        res = compare_total_supply(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)
        res = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = ret["result"]["proposition"]["address"]
        transfer_amount = 99

        tx_hash = transfer_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                  transfer_amount, static_call=False, generate_block=True)

        res = sc_node.rpc_eth_getTransactionReceipt(tx_hash)
        assert_equal(res['result']['status'], '0x1', "Error in tx - unrelated to debug methods")

        res = sc_node.rpc_debug_traceTransaction(tx_hash)['result']
        assert_true("error" not in res, "debug_traceTransaction failed for successful smart contract transaction")

        tx_hash = eoa_transfer(sc_node, evm_address, other_address, transfer_amount, static_call=False,
                               generate_block=True)

        res = sc_node.rpc_eth_getTransactionReceipt(tx_hash)
        assert_equal(res['result']['status'], '0x1', "Error in tx - unrelated to debug methods")

        # TODO debug call fails for EOA
        res = sc_node.rpc_debug_traceTransaction(tx_hash)
        assert_true("error" not in res['result'], "debug_traceTransaction failed for successful eoa transfer")

        res = sc_node.rpc_debug_traceBlockByNumber("0x4")
        assert_true("error" not in res["result"], 'debug_traceBlockByNumber failed')


if __name__ == "__main__":
    SCEvmDebugMethods().main()
