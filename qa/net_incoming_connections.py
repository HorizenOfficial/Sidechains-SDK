#!/usr/bin/env python3

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes
from httpCalls.peer.getConnectedPeers import get_connected_peers
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, assert_true

"""
Make sure that a node doesn't accept more incoming connections than the limit set in the config file.

Configuration: start 8 sidechain nodes. First node has 5 max incoming connections and 0 outgoing.
All the other nodes have 0 incoming and 1 outgoing.

Test:
    - try to connect all the nodes, starting from the second one, to the first node
    - the first node will accept the first 5 connections and decline the others
"""


class NetIncomingConnections(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 8

    def __init__(self, max_incoming_connections=5):
        self.max_incoming_connections = max_incoming_connections
        self.sc_nodes_bootstrap_info = None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]

        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                max_incoming_connections=self.max_incoming_connections,
                max_outgoing_connections=0
            )
        ]
        for _ in range(1, self.number_of_sidechain_nodes):
            sc_node_configuration.append(
                SCNodeConfiguration(
                    MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                    ),
                    max_incoming_connections=0,
                    max_outgoing_connections=1
                )
            )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000), *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        sc_nodes = self.sc_nodes

        for index in range(1, len(sc_nodes)):
            try:
                connect_sc_nodes(sc_nodes[index], 0)
            except:
                pass

        self.sc_sync_all()
        response = get_connected_peers(sc_nodes[0])
        connected_peers = response['result']['peers']
        assert_true(len(connected_peers) == self.max_incoming_connections)

        for index in range(1, len(sc_nodes)):
            response = get_connected_peers(sc_nodes[index])
            connected_peers = response['result']['peers']
            assert_true(len(connected_peers) <= 1)


if __name__ == "__main__":
    NetIncomingConnections().main()
