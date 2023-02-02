#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, sc_p2p_port
from httpCalls.peer.getConnectedPeers import get_connected_peers
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, assert_true

"""
Make sure that a temporary unreachable known peer doesn't paralyze the node from connecting to other peers.

Configuration: start 7 sidechain nodes.

Test:
    - wait for 5s for the nodes to establish connections
    - make sure the first node doesn't get stuck trying to connect to a down known peer
"""


class NetSkipDownKnownPeer(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 7

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
                known_peers=["127.0.0.1:" + str(sc_p2p_port(1)), "127.0.0.1:" + str(sc_p2p_port(2)), "127.0.0.1:6666"],
                declared_address=f'127.0.0.1:{sc_p2p_port(0)}'
            ),
            # Node B
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                known_peers=["127.0.0.1:" + str(sc_p2p_port(3)), "127.0.0.1:" + str(sc_p2p_port(4)),
                             "127.0.0.1:" + str(sc_p2p_port(5))],
                declared_address=f'127.0.0.1:{sc_p2p_port(1)}'
            ),
            # Node C
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                known_peers=["127.0.0.1:" + str(sc_p2p_port(4)), "127.0.0.1:" + str(sc_p2p_port(5)),
                             "127.0.0.1:" + str(sc_p2p_port(6))],
                declared_address=f'127.0.0.1:{sc_p2p_port(2)}'
            ),
            # Node D
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(3)}'
            ),
            # Node E
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(4)}'
            ),
            # Node F
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(5)}'
            ),
            # Node G
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(6)}'
            )
        ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000), *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        sc_nodes = self.sc_nodes
        time.sleep(5)

        response = get_connected_peers(sc_nodes[0])
        connected_peers = response['result']['peers']

        def condition(peer):
            return peer['remoteAddress'] == f'/127.0.0.1:{str(sc_p2p_port(1))}' \
                or peer['remoteAddress'] == f'/127.0.0.1:{str(sc_p2p_port(2))}'

        assert_true(len(connected_peers) >= 2)
        assert_true(len([el for el in connected_peers if condition(el)]) == 2)


if __name__ == "__main__":
    NetSkipDownKnownPeer().main()
