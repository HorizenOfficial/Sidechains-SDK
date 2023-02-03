#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes, sc_p2p_port
from httpCalls.peer.getConnectedPeers import get_connected_peers
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index

"""
Make sure that all the nodes are connected to each other starting from a star topology.

Configuration: start 10 sidechain nodes. Each node is declaring their own address

Test:
    - connect all the nodes in a ring shape
    - wait for 60s to let the nodes share the peers among them
    - check that every node is connected to all other
"""


class NetRingOfNodes(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 10

    def __init__(self):
        self.sc_nodes_bootstrap_info = None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]

        sc_node_configuration = []

        for index in range(0, self.number_of_sidechain_nodes):
            sc_node_configuration.append(
                SCNodeConfiguration(
                    MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                    ),
                    declared_address=f'127.0.0.1:{sc_p2p_port(index)}',
                    get_peers_interval='5s'
                )
            )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000), *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        sc_nodes = self.sc_nodes

        for index in range(0, len(sc_nodes) - 1):
            try:
                connect_sc_nodes(sc_nodes[index], index + 1)
            except:
                pass

        connect_sc_nodes(sc_nodes[-1], 0)
        time.sleep(60)

        for index in range(0, len(sc_nodes)):
            response = get_connected_peers(sc_nodes[index])
            connected_peers = response['result']['peers']
            assert_equal(len(connected_peers), len(sc_nodes) - 1)


if __name__ == "__main__":
    NetRingOfNodes().main()
