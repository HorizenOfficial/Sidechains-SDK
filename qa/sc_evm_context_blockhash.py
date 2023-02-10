#!/usr/bin/env python3
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import (
    contract_function_static_call, deploy_smart_contract,
)
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import assert_equal

"""
Test EVM Contract with BLOCKHASH instruction.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Compile and deploy test contract: BlockHash
    - Mine a few blocks
    - Verify block hashes returned by contract function
    - Test behavior when eth_call with different block contexts,
        i.e. "pending", "latest" or a block number passed to eth_call
"""


class SCEvmContextBlockHash(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=100)

    def deploy(self, contract_name):
        logging.info(f"Creating smart contract utilities for {contract_name}")
        contract = SmartContract(contract_name)
        logging.info(contract)
        contract_address = deploy_smart_contract(self.sc_nodes[0], contract, self.evm_address)
        return contract, contract_address

    def read_and_verify_blockhash(self, contract, address, offset, expected_block_hash, tag):
        read_value = contract_function_static_call(
            self.sc_nodes[0], contract, address, self.evm_address, "get(uint256)", offset, tag=tag
        )
        actual_block_hash = bytes.hex(read_value[0])
        assert_equal(expected_block_hash, actual_block_hash, "unexpected value")

    def run_test(self):
        self.sc_ac_setup()

        zero_hash = "0000000000000000000000000000000000000000000000000000000000000000"

        # deploy test contract
        contract, address = self.deploy("BlockHash")

        # mine some blocks and get the block hashes
        # the keys are offsets relative to the current block height, i.e. "latest"
        block_hashes = {
            3: generate_next_block(self.sc_nodes[0], "first node"),
            2: generate_next_block(self.sc_nodes[0], "first node"),
            1: generate_next_block(self.sc_nodes[0], "first node"),
            0: generate_next_block(self.sc_nodes[0], "first node")
        }

        # get current block height
        height = self.sc_nodes[0].block_best()["result"]["height"]
        logging.info("current block height: {}".format(height))

        # verify block hashes returned by the contract relative to "pending"
        for offset, block_hash in block_hashes.items():
            # all blocks will be "older" than the pending block, we should be able to get all the block hashes here
            offset_to_pending = offset + 1
            self.read_and_verify_blockhash(contract, address, offset_to_pending, block_hash, "pending")

        # verify block hashes returned by the contract relative to "latest"
        for offset, block_hash in block_hashes.items():
            # BLOCKHASH opcode does not allow fetching the hash of the current block (or future blocks)
            # doing that will always result in the zero hash
            if offset <= 0:
                offset = 0
                block_hash = zero_hash
            self.read_and_verify_blockhash(contract, address, offset, block_hash, "latest")

        # verify block hashes returned by the contract relative to height-2
        reference_offset = 2
        for offset, block_hash in block_hashes.items():
            # BLOCKHASH opcode does not allow fetching the hash of the current block (or future blocks)
            # doing that will always result in the zero hash
            offset_to_reference = offset - reference_offset
            if offset_to_reference <= 0:
                offset_to_reference = 0
                block_hash = zero_hash
            self.read_and_verify_blockhash(
                contract, address, offset_to_reference, block_hash, hex(height - reference_offset)
            )


if __name__ == "__main__":
    SCEvmContextBlockHash().main()
