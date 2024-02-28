#!/usr/bin/env python3
import logging
from decimal import Decimal

from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, generate_block_and_get_tx_receipt
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block
from httpCalls.block.findBlockByID import http_block_findById
from test_framework.util import assert_equal

"""
Check an EVM PREVRANDAO Opcode.
For given AccountBlock B, we expect PREVRANDAO == B.header.vrfOutput
For tag `pending`, we expect PREVRANDAO BestBlock.header.vrfOutput

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the smart contract:
        - Deploy the smart contract
        - Test `getRandomByCallingPrevrandao()` and getRandomByCallingDifficulty' methods within:
          * `latest` block
          * `pending` block
          * `safe` block
        - Results expected to be the equal and fits blockheader vrfOutput value.
        - Test: `random` is used as a part of Tx -> Forger associates the same VrfOutput as the one in the AccountState.
        - Check that EthereumBlockView returned by RPC contains `mixHash` fields equals to vrfOutput
"""


class SCEvmPrevrandao(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def __get_random_by_prevrandao(self, smart_contract, address, tx_sender, expected_value, tag):
        res = smart_contract.static_call(self.sc_nodes[0],
                                         'getRandomByCallingPrevrandao()',
                                         fromAddress=tx_sender,
                                         toAddress=address,
                                         gasPrice=900000000,
                                         tag=tag)

        logging.info("Expected random value: \"{}\", actual value: \"{}\"".format(expected_value, res[0]))
        assert_equal(expected_value, res[0], "Different random value retrieved by using PREVRANDAO")

    def __get_random_by_difficulty(self, smart_contract, address, tx_sender, expected_value, tag):
        res = smart_contract.static_call(self.sc_nodes[0],
                                         'getRandomByCallingDifficulty()',
                                         fromAddress=tx_sender,
                                         toAddress=address,
                                         gasPrice=900000000,
                                         tag=tag)

        logging.info("Expected random value: \"{}\", actual value: \"{}\"".format(expected_value, res[0]))
        assert_equal(expected_value, res[0], "Different random value retrieved by using DIFFICULTY")

    def __persist_random(self, smart_contract, address, tx_sender):
        node = self.sc_nodes[0]
        logging.info("Setting smart contract storage persistRandom")
        return smart_contract.call_function(node, 'persistRandom()', fromAddress=tx_sender, gasLimit=10000000,
                                            toAddress=address)

    def __get_persistent_random(self, smart_contract, address, tx_sender, expected_value, tag):
        res = smart_contract.static_call(self.sc_nodes[0],
                                         'persistentRandom()',
                                         fromAddress=tx_sender,
                                         toAddress=address,
                                         gasPrice=900000000,
                                         tag=tag)

        logging.info("Expected persistent random value: \"{}\", actual value: \"{}\"".format(expected_value, res[0]))
        assert_equal(expected_value, res[0], "Different persistent random value retrieved")

    def run_test(self):
        sc_node = self.sc_nodes[0]
        ft_amount_in_zen = Decimal("3000")
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        smart_contract_type = 'PrevrandaoTestContract'
        logging.info(f"Creating smart contract utilities for {smart_contract_type}")
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)

        estimated_gas = smart_contract.estimate_gas(sc_node, 'constructor',
                                                    fromAddress=self.evm_address)
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node,
                                                                fromAddress=self.evm_address,
                                                                gasLimit=estimated_gas)

        tx_receipt = generate_block_and_get_tx_receipt(sc_node, tx_hash)['result']
        assert_equal('0x1', tx_receipt['status'], 'Transaction unexpectedly failed')
        assert_equal(format_evm(tx_receipt['contractAddress']), smart_contract_address)

        # Generate some more SC blocks to be able to interact with smart contract for  "safe" tag
        safe_block_id = generate_next_block(sc_node, "first node")

        generate_next_blocks(sc_node, "first node", 50)
        # mine an mc block otherwise we have SC a long chain span (limit is 100) without mc block references, and the forging
        # of new SC blocks would be paused
        self.nodes[0].generate(1)

        generate_next_blocks(sc_node, "first node", 50)

        # Get best block info and expected random value
        best_block = sc_node.block_best()["result"]
        best_block_height = best_block["height"]
        best_block_vrf_output = best_block["block"]["header"]["vrfOutput"]["bytes"]
        expected_random = int('0x' + best_block_vrf_output, 16)

        # Test against the `latest`
        self.__get_random_by_prevrandao(smart_contract, smart_contract_address, self.evm_address,
                                        expected_random, 'latest')
        self.__get_random_by_difficulty(smart_contract, smart_contract_address, self.evm_address,
                                        expected_random, 'latest')

        # Test against the `pending`
        self.__get_random_by_prevrandao(smart_contract, smart_contract_address, self.evm_address,
                                        expected_random, 'pending')
        self.__get_random_by_difficulty(smart_contract, smart_contract_address, self.evm_address,
                                        expected_random, 'pending')

        # Test against the `safe`
        safe_block = http_block_findById(sc_node, safe_block_id)
        safe_block_vrf_output = safe_block["block"]["header"]["vrfOutput"]["bytes"]
        expected_random = int(add_0x_prefix(safe_block_vrf_output), 16)

        self.__get_random_by_prevrandao(smart_contract, smart_contract_address, self.evm_address,
                                        expected_random, 'safe')
        self.__get_random_by_difficulty(smart_contract, smart_contract_address, self.evm_address,
                                        expected_random, 'safe')

        # Create Tx to store persistent random
        res = self.__persist_random(smart_contract, smart_contract_address, self.evm_address)

        # Generate block
        # We expect that Forger defines Random in the same way as the AccountState
        last_block_id = generate_next_block(sc_node, "first node")
        tx_receipt = sc_node.rpc_eth_getTransactionReceipt(res)

        # Check that Tx applied successfully
        status = int(tx_receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")

        # Verify persistent random
        last_block = http_block_findById(sc_node, last_block_id)
        last_block_vrf_output = last_block["block"]["header"]["vrfOutput"]["bytes"]
        expected_random = int(add_0x_prefix(last_block_vrf_output), 16)

        self.__get_persistent_random(smart_contract, smart_contract_address, self.evm_address, expected_random, 'latest')

        last_block_eth = sc_node.rpc_eth_getBlockByHash(add_0x_prefix(last_block_id), False)['result']
        if last_block_eth is None:
            raise Exception('Unexpected error: block not found {}'.format(last_block_id))

        assert_equal(add_0x_prefix(last_block_vrf_output), last_block_eth['mixHash'],
                     "EthBlockView mixHash is different to AccountBlockHeader vrfOutput")


if __name__ == "__main__":
    SCEvmPrevrandao().main()
