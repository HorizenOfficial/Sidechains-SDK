#!/usr/bin/env python3
import logging
from decimal import Decimal

from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_use_smart_contract import SmartContract, EvmExecutionError
from SidechainTestFramework.account.address_util import format_eoa, format_evm
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, websocket_port_by_mc_node_index, \
    forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, AccountModelBlockVersion, \
    EVM_APP_BINARY, generate_next_blocks, generate_next_block

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


def call_addr_uint_fn(node, smart_contract, contract_address, source_addr, addr, uint, static_call, generate_block,
                      method, overrideGas):
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
                           generate_block, method, overrideGas):
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
              "{} tokens from {} to {}".format(amount, source_account, target_account))
    else:
        logging.info("Calling {}: transferring {} tokens from {} to {}".format(method, amount, source_account,
                                                                            target_account))

    return call_addr_uint_fn(node, smart_contract, contract_address, source_account, target_account, amount,
                             static_call, generate_block, method, overrideGas)


def transfer_from_tokens(node, smart_contract, contract_address, tx_sender_account, source_account, target_account,
                         amount, *, static_call=False, generate_block=True, overrideGas = None):
    method = 'transferFrom(address,address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transfer of ".format(method) +
              "{} tokens from {} to {}".format(amount, source_account, target_account))
    else:
        logging.info("Calling {}: transferring {} tokens from {} to {}".format(method, amount, source_account,
                                                                            target_account))

    return call_addr_addr_uint_fn(node, smart_contract, contract_address, tx_sender_account, source_account,
                                  target_account, amount, static_call=static_call, generate_block=generate_block,
                                  method=method, overrideGas=overrideGas)


def approve(node, smart_contract, contract_address, source_account, target_account, amount, *, static_call=False,
            generate_block=True):
    method = 'approve(address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing approval of ".format(method) +
              "{} tokens from {} to {}".format(amount, source_account, target_account))
    else:
        logging.info(
            "Calling {}: approving {} tokens from {} to {}".format(method, amount, source_account, target_account))

    return call_addr_uint_fn(node, smart_contract, contract_address, source_account, target_account, amount,
                             static_call, generate_block, method, None)


def compare_balance(node, smart_contract, contract_address, account_address, expected_balance):
    logging.info("Checking balance of {}...".format(account_address))
    res = smart_contract.static_call(node, 'balanceOf(address)', account_address,
                                     fromAddress=account_address, toAddress=contract_address)
    logging.info("Expected balance: '{}', actual balance: '{}'".format(expected_balance, res[0]))
    assert_equal(res[0], expected_balance)
    return res[0]


def compare_allowance(node, smart_contract, contract_address, owner_address, allowed_address, expected_balance):
    logging.info("Checking allowance of {} from {}...".format(allowed_address, owner_address))
    res = smart_contract.static_call(node, 'allowance(address,address)', owner_address, allowed_address,
                                     fromAddress=allowed_address, toAddress=contract_address)
    logging.info("Expected allowance: '{}', actual allowance: '{}'".format(expected_balance, res[0]))
    assert_equal(expected_balance, res[0])
    return res[0]


def compare_total_supply(node, smart_contract, contract_address, sender_address, expected_supply):
    logging.info("Checking total supply of token at {}...".format(contract_address))
    res = smart_contract.static_call(node, 'totalSupply()', fromAddress=sender_address, toAddress=contract_address)
    logging.info("Expected supply: '{}', actual supply: '{}'".format(expected_supply, res[0]))
    assert_equal(res[0], expected_supply)
    return res[0]


def deploy_smart_contract(node, smart_contract, from_address):
    logging.info("Deploying smart contract...")
    logging.info(from_address)
    logging.info(format_evm(from_address))
    logging.info("Estimating gas for deployment...")
    estimated_gas = smart_contract.estimate_gas(node, 'constructor',
                                                                fromAddress=from_address)
    logging.info("Estimated gas is {}".format(estimated_gas))
    tx_hash, address = smart_contract.deploy(node,
                                             fromAddress=from_address,
                                             gasLimit=estimated_gas)
    logging.info("Generating next block...")
    generate_next_blocks(node, "first node", 1)
    # TODO check logs when implemented (events)
    tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
    assert_equal(format_evm(tx_receipt['result']['contractAddress']), format_evm(address))
    logging.info("Smart contract deployed successfully to address {}".format(address))
    return address


class SCEvmERC20Contract(SidechainTestFramework):
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
                                                                 block_timestamp_rewind=720 * 120 * 5,
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
        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = format_evm(ret["result"]["proposition"]["address"])

        # call a legacy wallet api
        ret = sc_node.wallet_allPublicKeys()
        logging.info(ret)

        ft_amount_in_zen = Decimal("100.00")
        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(evm_address),
                                      ft_amount_in_zen,
                                      mc_return_address)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(other_address),
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

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()


        transfer_amount = 99

        # Testing normal transfer
        check_res = transfer_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                    transfer_amount, static_call=True, generate_block=True)
        assert_true(check_res[0], "Static call failed")
        compare_balance(sc_node, smart_contract, smart_contract_address, evm_address, initial_balance)

        # TODO check receipt of tx below - should emit logs and be successful
        tx_hash = transfer_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                  transfer_amount, static_call=False, generate_block=True)
        res = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                              initial_balance - transfer_amount)
        res = compare_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)

        # Test reverting
        reverting_transfer_amount = 2
        exception_thrown = False
        try:
            exception_thrown = False
            check_res = transfer_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                        reverting_transfer_amount, static_call=True, generate_block=True)
        except EvmExecutionError as err:
            exception_thrown = True
            logging.info("Expected exception thrown: {}".format(err))

        finally:
            assert_true(exception_thrown, "Exception should have been thrown")
            pass

        # TODO check receipt of tx below - should revert
        tx_hash = transfer_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                  reverting_transfer_amount, static_call=False, generate_block=True, overrideGas=9000000)
        res = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                              initial_balance - transfer_amount)
        res = compare_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)

        # Test transferFrom and approval
        approved_amount = 10

        tx_hash = approve(sc_node, smart_contract, smart_contract_address, other_address, evm_address, approved_amount,
                          static_call=False, generate_block=True)
        res = compare_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)

        res = compare_allowance(sc_node, smart_contract, smart_contract_address, other_address, evm_address,
                                approved_amount)
        # TODO once evm_call is fixed - below should indicate success
        res = transfer_from_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                   evm_address, approved_amount, static_call=True, generate_block=False)

        # TODO once receipts are fixed - below should be a success and emit logs
        tx_hash = transfer_from_tokens(sc_node, smart_contract, smart_contract_address, evm_address, other_address,
                                       evm_address, approved_amount, static_call=False, generate_block=True)

        res = compare_balance(sc_node, smart_contract, smart_contract_address, evm_address,
                              initial_balance - transfer_amount + approved_amount)
        res = compare_balance(sc_node, smart_contract, smart_contract_address, other_address,
                              transfer_amount - approved_amount)


if __name__ == "__main__":
    SCEvmERC20Contract().main()