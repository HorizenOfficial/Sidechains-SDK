#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes, stop_sc_node, start_sc_node, \
    wait_for_sc_node_initialization, sc_p2p_port
from httpCalls.peer.getAllPeers import get_all_peers
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, assert_true

"""
Make sure that a node can save and restore peers data.

Configuration: start 5 sidechain nodes. All nodes have max outgoing connections set to 0, except the first one.

Test:
    - connect the first node to all the other nodes
    - get all connected peers
    - stop the first node
    - restart it
    - get again all connected peers and check that this list and the previous one contain the same addresses
"""


class NetPeersStoragePersistence(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 5

    def __init__(self):
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
                storage_backup_delay="5s",
                storage_backup_interval="1s",
                declared_address=f'127.0.0.1:{sc_p2p_port(0)}'
            )
        ]

        for _ in range(1, self.number_of_sidechain_nodes):
            sc_node_configuration.append(
                SCNodeConfiguration(
                    MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                    ),
                    storage_backup_delay="5s",
                    storage_backup_interval="1s",
                    max_outgoing_connections=0
                )
            )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000), *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        sc_nodes = self.sc_nodes

        connect_sc_nodes(sc_nodes[0], 1)
        connect_sc_nodes(sc_nodes[0], 2)
        connect_sc_nodes(sc_nodes[0], 3)
        connect_sc_nodes(sc_nodes[0], 4)

        all_peers_before = get_all_peers(sc_nodes[0])['result']['peers']

        stop_sc_node(sc_nodes[0], 0)

        time.sleep(5)
        start_sc_node(0, self.options.tmpdir)
        wait_for_sc_node_initialization(self.sc_nodes)

        all_peers_after = get_all_peers(sc_nodes[0])['result']['peers']
        assert_true(len(all_peers_before) == len(all_peers_after))

        for peer in all_peers_before:
            assert_true(peer in all_peers_after)


if __name__ == "__main__":
    NetPeersStoragePersistence().main()
