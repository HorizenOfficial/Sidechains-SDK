#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract, EvmExecutionError
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa, contract_function_static_call, \
    contract_function_call, generate_block_and_get_tx_receipt, deploy_smart_contract, CallMethod
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.utils import convertZenToZennies, computeForgedTxFee, convertZenToWei
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block
from test_framework.util import assert_equal, assert_true

"""
Check an EVM ERC721 Smart Contract.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy the smart contract with initial data
        - Test minting
        - Check minting results
        - Pause contract
        - Fail minting
        - Unpause contract
        - Mint a second time
        - Check results
        - Transfer nft
        - Check results
"""


def mint_payable(node, smart_contract, contract_address, source_account, amount, tokenid, *, static_call: bool = False,
                 generate_block: bool = False, overrideGas=None, call_method: CallMethod = CallMethod.RPC_LEGACY):
    method = 'mint(uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing minting of ".format(method) +
                     "a token (id: {}) of collection {} to 0x{}".format(tokenid, contract_address, source_account))
        res = smart_contract.static_call(node, method, tokenid,
                                         fromAddress=source_account,
                                         toAddress=contract_address,
                                         value=amount,
                                         gasPrice=900000000)
    else:
        logging.info(
            "Calling {}: minting of a token (id: {}) of collection {} to 0x{}".format(method, tokenid, contract_address,
                                                                                      source_account))
        if overrideGas is None:
            estimated_gas = smart_contract.estimate_gas(node, method, tokenid, fromAddress=source_account,
                                                        toAddress=contract_address, value=amount)
        res = smart_contract.call_function(node, method, tokenid, call_method=call_method,
                                           fromAddress=source_account,
                                           gasLimit=estimated_gas if overrideGas is None else overrideGas,
                                           toAddress=contract_address,
                                           value=amount)

    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)
    return res


def get_native_balance(node, addr):
    return int(node.rpc_eth_getBalance(format_evm(addr), "latest")['result'], 16)


def set_paused(node, smart_contract, contract_address, sender_address, *, paused: bool, static_call: bool = False,
               generate_block: bool = False):
    if paused:
        method = 'pause()'
    else:
        method = 'unpause()'

    if static_call:
        logging.info("Read-only calling {}: checking (un)pausing of contract at {} from account 0x{}".format(method,
                                                                                                             contract_address,
                                                                                                             sender_address))
        ret = contract_function_static_call(node, smart_contract, contract_address, sender_address, method)
    else:
        logging.info("Calling {}: (un)pausing contract at {} from account 0x{}".format(method,
                                                                                       contract_address,
                                                                                       sender_address))
        ret = contract_function_call(node, smart_contract, contract_address, sender_address, method)

    if generate_block:
        logging.info("generating next block...")
        generate_next_blocks(node, "first node", 1)

    return ret


def compare_total_supply(node, smart_contract, smart_contract_address, account_address, expected_supply):
    method = 'totalSupply()'
    res = contract_function_static_call(node, smart_contract, smart_contract_address, account_address,
                                        method)
    assert_equal(expected_supply, res[0])


def compare_and_return_balance(node, smart_contract, contract_address, account_address, expected_balance):
    logging.info("Checking balance of 0x{}".format(account_address))
    new_balance = smart_contract.get_balance(node, account_address, contract_address)[0]
    logging.info("Expected balance: '{}', actual balance: '{}'".format(expected_balance, new_balance))
    assert_equal(expected_balance, new_balance)
    return new_balance


def compare_and_return_nat_balance(node, account_address, expected_balance):
    logging.info("Checking native balance of 0x{}".format(account_address))
    new_balance = get_native_balance(node, account_address)
    logging.info("Expected native balance: '{}', actual native balance: '{}'".format(expected_balance, new_balance))
    assert_equal(expected_balance, new_balance)
    return new_balance


def compare_ownerof(node, smart_contract, contract_address, sender_address, tokenid, expected_owner):
    method = 'ownerOf(uint256)'
    expected_owner = format_evm(expected_owner)
    logging.info("Checking owner of token {} of collection at {}...".format(tokenid, contract_address))
    res = contract_function_static_call(node, smart_contract, contract_address, sender_address, method,
                                        tokenid)
    logging.info("Expected owner: '{}', actual owner: '{}'".format(expected_owner, res[0]))
    assert_equal(format_evm(expected_owner), format_evm(res[0]))


