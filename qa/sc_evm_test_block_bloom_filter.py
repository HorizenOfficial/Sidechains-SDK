#!/usr/bin/env python3
import logging

from eth_bloom import BloomFilter
from eth_utils import keccak, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import CallMethod, deploy_smart_contract, \
    contract_function_static_call, contract_function_call, eoa_transfer
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import assert_equal, assert_true

global_call_method = CallMethod.RPC_EIP155

"""
Check block bloom filter with smart contract deployment and interaction.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info
    
Test:
    - Deploy the ERC20 smart contract
    - Transfer some tokens from one address to another (emit Transfer event) and mine a new block
    - Check if the Transfer event is present in bloom filter of latest block together with ERC20 contract address, sender and recipient
    - Make an EOA transfer and mine a new block
    - Check if the bloom filter is empty (no SC was called, so nothing was stored in bloom filter)
    - Deploy the ERC721 smart contract
    - Mint tokens to some address and mine a new block
    - Check if Transfer event was emitted together with sender (since it was minted it is address(0)), recipient and token id
"""


class SCEvmBlockBloomFilter(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        TOPIC_LENGTH = 64
        EMPTY_BLOOM_FILTER = "0x" + 512 * "0"

        sc_node = self.sc_nodes[0]
        self.sc_ac_setup()

        smart_contract_type = 'TestERC20'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        erc20_contract = SmartContract(smart_contract_type)

        initial_balance = 100
        erc20_address = deploy_smart_contract(sc_node, erc20_contract, self.evm_address)

        method = 'totalSupply()'
        res = contract_function_static_call(sc_node, erc20_contract, erc20_address, self.evm_address, method)
        assert_equal(res[0], initial_balance)

        method = 'balanceOf(address)'
        res = contract_function_static_call(sc_node, erc20_contract, erc20_address, self.evm_address, method,
                                            self.evm_address)
        assert_equal(res[0], initial_balance)

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        other_address = ret["result"]["proposition"]["address"]
        transfer_amount = 1

        # Test bloomfilter for ERC20 transfer
        method = 'transfer(address,uint256)'
        contract_function_call(sc_node, erc20_contract, erc20_address, self.evm_address, method, other_address,
                               transfer_amount)
        generate_next_block(sc_node, "first node")

        block = sc_node.rpc_eth_getBlockByNumber("latest", "false")
        bloom_filter = BloomFilter(int(block["result"]["logsBloom"], 16))

        assert_true(bytes.fromhex(remove_0x_prefix(erc20_address)) in bloom_filter,
                    "bloom filter should contain the address of emitting contract")

        transfer_event_signature = keccak(b"Transfer(address,address,uint256)")

        assert_true(transfer_event_signature in bloom_filter,
                    "bloom filter should contain the signature of transfer event")

        assert_true(bytes.fromhex("0" * (TOPIC_LENGTH - len(other_address)) + other_address) in bloom_filter,
                    "bloom filter should contain the address of receiver")

        evm_address_stripped = remove_0x_prefix(self.evm_address)
        assert_true(
            bytes.fromhex("0" * (TOPIC_LENGTH - len(evm_address_stripped)) + evm_address_stripped) in bloom_filter,
            "bloom filter should contain the address of sender")

        # Test bloomfilter for EOA transfer
        eoa_transfer(sc_node, self.evm_address, other_address, transfer_amount, static_call=False)
        generate_next_block(sc_node, "first node")

        block = sc_node.rpc_eth_getBlockByNumber("latest", "false")
        assert_equal(block["result"]["logsBloom"], EMPTY_BLOOM_FILTER, "bloom filter isn't empty")

        # Test bloomfilter for ERC721 transfer
        smart_contract_type = 'TestERC721'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        erc721_contract = SmartContract(smart_contract_type)
        erc721_address = deploy_smart_contract(sc_node, erc721_contract, self.evm_address,
                                               "Test",
                                               "Test", "Test")
        minted_ids_user1 = [1]
        minting_price = 1

        method = 'mint(uint256)'
        contract_function_call(sc_node, erc721_contract, erc721_address, self.evm_address, method, minted_ids_user1[0],
                               value=minting_price)
        generate_next_block(sc_node, "first node")

        block = sc_node.rpc_eth_getBlockByNumber("latest", "false")
        bloom_filter = BloomFilter(int(block["result"]["logsBloom"], 16))

        assert_true(bytes.fromhex(remove_0x_prefix(erc721_address)) in bloom_filter,
                    "bloom filter should contain the address of nft contract")

        transfer_event_signature = keccak(b"Transfer(address,address,uint256)")

        assert_true(transfer_event_signature in bloom_filter,
                    "bloom filter should contain the signature of transfer event")
        assert_true(
            bytes.fromhex("0" * (TOPIC_LENGTH - len(evm_address_stripped)) + evm_address_stripped) in bloom_filter,
            "bloom filter should contain the address of nft receiver")

        assert_true(
            bytes.fromhex(
                "0" * (TOPIC_LENGTH - len(str(minted_ids_user1[0]))) + str(minted_ids_user1[0])) in bloom_filter,
            "bloom filter should contain the nft token id")


if __name__ == "__main__":
    SCEvmBlockBloomFilter().main()
