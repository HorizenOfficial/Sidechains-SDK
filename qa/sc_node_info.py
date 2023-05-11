#!/usr/bin/env python3

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes, sc_p2p_port
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, assert_true

"""
Make sure that node info endpoint gives correct data.

Configuration: start 5 sidechain nodes. All nodes have max outgoing connections set to 0, except the first one.

Test:
    - connect the first node to all the other nodes
    - get the first node info
    - check that number of all peers is 4
"""


class ScNodeInfo(SidechainTestFramework):
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

        node_info = sc_nodes[0].node_info()['result']

        assert_true(node_info['numberOfPeers'], 4)


if __name__ == "__main__":
    ScNodeInfo().main()
