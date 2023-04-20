#!/usr/bin/env python3
import logging
from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, \
    contract_function_static_call
from test_framework.util import assert_equal, assert_false, assert_true

"""
Check that we corretly manage the EIP-1898 inputs for the following rpc methods:
    - eth_getBalance
    - eth_getStorageAt
    - eth_getTransactionCount
    - eth_getCode
    - eth_call
    - eth_getProof
EIP-1898 details at: https://eips.ethereum.org/EIPS/eip-1898

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info
    - Deploy an ERC20 contract 
    - Test the previous rpc methods with EIP-1898 inputs

"""

def assert_response(response, expected=None, expect_error=False):
    if expect_error:
        assert_true("error" in response)
    else:
        assert_false("error" in response)
        assert_equal(expected, response["result"], "unexpected response")

class SCEvmEIP1898(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        sc_node = self.sc_nodes[0]

        self.sc_ac_setup()

        # -----------------------------------------------------------------------------
        # Deploy Smart Contract
        smart_contract_type = 'TestERC20'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)

        initial_balance = 100
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, self.evm_address)

        # eth_call
        # call smart contract method with tag latest
        method = 'totalSupply()'
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method, tag='latest')
        assert_equal(initial_balance, res[0])
        # call smart contract method with tag block number
        block_number = sc_node.block_best()["result"]["height"]
        hex_block_number = add_0x_prefix(hex(block_number))
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method, tag=hex_block_number)
        assert_equal(initial_balance, res[0])

        # call smart contract method with EIP-1898 input (block number)
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method, tag=hex_block_number, eip1898=True)
        assert_equal(initial_balance, res[0])
        # call smart contract method with EIP-1898 input (block hash)
        block_hash = sc_node.rpc_eth_getBlockByNumber(hex_block_number, False)['result']['hash']
        res = contract_function_static_call(sc_node, smart_contract, smart_contract_address, self.evm_address, method, tag=block_hash, eip1898=True, isBlockHash=True)
        assert_equal(initial_balance, res[0])

        # EIP-1898 inputs
        eip1898_blockNumber = {
            "blockNumber": hex_block_number
        }
        eip1898_blockHash = {
            "blockHash": block_hash
        }
        eip1898_invalidInput_blockNumber = {
            "blockNumber": "0xffff",
        }
        eip1898_invalidInput_blockHash = {
            "blockHash": "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        }
        eip1898_invalidInput_bothFields = {
            "blockNumber": hex_block_number,
            "blockHash": block_hash
        }

        # eth_getBalance
        check_balance = int(sc_node.rpc_eth_getBalance(self.evm_address)['result'], 16)
        balance = int(sc_node.rpc_eth_getBalance(self.evm_address, 'latest')['result'], 16)
        assert_equal(check_balance, balance)
        balance = int(sc_node.rpc_eth_getBalance(self.evm_address, hex_block_number)['result'], 16)
        assert_equal(check_balance, balance)
        balance = int(sc_node.rpc_eth_getBalance(self.evm_address, eip1898_blockNumber)['result'], 16)
        assert_equal(check_balance, balance)
        balance = int(sc_node.rpc_eth_getBalance(self.evm_address, eip1898_blockHash)['result'], 16)
        assert_equal(check_balance, balance)
        assert_response(sc_node.rpc_eth_getBalance(self.evm_address, eip1898_invalidInput_blockNumber), expect_error=True)
        assert_response(sc_node.rpc_eth_getBalance(self.evm_address, eip1898_invalidInput_blockHash), expect_error=True)
        assert_response(sc_node.rpc_eth_getBalance(self.evm_address, eip1898_invalidInput_bothFields), expect_error=True)

        # eth_getStorageAt
        check_storage = sc_node.rpc_eth_getStorageAt(smart_contract_address, 1)['result']
        storage = sc_node.rpc_eth_getStorageAt(smart_contract_address, 1, 'latest')['result']
        assert_equal(check_storage, storage)
        storage = sc_node.rpc_eth_getStorageAt(smart_contract_address, 1, hex_block_number)['result']
        assert_equal(check_storage, storage)
        storage = sc_node.rpc_eth_getStorageAt(smart_contract_address, 1, eip1898_blockNumber)['result']
        assert_equal(check_storage, storage)
        storage = sc_node.rpc_eth_getStorageAt(smart_contract_address, 1, eip1898_blockHash)['result']
        assert_equal(check_storage, storage)
        assert_response(sc_node.rpc_eth_getStorageAt(self.evm_address, eip1898_invalidInput_blockNumber), expect_error=True)
        assert_response(sc_node.rpc_eth_getStorageAt(self.evm_address, eip1898_invalidInput_blockHash), expect_error=True)
        assert_response(sc_node.rpc_eth_getStorageAt(self.evm_address, eip1898_invalidInput_bothFields), expect_error=True)
        print(storage)

        # eth_getTransactionCount
        check_nonce = int(sc_node.rpc_eth_getTransactionCount(self.evm_address)['result'], 16)
        nonce = int(sc_node.rpc_eth_getTransactionCount(self.evm_address, 'latest')['result'], 16)
        assert_equal(check_nonce, nonce)
        nonce = int(sc_node.rpc_eth_getTransactionCount(self.evm_address, hex_block_number)['result'], 16)
        assert_equal(check_nonce, nonce)
        nonce = int(sc_node.rpc_eth_getTransactionCount(self.evm_address, eip1898_blockNumber)['result'], 16)
        assert_equal(check_nonce, nonce)
        nonce = int(sc_node.rpc_eth_getTransactionCount(self.evm_address, eip1898_blockHash)['result'], 16)
        assert_equal(check_nonce, nonce)
        assert_response(sc_node.rpc_eth_getTransactionCount(self.evm_address, eip1898_invalidInput_blockNumber), expect_error=True)
        assert_response(sc_node.rpc_eth_getTransactionCount(self.evm_address, eip1898_invalidInput_blockHash), expect_error=True)
        assert_response(sc_node.rpc_eth_getTransactionCount(self.evm_address, eip1898_invalidInput_bothFields), expect_error=True)

        # eth_getCode
        check_code = sc_node.rpc_eth_getCode(smart_contract_address)['result']
        code = sc_node.rpc_eth_getCode(smart_contract_address, 'latest')['result']
        assert_equal(check_code, code)
        code = sc_node.rpc_eth_getCode(smart_contract_address, hex_block_number)['result']
        assert_equal(check_code, code)
        code = sc_node.rpc_eth_getCode(smart_contract_address, eip1898_blockNumber)['result']
        assert_equal(check_code, code)
        code = sc_node.rpc_eth_getCode(smart_contract_address, eip1898_blockHash)['result']
        assert_equal(check_code, code)
        assert_response(sc_node.rpc_eth_getCode(self.evm_address, eip1898_invalidInput_blockNumber), expect_error=True)
        assert_response(sc_node.rpc_eth_getCode(self.evm_address, eip1898_invalidInput_blockHash), expect_error=True)
        assert_response(sc_node.rpc_eth_getCode(self.evm_address, eip1898_invalidInput_bothFields), expect_error=True)

        check_proof = sc_node.rpc_eth_getProof(smart_contract_address, [storage])['result']
        proof = sc_node.rpc_eth_getProof(smart_contract_address, [storage], 'latest')['result']
        assert_equal(check_proof, proof)
        proof = sc_node.rpc_eth_getProof(smart_contract_address, [storage], hex_block_number)['result']
        assert_equal(check_proof, proof)
        proof = sc_node.rpc_eth_getProof(smart_contract_address, [storage], eip1898_blockNumber)['result']
        assert_equal(check_proof, proof)
        proof = sc_node.rpc_eth_getProof(smart_contract_address, [storage], eip1898_blockHash)['result']
        assert_equal(check_proof, proof)
        print(sc_node.rpc_eth_getProof(self.evm_address, [storage], eip1898_invalidInput_blockHash))
        assert_response(sc_node.rpc_eth_getProof(self.evm_address, [storage], eip1898_invalidInput_blockHash), expect_error=True)
        assert_response(sc_node.rpc_eth_getProof(self.evm_address, [storage], eip1898_invalidInput_bothFields), expect_error=True)


if __name__ == "__main__":
    SCEvmEIP1898().main()
