#!/usr/bin/env python3
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract, EvmExecutionError
from SidechainTestFramework.account.ac_utils import format_eoa, format_evm, deploy_smart_contract, \
    contract_function_static_call, contract_function_call, generate_block_and_get_tx_receipt
from SidechainTestFramework.scutil import is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, \
    generate_next_block
from test_framework.util import assert_equal, assert_true, forward_transfer_to_sidechain, fail

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


class SCEvmERC20Contract(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        sc_node = self.sc_nodes[0]
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        logging.info("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node.block_best()["result"]

        assert_equal(1, sc_best_block["height"], "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        mc_return_address = self.nodes[0].getnewaddress()

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

        method = 'totalSupply()'
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, evm_address, method)
        assert_equal(initial_balance, res[0])

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address)
        assert_equal(initial_balance, res[0])

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        transfer_amount = 99

        # Testing normal transfer
        method = 'transfer(address,uint256)'
        check_res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, evm_address, method,
                                                  other_address,
                                                  transfer_amount)
        assert_true(check_res[0], "Static call failed")

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address)
        assert_equal(initial_balance, res[0])

        method = 'transfer(address,uint256)'
        tx_hash = contract_function_call(sc_node, smart_contract, smart_contract_address, evm_address, method,
                                         other_address,
                                         transfer_amount)
        tx_receipt = generate_block_and_get_tx_receipt(sc_node, tx_hash)['result']
        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')
        assert_true(len(tx_receipt['logs'][0]) > 0, 'Receipt does not include logs')

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address)
        assert_equal(initial_balance - transfer_amount, res[0])

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address, other_address)
        assert_equal(transfer_amount, res[0])

        # Test reverting
        reverting_transfer_amount = 2
        try:
            method = 'transfer(address,uint256)'
            contract_function_static_call(sc_node, smart_contract, smart_contract_address, evm_address,
                                          method, other_address,
                                          reverting_transfer_amount)
        except EvmExecutionError as err:
            logging.info("Expected exception thrown: {}".format(err))

        else:
            fail("Exception should have been thrown")

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address)
        assert_equal(initial_balance - transfer_amount, res[0])

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address, other_address)
        assert_equal(transfer_amount, res[0])

        # Test transferFrom and approval
        approved_amount = 10

        method = 'approve(address,uint256)'
        tx_hash = contract_function_call(sc_node, smart_contract, smart_contract_address, other_address, method,
                                         evm_address, approved_amount)
        res = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(res, 'Transaction failed')

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address, other_address)
        assert_equal(transfer_amount, res[0])

        method = 'allowance(address,address)'
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, evm_address, method,
                                            other_address, evm_address)
        assert_equal(approved_amount, res[0])

        method = 'transferFrom(address,address,uint256)'
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, evm_address, method,
                                            other_address, evm_address, approved_amount)[0]
        assert_true(res, 'Static call failed')

        tx_hash = contract_function_call(sc_node, smart_contract, smart_contract_address, evm_address, method,
                                         other_address, evm_address, approved_amount)
        tx_receipt = generate_block_and_get_tx_receipt(sc_node, tx_hash)['result']
        assert_equal('0x1', tx_receipt['status'], 'Transaction failed')
        assert_true(len(tx_receipt['logs'][0]) > 0, 'Receipt does not include logs')

        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address)
        assert_equal(initial_balance - transfer_amount + approved_amount, res[0])
        res = smart_contract.get_balance(sc_node, evm_address, smart_contract_address, other_address)
        assert_equal(transfer_amount - approved_amount, res[0])


if __name__ == "__main__":
    SCEvmERC20Contract().main()
