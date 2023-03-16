#!/usr/bin/env python3
import re

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import assert_true

"""
Check web3 namespace rpc methods

Configuration:
    - 1 SC node
    - 1 MC node

Test:
    - Check web3_clientVersion
    
"""


class SCEvmRpcWeb3Methods(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=1, withdrawalEpochLength=10)

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


if __name__ == "__main__":
    SCEvmRpcWeb3Methods().main()