class SCEvmERC721Contract(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=40)

    def run_test(self):
        sc_node = self.sc_nodes[0]
        ft_amount_in_zen = Decimal("330.22")
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = format_evm(ret["result"]["proposition"]["address"])

        logging.info(sc_node.rpc_eth_getBalance(str(self.evm_address), "latest"))
        logging.info(sc_node.rpc_eth_getBalance(other_address, "latest"))

        createLegacyTransaction(sc_node,
              fromAddress=format_eoa(self.evm_address),
              toAddress=format_eoa(other_address),
              value=convertZenToWei(30)
        )

        generate_next_block(sc_node, "first node", 1)

        logging.info(sc_node.rpc_eth_getBalance(other_address, "latest"))

        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)

        collection_name = "Test ERC721 Tokens"
        collection_symbol = "TET"
        collection_uri = "https://localhost:1337"
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, self.evm_address,
                                                       collection_name,
                                                       collection_symbol, collection_uri)

        # checking initial data
        compare_total_supply(sc_node, smart_contract, smart_contract_address, self.evm_address, 0)

        method = 'name()'
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method)
        assert_equal(collection_name, res[0])

        method = 'symbol()'
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method)
        assert_equal(collection_symbol, res[0])

        # get basic info about account
        last_nat_balance = get_native_balance(sc_node, self.evm_address)
        last_balance = compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address, 0)

        # test minting
        minted_ids_user1 = [1]
        minting_price = 1
        minting_amount = 1
        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                               minted_ids_user1[0], static_call=False, generate_block=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        gas_fee_paid, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_hash)

        compare_total_supply(sc_node, smart_contract, smart_contract_address, format_evm(self.evm_address), 1)
        compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[0],
                        self.evm_address)
        last_nat_balance = compare_and_return_nat_balance(sc_node, self.evm_address,
                                                          last_nat_balance - minting_price - gas_fee_paid)
        last_balance = compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                                  last_balance + minting_amount)

        # test pausing and then minting (failed)
        res = set_paused(sc_node, smart_contract, smart_contract_address, self.evm_address, paused=True,
                         static_call=True)
        assert_true(len(res) == 0)

        tx_hash = set_paused(sc_node, smart_contract, smart_contract_address, self.evm_address, paused=True)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        gas_fee_paid_1, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_hash)
        logging.info("gas_fee_paid_1 {}".format(gas_fee_paid_1))
        minted_ids_user1.append(2)

        exception_thrown = False
        try:
            exception_thrown = False
            mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                         minted_ids_user1[1], static_call=True)
        except EvmExecutionError as err:
            exception_thrown = True
            logging.info("Expected exception thrown: {}".format(err))

        finally:
            assert_true(exception_thrown, "Exception should have been thrown")
            pass

        # Contract paused, minting contract call should fail
        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                               minted_ids_user1[1], overrideGas=9000000)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status == 0, "Contract call failed")

        gas_fee_paid_2, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_hash)
        logging.info("gas_fee_paid {}".format(gas_fee_paid_2))

        compare_total_supply(sc_node, smart_contract, smart_contract_address, format_evm(self.evm_address), 1)
        last_nat_balance = compare_and_return_nat_balance(sc_node, self.evm_address,
                                                          last_nat_balance - gas_fee_paid_1 - gas_fee_paid_2)
        last_balance = compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                                  last_balance)

        # Test unpausing and then minting (success)
        res = set_paused(sc_node, smart_contract, smart_contract_address, self.evm_address, paused=False,
                         static_call=True)
        assert_true(len(res) == 0)
        tx_hash = set_paused(sc_node, smart_contract, smart_contract_address, self.evm_address, paused=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash)
        assert_true(status, "Contract call failed")

        gas_fee_paid_1, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_hash)

        res = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                           minted_ids_user1[1], static_call=True)
        assert_true(len(res) == 0)
        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                               minted_ids_user1[1])
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        gas_fee_paid_2, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_hash)

        compare_total_supply(sc_node, smart_contract, smart_contract_address, format_evm(self.evm_address), 2)
        compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[1],
                        self.evm_address)
        last_nat_balance = compare_and_return_nat_balance(sc_node, self.evm_address,
                                                          last_nat_balance - minting_price - gas_fee_paid_1 - gas_fee_paid_2)
        last_balance = compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                                  last_balance + minting_amount)

        # testing transfer
        method = 'transferFrom(address,address,uint256)'
        tx_hash = contract_function_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method,
                                         self.evm_address, other_address, minted_ids_user1[0])
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        gas_fee_paid, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_hash)
        minted_ids_user2 = [minted_ids_user1[0]]
        minted_ids_user1 = [minted_ids_user1[1]]

        compare_total_supply(sc_node, smart_contract, smart_contract_address, format_evm(self.evm_address), 2)

        compare_ownerof(sc_node, smart_contract, smart_contract_address, self.evm_address, minted_ids_user1[0],
                        self.evm_address)
        compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user2[0],
                        other_address)

        compare_and_return_nat_balance(sc_node, self.evm_address, last_nat_balance - gas_fee_paid)

        last_balance = compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                                  last_balance - 1)
        compare_and_return_balance(sc_node, smart_contract, smart_contract_address, other_address,
                                   last_balance)


if __name__ == "__main__":
    SCEvmERC721Contract().main()
