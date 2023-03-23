#!/usr/bin/env python3
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from test_framework.util import assert_equal, assert_false, assert_true


class SCEvmRpcWeb3Sha3(AccountChainSetup):
    """
    **Test for web3_sha3 RPC method**

    Configuration:
        - 1 SC node
        - 1 MC node

    Test:
        - call web3_sha3 RPC method with data to be hashed
        - verify the response is as expected
    """

    def __init__(self):
        super().__init__(withdrawalEpochLength=50)

    def validate(self, response, expected=None):
        if expected is not None:
            assert_false("error" in response)
            assert_equal(expected, response["result"], "unexpected response")
        else:
            assert_true("error" in response, response['error'])

    def run_test(self):
        self.sc_ac_setup()
        sc_node = self.sc_nodes[0]

        self.validate(sc_node.rpc_web3_sha3("0x68656c6c6f20776f726c64"),
                      "0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad")
        self.validate(sc_node.rpc_web3_sha3("0x"), "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
        self.validate(sc_node.rpc_web3_sha3("0x68656c6c6f20776f726c642"))
        self.validate(sc_node.rpc_web3_sha3("0x68656c6c6f20776f726c6w"))
        self.validate(sc_node.rpc_web3_sha3("68656c6c6f20776f726c6w"))
        self.validate(sc_node.rpc_web3_sha3("68656c6c6f20776f726c64"))
        self.validate(sc_node.rpc_web3_sha3(""))


if __name__ == "__main__":
    SCEvmRpcWeb3Sha3().main()
