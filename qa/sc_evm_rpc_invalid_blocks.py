#!/usr/bin/env python3
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import assert_equal, assert_false, assert_true


def assert_response(response, expected=None, expect_error=False):
    if expect_error:
        assert_true("error" in response)
    else:
        assert_false("error" in response)
        assert_equal(expected, response["result"], "unexpected response")


class SCEvmRpcInvalidBlocks(AccountChainSetup):
    """
    **Test for invalid blocks**

    Configuration:
        - 1 SC node
        - 1 MC node

    Test:
        - call all RPC functions that accept either a block hash or block number
        - use parameters that refer to a block that does not exist (non-existing block hash or block number)
        - verify the response is as expected
    """

    def __init__(self):
        super().__init__(withdrawalEpochLength=50)

    def by_number(self, node, number):
        assert_response(node.rpc_eth_getBlockByNumber(number, False))
        assert_response(node.rpc_eth_getBlockTransactionCountByNumber(number))
        assert_response(node.rpc_eth_getTransactionByBlockNumberAndIndex(number, "0x0"))
        assert_response(node.rpc_eth_getTransactionByBlockNumberAndIndex(number, "0x200"))
        assert_response(node.rpc_eth_getCode(self.evm_address, number), expect_error=True)
        assert_response(node.rpc_eth_getProof(self.evm_address, [], number))
        assert_response(node.rpc_eth_getBalance(self.evm_address, number), expect_error=True)
        assert_response(node.rpc_eth_getTransactionCount(self.evm_address, number), expect_error=True)
        assert_response(node.rpc_debug_traceBlockByNumber(number))

    def by_hash(self, node, block_hash):
        assert_response(node.rpc_eth_getBlockByHash(block_hash, False))
        assert_response(node.rpc_eth_getTransactionByBlockHashAndIndex(block_hash, "0x0"))
        assert_response(node.rpc_eth_getTransactionByBlockHashAndIndex(block_hash, "0x200"))
        assert_response(node.rpc_debug_traceBlockByHash(block_hash))

    def run_test(self):
        self.sc_ac_setup()
        sc_node = self.sc_nodes[0]

        # generate some blocks
        generate_next_block(sc_node, "first node")
        generate_next_block(sc_node, "first node")
        generate_next_block(sc_node, "first node")
        generate_next_block(sc_node, "first node")

        # execute calls with non-existent block numbers and hashes
        self.by_number(sc_node, "0x100")
        self.by_number(sc_node, "0x1234")
        self.by_hash(sc_node, "0x0000000000000000000000000000000000000000000000000000000000001234")
        self.by_hash(sc_node, "0x00000000000000000000000000000000000000000000000000000000deadbeef")


if __name__ == "__main__":
    SCEvmRpcInvalidBlocks().main()
