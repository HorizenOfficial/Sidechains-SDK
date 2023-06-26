#!/usr/bin/env python3
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, CallMethod, eoa_transfer, \
    deploy_smart_contract, contract_function_static_call, generate_block_and_get_tx_receipt, contract_function_call
from SidechainTestFramework.account.utils import computeForgedTxFee
from SidechainTestFramework.scutil import generate_next_blocks
from sc_evm_test_erc721 import get_native_balance, compare_and_return_balance, compare_total_supply, mint_payable, \
    compare_ownerof, compare_and_return_nat_balance
from test_framework.util import assert_equal, assert_true

"""
Check basic metamask-like functionality.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Do an EOA to EOA transfer via RPC
        - Check specific output fields for the different transaction types
        - Test minting of an NFT
        - Check minting results
        - Test transferring of an ERC20
        - Check minting results
"""


def eoa_assert_native_balance(node, address, expected_balance, tag='latest'):
    res = node.rpc_eth_getBalance(format_evm(address), tag)

    if "result" not in res:
        raise RuntimeError("Something went wrong, see {}".format(str(res)))

    res = res['result']
    balance = int(res[2:], 16)
    assert_equal(expected_balance, balance, "Actual balance did not match expected balance")


def compare_erc20_balance(node, smart_contract, contract_address, account_address, expected_balance):
    logging.info("Checking balance of 0x{}...".format(account_address))
    res = smart_contract.static_call(node, 'balanceOf(address)', account_address,
                                     fromAddress=account_address,
                                     toAddress=contract_address)
    logging.info("Expected balance: '{}', actual balance: '{}'".format(expected_balance, res[0]))
    assert_equal(res[0], expected_balance)


def transfer_erc20_tokens(node, smart_contract, contract_address, source_account, target_account, amount, *,
                          static_call=False):
    method = 'transfer(address,uint256)'
    if static_call:
        logging.info("Read-only calling {}: testing transfer of ".format(method) +
                     "{} tokens from 0x{} to 0x{}".format(amount, source_account, target_account))
        ret = contract_function_static_call(node, smart_contract, contract_address, source_account, method,
                                            target_account, amount)
    else:
        logging.info("Calling {}: transferring {} tokens from 0x{} to 0x{}".format(method, amount, source_account,
                                                                                   target_account))
        ret = contract_function_call(node, smart_contract, contract_address, source_account, method, target_account,
                                     amount)
    return ret


def compare_erc20_total_supply(node, smart_contract, contract_address, sender_address, expected_supply):
    logging.info("Checking total supply of token at 0x{}...".format(contract_address))
    res = smart_contract.static_call(node, 'totalSupply()', fromAddress=sender_address, toAddress=contract_address)
    logging.info("Expected supply: '{}', actual supply: '{}'".format(expected_supply, res[0]))
    assert_equal(res[0], expected_supply)


def deploy_erc20_smart_contract(node, smart_contract, from_address):
    logging.info("Deploying smart contract...")
    logging.info("Estimating gas for deployment...")
    estimated_gas = smart_contract.estimate_gas(node, 'constructor',
                                                fromAddress=from_address)
    logging.info("Estimated gas is {}".format(estimated_gas))
    tx_hash, address = smart_contract.deploy(node,
                                             fromAddress=from_address,
                                             gasLimit=estimated_gas)
    logging.info("Generating next block...")
    generate_next_blocks(node, "first node", 1)
    tx_receipt = node.rpc_eth_getTransactionReceipt(tx_hash)
    assert_equal(format_evm(tx_receipt['result']['contractAddress']), format_evm(address))
    logging.info("Smart contract deployed successfully to address 0x{}".format(address))
    return address


