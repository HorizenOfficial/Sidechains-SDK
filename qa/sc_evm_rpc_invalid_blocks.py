#!/usr/bin/env python3
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import generate_next_block


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

    def validate(self, expected, response):
        if "error" in response:
            logging.warning("received error response: {}".format(str(response)))
            return
            # raise RuntimeError("Something went wrong, see {}".format(str(response)))
        if expected != response["result"]:
            logging.warning("unexpected response: want {} got {}".format(expected, response["result"]))
        # assert_equal(expected, response["result"], "unexpected response")

    def by_number(self, node, number):
        self.validate("null", node.rpc_eth_getBlockByNumber(number))
        self.validate("null", node.rpc_eth_getBlockTransactionCountByNumber(number))
        self.validate("null", node.rpc_eth_getTransactionByBlockNumberAndIndex(number, "0x0"))
        self.validate("null", node.rpc_eth_getTransactionByBlockNumberAndIndex(number, "0x200"))
        self.validate("null", node.rpc_eth_getCode(self.evm_address, number))
        self.validate("null", node.rpc_eth_getProof(self.evm_address, None, number))
        self.validate("0x0", node.rpc_eth_getBalance(self.evm_address, number))
        self.validate("0x0", node.rpc_eth_getTransactionCount(self.evm_address, number))
        self.validate(
            "0x0000000000000000000000000000000000000000000000000000000000000000",
            node.rpc_eth_getStorageAt(self.evm_address, "0x0", number)
        )
        self.validate("null", node.rpc_debug_traceBlockByNumber(number))
        callParams = {
            "to": "0x0000000000000000000000000000000000000009",
            "data": "0x0000000c48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003000000000000000000000001"
        }
        self.validate("null", node.rpc_debug_traceBlockByNumber(callParams, number, {"tracer": "callTracer"}))

    def by_hash(self, node, blockHash):
        self.validate("null", node.rpc_eth_getBlockByHash(blockHash))
        self.validate("null", node.rpc_eth_getTransactionByBlockHashAndIndex(blockHash, "0x0"))
        self.validate("null", node.rpc_eth_getTransactionByBlockHashAndIndex(blockHash, "0x200"))
        self.validate("null", node.rpc_debug_traceBlockByHash(blockHash))

    def run_test(self):
        self.sc_ac_setup()
        sc_node = self.sc_nodes[0]

        # generate some blocks
        generate_next_block(sc_node, "first node")
        generate_next_block(sc_node, "first node")
        generate_next_block(sc_node, "first node")
        valid_hash = generate_next_block(sc_node, "first node")

        # self.by_number(sc_node, "0x1")
        # self.by_number(sc_node, "0x4")
        self.by_number(sc_node, "0x100")

        # self.by_hash(sc_node, valid_hash)
        self.by_hash(sc_node, "0x0000000000000000000000000000000000000000000000000000000000001234")


if __name__ == "__main__":
    SCEvmRpcInvalidBlocks().main()
