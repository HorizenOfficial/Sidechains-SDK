#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes, sc_p2p_port
from httpCalls.peer.getConnectedPeers import get_connected_peers
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, assert_true

"""
Make sure that a node can connect first with known peers.

Configuration: start 10 sidechain nodes. First node has 5 max outgoing connections and 3 known peers.

Test:
    - starting from the second node, each node is connected to the following one
    - wait for 10s so that the nodes can share peers
    - check that the first node has at least 3 connections and all the known peers are included 
"""


class NetFirstKnownPeers(SidechainTestFramework):
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

        sc_node_configuration = [
            # Node A
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                known_peers=["127.0.0.1:" + str(sc_p2p_port(1)), "127.0.0.1:" + str(sc_p2p_port(2)),
                             "127.0.0.1:" + str(sc_p2p_port(3))],
                max_outgoing_connections=5,
                declared_address=f'127.0.0.1:{sc_p2p_port(0)}'
            )
        ]
        for index in range(1, self.number_of_sidechain_nodes):
            # Node B
            sc_node_configuration.append(
                SCNodeConfiguration(
                    MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                    ),
                    declared_address=f'127.0.0.1:{sc_p2p_port(index)}'
                )
            )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000), *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        sc_nodes = self.sc_nodes
        for index in range(1, len(sc_nodes) - 1):
            connect_sc_nodes(sc_nodes[index], index + 1)

        time.sleep(10)

        response = get_connected_peers(sc_nodes[0])
        connected_peers = response['result']['peers']
        assert_true(len(connected_peers) >= 3)

        known_peers = ["/127.0.0.1:" + str(sc_p2p_port(1)), "/127.0.0.1:" + str(sc_p2p_port(2)),
                       "/127.0.0.1:" + str(sc_p2p_port(3))]
        remote_addresses = [peer['remoteAddress'] for peer in connected_peers]
        for address in known_peers:
            assert_true(address in remote_addresses)


if __name__ == "__main__":
    NetFirstKnownPeers().main()
