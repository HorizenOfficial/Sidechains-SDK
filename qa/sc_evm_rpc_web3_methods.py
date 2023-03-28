#!/usr/bin/env python3
import re

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import assert_true, assert_false, assert_equal

"""
Check web3 namespace rpc methods

Configuration:
    - 1 SC node
    - 1 MC node

Test:
    - Check web3_clientVersion
    - Check web3_sha3
    
"""


class SCEvmRpcWeb3Methods(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=1, withdrawalEpochLength=10)

    def validate(self, response, expected):
        assert_false("error" in response)
        assert_equal(expected, response["result"], "unexpected response")


    def run_test(self):
        sc_node_1 = self.sc_nodes[0]

        # Check web3_clientVersion
        clientVersion = str(sc_node_1.rpc_web3_clientVersion()['result'])
        assert_true(clientVersion.startswith('sidechains-sdk/'))
        clientVersion = clientVersion.split("/")
        # Check that sdk version has at least three digits
        assert_true(re.search(r'\d.*\d.*\d', clientVersion[1]))
        # Check that architecture is present and is not default value
        assert_true(len(clientVersion[2]) > 0 and clientVersion[2] != 'dev')
        # Check that jdk version has at least 2 digits
        assert_true(re.search(r'jdk\d{2,}', clientVersion[3]))

        # Check web3_sha3 method
        self.validate(sc_node_1.rpc_web3_sha3("0x68656c6c6f20776f726c64"),
                      "0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad")
        self.validate(sc_node_1.rpc_web3_sha3("68656c6c6f20776f726c64"),
                      "0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad")
        self.validate(sc_node_1.rpc_web3_sha3("0xZZXX68656c6c6f20776f726c64"),
                      "0x8bc0488d7a81d4c07855cf17f63c41b5f6cc6c0d15c764ce43ab668c9e6809ba")
        self.validate(sc_node_1.rpc_web3_sha3("0x"),
                      "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
        self.validate(sc_node_1.rpc_web3_sha3(""),
                      "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
        self.validate(sc_node_1.rpc_web3_sha3("text"),
                      "0x2c7a9a0b269b5b740e242917d5b704ce4329a174526cd76ba1f042dfd88795bb")


if __name__ == "__main__":
    SCEvmRpcWeb3Methods().main()
