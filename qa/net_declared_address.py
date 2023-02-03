#!/usr/bin/env python3
import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from httpCalls.peer.getConnectedPeers import get_connected_peers
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, assert_true, assert_false
from SidechainTestFramework.scutil import check_box_balance, connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes, is_mainchain_block_included_in_sc_block, generate_next_blocks, \
    check_mainchain_block_reference_info, check_wallet_coins_balance, sc_p2p_port
from httpCalls.peer.getAllPeers import get_all_peers

"""
Make sure that a node not exposing its address cannot be reached from external.

Configuration: connect the nodes this way:
    - 0 -> 1 -> 2 -> 3 <- 5
                |
                v
                4

Test:
    - connect all the nodes like above
    - wait for 60s to let the nodes share the peers among them
    - check that no node was able to establish an outgoing connection to node 0
"""


class NetDeclaredAddress(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 6

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
                get_peers_interval='5s'
            ),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(1)}',
                get_peers_interval='5s'
            ),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(2)}',
                get_peers_interval='5s'
            ),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(3)}',
                get_peers_interval='5s'
            ),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(4)}',
                get_peers_interval='5s'
            ),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))
                ),
                declared_address=f'127.0.0.1:{sc_p2p_port(5)}',
                get_peers_interval='5s'
            )
        ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000), *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        sc_nodes = self.sc_nodes

        connect_sc_nodes(sc_nodes[0], 1)
        connect_sc_nodes(sc_nodes[1], 2)
        connect_sc_nodes(sc_nodes[2], 3)
        connect_sc_nodes(sc_nodes[2], 4)
        connect_sc_nodes(sc_nodes[5], 3)

        time.sleep(60)

        response = get_connected_peers(sc_nodes[0])
        connected_peers = response['result']['peers']

        for peer in connected_peers:
            assert_true(peer['connectionType'] == 'Outgoing')


if __name__ == "__main__":
    NetDeclaredAddress().main()
