#!/usr/bin/env python3

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import connect_sc_nodes, disconnect_sc_nodes_bi, assert_true

"""
Check net namespace rpc methods

Configuration:
    - 2 SC nodes
    - 1 MC node

Test:
    - Connect SC node 1 to SC node 2
    - Check that peer count is 1 for both nodes
    - Check that SC node 1 is listening for new connections
    - Check that SC node 2 is not listening for new connections
    - Disconnect SC node 1 from SC node 2
    - Check that peer count is 0 for both nodes
    - Check that both are listening for new connections
    - Check net_version
    
"""


class SCEvmRpcNetMethods(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=10, max_incoming_connections=1,
                         connect_nodes=False)

    def run_test(self):
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # Connect SC node 1 to SC node 2
        connect_sc_nodes(sc_node_1, 1)

        assert_true(int(sc_node_1.rpc_net_peerCount()['result'], 16) == 1)
        assert_true(int(sc_node_2.rpc_net_peerCount()['result'], 16) == 1)
        assert_true(sc_node_1.rpc_net_listening()['result'] is True)
        assert_true(sc_node_2.rpc_net_listening()['result'] is False)

        # Disconnect SC nodes
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)

        assert_true(int(sc_node_1.rpc_net_peerCount()['result'], 16) == 0)
        assert_true(int(sc_node_2.rpc_net_peerCount()['result'], 16) == 0)
        assert_true(sc_node_1.rpc_net_listening()['result'] is True)
        assert_true(sc_node_2.rpc_net_listening()['result'] is True)

        assert_true(int(sc_node_1.rpc_net_version()['result']) == 1000000001)


if __name__ == "__main__":
    SCEvmRpcNetMethods().main()