class SCEvmMetamaskTest(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def __check_tx_type_specific_outputs(self, tx_hash, tx_type):
        tx = self.sc_nodes[0].rpc_eth_getTransactionByHash(tx_hash)['result']
        keys = ['maxFeePerGas', 'maxPriorityFeePerGas', 'chainId']
        if tx_type == 'eip1559':
            assert_true(keys[0] in tx and keys[1] in tx)
        else:
            assert_true(keys[0] not in tx and keys[1] not in tx)
        if tx_type == 'legacy':
            assert_true(keys[2] not in tx)
        else:
            assert_true(keys[2] in tx)

    def run_test(self):
        # Setting up
        sc_node = self.sc_nodes[0]

        self.sc_ac_setup()

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = format_evm(ret["result"]["proposition"]["address"])

        initial_balance = int(sc_node.rpc_eth_getBalance(self.evm_address, "latest")['result'], 16)
        transfer_amount = 1

        # EOA transfers via RPC

        eoa_assert_native_balance(sc_node, self.evm_address, initial_balance)
        eoa_assert_native_balance(sc_node, other_address, 0)

        eoa_transfer(sc_node, self.evm_address, other_address, transfer_amount, static_call=True)
        tx_hash_legacy = eoa_transfer(sc_node, self.evm_address, other_address, transfer_amount,
                                      call_method=CallMethod.RPC_LEGACY)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash_legacy, True)
        assert_true(status, "Contract call failed")

        self.__check_tx_type_specific_outputs(tx_hash_legacy, 'legacy')

        (gas_used_legacy, _, _) = computeForgedTxFee(sc_node, tx_hash_legacy)

        eoa_assert_native_balance(sc_node, self.evm_address, initial_balance - (transfer_amount + gas_used_legacy))
        eoa_assert_native_balance(sc_node, other_address, transfer_amount)

        eoa_transfer(sc_node, self.evm_address, other_address, transfer_amount, static_call=True)
        tx_hash_eip155 = eoa_transfer(sc_node, self.evm_address, other_address, transfer_amount,
                                      call_method=CallMethod.RPC_EIP155)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash_eip155, True)
        assert_true(status, "Contract call failed")

        self.__check_tx_type_specific_outputs(tx_hash_eip155, 'eip155')

        (gas_used_eip155, _, _) = computeForgedTxFee(sc_node, tx_hash_eip155)

        eoa_assert_native_balance(sc_node, self.evm_address,
                                  initial_balance - (2 * transfer_amount + gas_used_legacy + gas_used_eip155))
        eoa_assert_native_balance(sc_node, other_address, 2 * transfer_amount)

        logging.info(initial_balance - (2 * transfer_amount + gas_used_legacy + gas_used_eip155))

        eoa_transfer(sc_node, self.evm_address, other_address, transfer_amount, static_call=True)
        tx_hash_eip1559 = eoa_transfer(sc_node, self.evm_address, other_address, transfer_amount,
                                       call_method=CallMethod.RPC_EIP1559)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash_eip1559, True)
        assert_true(status, "Contract call failed")

        self.__check_tx_type_specific_outputs(tx_hash_eip1559, 'eip1559')

        (gas_used_eip1559, _, _) = computeForgedTxFee(sc_node, tx_hash_eip1559)

        eoa_assert_native_balance(sc_node, other_address, 3 * transfer_amount)
        eoa_assert_native_balance(sc_node, self.evm_address, initial_balance - (
                3 * transfer_amount + gas_used_legacy + gas_used_eip155 + gas_used_eip1559))

        collection_name = "Test ERC721 Tokens"
        collection_symbol = "TET"
        collection_uri = "https://localhost:1337"

        global_call_method = CallMethod.RPC_EIP155
        logging.info("Running test with EIP155 calls")

        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, self.evm_address,
                                                       collection_name,
                                                       collection_symbol, collection_uri,
                                                       call_method=global_call_method)

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

        # Minting NFTs via RPC

        minted_ids_user1 = [1]
        minting_price = 1
        minting_amount = 1
        # check execution before submitting proper transaction
        res = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                           minted_ids_user1[0], static_call=True)
        assert_true(len(res) == 0)

        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                               minted_ids_user1[0], static_call=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        compare_total_supply(sc_node, smart_contract, smart_contract_address, self.evm_address, 1)
        compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[0],
                        self.evm_address)
        (gas_used, _, _) = computeForgedTxFee(sc_node, tx_hash)
        compare_and_return_nat_balance(sc_node, self.evm_address,
                                       last_nat_balance - minting_price - gas_used)
        compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                   last_balance + minting_amount)

        # Testing ERC20 functionality
        smart_contract_type = 'TestERC20'
        smart_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        smart_contract_address = deploy_erc20_smart_contract(sc_node, smart_contract, self.evm_address)

        compare_erc20_total_supply(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                   initial_balance)
        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address, initial_balance)

        transfer_amount = 99

        # Testing normal transfer
        check_res = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                          other_address,
                                          transfer_amount, static_call=True)
        assert_true(check_res[0])

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address, initial_balance)

        tx_hash = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                        other_address,
                                        transfer_amount, static_call=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                              initial_balance - transfer_amount)
        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)

        logging.info("Running test with legacy calls")

        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
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

        # Minting NFTs via RPC

        minted_ids_user1 = [1]
        minting_price = 1
        minting_amount = 1
        # check execution before submitting proper transaction
        res = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                           minted_ids_user1[0], static_call=True)
        assert_true(len(res) == 0)

        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                               minted_ids_user1[0], static_call=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        compare_total_supply(sc_node, smart_contract, smart_contract_address, self.evm_address, 1)
        compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[0],
                        self.evm_address)

        (gas_used, _, _) = computeForgedTxFee(sc_node, tx_hash)
        compare_and_return_nat_balance(sc_node, self.evm_address,
                                       last_nat_balance - minting_price - gas_used)
        compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                   last_balance + minting_amount)

        # Testing ERC20 functionality
        smart_contract_type = 'TestERC20'
        smart_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        smart_contract_address = deploy_erc20_smart_contract(sc_node, smart_contract, self.evm_address)

        compare_erc20_total_supply(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                   initial_balance)
        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address, initial_balance)

        transfer_amount = 99

        # Testing normal transfer
        check_res = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                          other_address,
                                          transfer_amount, static_call=True)
        assert_true(check_res[0])

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address, initial_balance)

        tx_hash = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                        other_address,
                                        transfer_amount, static_call=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                              initial_balance - transfer_amount)
        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)

        global_call_method = CallMethod.RPC_EIP1559
        logging.info("Running test with EIP1559 calls")

        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, self.evm_address,
                                                       collection_name,
                                                       collection_symbol, collection_uri,
                                                       call_method=global_call_method)

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

        # Minting NFTs via RPC

        minted_ids_user1 = [1]
        minting_price = 1
        minting_amount = 1
        # check execution before submitting proper transaction
        res = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                           minted_ids_user1[0], static_call=True)
        assert_true(len(res) == 0)

        tx_hash = mint_payable(sc_node, smart_contract, smart_contract_address, self.evm_address, minting_price,
                               minted_ids_user1[0], static_call=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        compare_total_supply(sc_node, smart_contract, smart_contract_address, self.evm_address, 1)
        compare_ownerof(sc_node, smart_contract, smart_contract_address, other_address, minted_ids_user1[0],
                        self.evm_address)
        (gas_used, _, _) = computeForgedTxFee(sc_node, tx_hash)
        compare_and_return_nat_balance(sc_node, self.evm_address,
                                       last_nat_balance - minting_price - gas_used)
        compare_and_return_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                   last_balance + minting_amount)

        # Testing ERC20 functionality
        smart_contract_type = 'TestERC20'
        smart_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        smart_contract_address = deploy_erc20_smart_contract(sc_node, smart_contract, self.evm_address)

        compare_erc20_total_supply(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                   initial_balance)
        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address, initial_balance)

        transfer_amount = 99

        # Testing normal transfer
        check_res = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                          other_address,
                                          transfer_amount, static_call=True)
        assert_true(check_res[0])

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address, initial_balance)

        tx_hash = transfer_erc20_tokens(sc_node, smart_contract, smart_contract_address, self.evm_address,
                                        other_address,
                                        transfer_amount, static_call=False)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Contract call failed")

        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, self.evm_address,
                              initial_balance - transfer_amount)
        compare_erc20_balance(sc_node, smart_contract, smart_contract_address, other_address, transfer_amount)


if __name__ == "__main__":
    SCEvmMetamaskTest().main()
