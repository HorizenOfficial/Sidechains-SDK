#!/usr/bin/env python3
import logging
from decimal import Decimal

from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import contract_function_static_call, contract_function_call, \
    generate_block_and_get_tx_receipt, eoa_transfer
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei
from sc_evm_test_contract_contract_deployment_and_interaction import deploy_smart_contract
from sc_evm_test_erc721 import compare_and_return_nat_balance
from test_framework.util import assert_equal, assert_true, fail

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


class SCEvmContractDeploymentCreate2(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=50)

    def run_test(self):
        sc_node = self.sc_nodes[0]

        self.sc_ac_setup()

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
        evm_hex_address = to_checksum_address(self.evm_address)

        # Deploy Factory smart contract
        factory_contract_address = deploy_smart_contract(sc_node, factory_contract, evm_hex_address)

        # Get address with salt, that is later a smart contract
        simple_wallet_contract_address = \
            contract_function_static_call(sc_node, factory_contract, factory_contract_address,
                                          evm_hex_address,
                                          'getAddress(uint256)', salt)[0]

        # Transfer some funds to the address, that is later a smart contract
        amount_zen = convertZenniesToWei(convertZenToZennies(Decimal(12.34)))
        tx_hash = eoa_transfer(sc_node, self.evm_address, simple_wallet_contract_address, amount_zen)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Transaction failed")
        compare_and_return_nat_balance(sc_node, simple_wallet_contract_address, amount_zen)

        # CREATE2 deployment of Simple Wallet via Factory contract call
        method = 'deploy(uint256)'
        method_args = salt
        tx_hash = contract_function_call(sc_node, factory_contract, factory_contract_address, self.evm_address, method,
                                         method_args)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Transaction failed")

        # Check balance of Simple Wallet contract has the amount sent before
        balance = contract_function_static_call(sc_node, simple_wallet_contract, simple_wallet_contract_address,
                                                evm_hex_address, 'getBalance()')

        assert_equal(str(amount_zen), str(balance[0]))

        # Set storage value of Simple Wallet contract and check if it was set
        method = 'setStorageValue(string)'
        method_args = '5'
        tx_hash = contract_function_call(sc_node, simple_wallet_contract, simple_wallet_contract_address,
                                         self.evm_address,
                                         method,
                                         method_args)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Transaction failed")

        method = 'getStorageValue()'
        storage_value = contract_function_static_call(sc_node, simple_wallet_contract, simple_wallet_contract_address,
                                                      evm_hex_address, method)

        assert_equal(str(method_args), str(storage_value[0]))

        # Call destroy method of Simple Wallet contract with sending all funds to evm_address
        method = 'destroy(address)'
        method_args = evm_hex_address
        tx_hash = contract_function_call(sc_node, simple_wallet_contract, simple_wallet_contract_address,
                                         self.evm_address,
                                         method, method_args)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Transaction failed")

        # CREATE2 deployment of Simple Wallet via Factory contract call
        method = 'deploy(uint256)'
        method_args = salt
        tx_hash = contract_function_call(sc_node, factory_contract, factory_contract_address, self.evm_address, method,
                                         method_args)
        status = generate_block_and_get_tx_receipt(sc_node, tx_hash, True)
        assert_true(status, "Transaction failed")

        # Check that previous smart contract storage was cleared
        method = 'getStorageValue()'
        storage_value = contract_function_static_call(sc_node, simple_wallet_contract, simple_wallet_contract_address,
                                                      evm_hex_address, method)

        assert_equal('', storage_value[0])

        # Check that we can not deploy to the same address again
        try:
            contract_function_call(sc_node, factory_contract, factory_contract_address, self.evm_address, method,
                                   method_args)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("Deploying a contract to the same address again should fail")


if __name__ == "__main__":
    SCEvmContractDeploymentCreate2().main()
