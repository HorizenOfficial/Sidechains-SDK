#!/usr/bin/env python2
import time

from SidechainTestFramework.sc_boostrap_info \
    import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework \
    import SidechainTestFramework
from SidechainTestFramework.scutil \
    import bootstrap_sidechain_nodes, start_sc_nodes, get_known_peers, sc_p2p_port
from test_framework.util \
    import initialize_chain_clean, start_nodes, websocket_port_by_mc_node_index, assert_true, assert_false

"""
Check the connection between sidechain nodes to be unidirectional.

Configuration: start 3 sidechain nodes and 1 Mainchain node, from which the sidechain is bootstrapped. 
    Connections:
    sc_node1 -> sc_node2   
    sc_node2 -> sc_node3
    
Test:
    - verify that:
        after a while node_1 and node_3 are connected
"""


def separator():
    print "\n*****************************************************\n"


def print_peers_addresses(node):
    for peer in node.node_connectedPeers()["result"]["peers"]:
        print peer['address'] + " - " + peer['connectionType']


def get_node_peers_as_port_colon_direction(node):
    node_peers = []
    for peer in node.node_connectedPeers()["result"]["peers"]:
        conny = peer['address'].split(":")[-1] + ":" + peer['connectionType']
        node_peers.append(conny.strip())
    return node_peers


def are_they_well_connected(node_1, node_2):
    peers_1 = get_node_peers_as_port_colon_direction(node_1)
    peers_2 = get_node_peers_as_port_colon_direction(node_2)

    match_1 = node_2.peer_port
    match_2 = node_1.peer_port

    matching_1 = [s for s in peers_1 if (match_1 in s)]
    if len(matching_1) > 1:
        print " more than one connection between peers " + node_1.peer_port + " and " + node_2.peer_port
        return False

    matching_2 = [s for s in peers_2 if (match_2 in s)]
    if len(matching_2) > 1:
        print " more than one connection between peers " + node_2.peer_port + " and " + node_1.peer_port
        return False
    if len(matching_1) > 0 and len(matching_2) > 0:
        direction_1 = matching_1[0].split(":")[1]
        direction_2 = matching_2[0].split(":")[1]
    else:
        return False

    if direction_1 == "Incoming":
        if direction_2 == "Outgoing":
            return True
        return False
    else:
        if direction_2 == "Incoming":
            return True
        return False
    return False


def print_peers_connections(sc_nodes):
    print "\n"
    for i in range(len(sc_nodes)):
        print "\n****** " + str(i) + " ******"
        print "node " + "127.0.0.1:" + str(sc_p2p_port(i)) + " peers:"
        print_peers_addresses(sc_nodes[i])
    return ""


def create_three_nodes_network(mc_node_1):
    sc_node_1_configuration = SCNodeConfiguration(
        MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
        known_peers=get_known_peers([1])

    )
    sc_node_2_configuration = SCNodeConfiguration(
        MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
        known_peers=get_known_peers([0, 2])

    )
    sc_node_3_configuration = SCNodeConfiguration(
        MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
        known_peers=get_known_peers([])

    )
    network = SCNetworkConfiguration(
        SCCreationInfo(mc_node_1, 600, 1000),
        sc_node_1_configuration,
        sc_node_2_configuration,
        sc_node_3_configuration)
    return network


# **********************************************************************************************
# *************************************** CLASS ************************************************
# **********************************************************************************************

class SCNodeSelfCompleteConnections(SidechainTestFramework):
    # *************************************** ATTRIBUTES ************************************************
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 3
    sc_nodes_bootstrap_info = None

    # *************************************** SETUP ************************************************

    # *** MC ***
    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    # *** SC ***
    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        # *********** simple way network ***********
        network = create_three_nodes_network(mc_node_1)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    # *************************************** RUN TEST ************************************************

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        print "Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(
            self.number_of_mc_nodes))
        print "Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(
            self.number_of_sidechain_nodes))

        node_1 = sc_nodes[0]
        node_2 = sc_nodes[1]
        node_3 = sc_nodes[2]

        time.sleep(10)

        assert_true(are_they_well_connected(node_1, node_2),
                    "peers " + node_1.peer_port + " and " + node_2.peer_port + " are not connected!!")

        assert_true(are_they_well_connected(node_2, node_3),
                    "peers " + node_2.peer_port + " and " + node_3.peer_port + " are not connected!!")

        time.sleep(20)
        # this proves they are not going to connect by default
        assert_false(are_they_well_connected(node_1, node_3),
                    "peers " + node_1.peer_port + " and " + node_3.peer_port + " are not connected!!")


if __name__ == "__main__":
    SCNodeSelfCompleteConnections().main()
