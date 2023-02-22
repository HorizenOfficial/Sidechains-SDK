#!/usr/bin/env python3

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import connect_sc_nodes, disconnect_sc_nodes_bi, assert_true

"""
Check net and web3_clientVersion rpc methods

Configuration:
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    - Connect SC node 2 to SC node 1
    - Check that peer count is 1
    - Check that SC node 1 is listening for new connections
    - Disconnect SC node 2 from SC node 1
    - Check that peer count is 0
    - Check that web3_clientVersion responds
    
"""


class SCEvmRpcNetWeb3Methods(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=10)

    def run_test(self):
        sc_node_1 = self.sc_nodes[0]

        # Connect SC node
        connect_sc_nodes(sc_node_1, 1)

        assert_true(int(sc_node_1.rpc_net_peerCount()['result'], 16) == 1)
        assert_true(sc_node_1.rpc_net_listening()['result'] is True)

        # Disconnect SC node
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        assert_true(int(sc_node_1.rpc_net_peerCount()['result'], 16) == 0)
        assert_true(sc_node_1.rpc_net_listening()['result'] is True)

        # Check web3_clientVersion exists
        assert_true(len(sc_node_1.rpc_web3_clientVersion()['result']) > 6)


if __name__ == "__main__":
    SCEvmRpcNetWeb3Methods().main()
