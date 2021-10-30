#!/usr/bin/env python2
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SCMultiNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, get_known_peers, sc_p2p_port, \
    stop_sc_node, \
    start_sc_node, sc_create_multiple_nodes_network
from test_framework.util import initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, assert_true

"""
Check the connection between sidechain nodes to be unidirectional.

Configuration: start 3 sidechain nodes and 1 Mainchain node, from which the sidechain is bootstrapped. 
    Connections:
        every node has a connection with every other node
Test:
    - verify that:
        there are no redundant connections. 
"""



def print_connected_peers_addresses(node):
    for peer in node.node_connectedPeers()["result"]["peers"]:
        print peer['address'] + " - " + peer['connectionType']


def print_peers_connections(sc_nodes):
    print "\n"
    for i in range(len(sc_nodes)):
        print "\n****** " + str(i) + " ******"
        print "node " + "127.0.0.1:" + str(sc_p2p_port(i)) + " peers:"
        print_connected_peers_addresses(sc_nodes[i])
    return ""


def get_node_ports(sc_nodes):
    ports = []
    for i in range(len(sc_nodes)):
        ports.append(str(sc_p2p_port(i)))
    print ports





def create_three_nodes_network(mc_node_1):
    sc_node_1_configuration = SCNodeConfiguration(
        MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
        known_peers=get_known_peers([1, 2])

    )
    sc_node_2_configuration = SCNodeConfiguration(
        MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
        known_peers=get_known_peers([0, 2])

    )
    sc_node_3_configuration = SCNodeConfiguration(
        MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
        known_peers=get_known_peers([0, 1])

    )
    network = SCNetworkConfiguration(
        SCCreationInfo(mc_node_1, 600, 1000),
        sc_node_1_configuration,
        sc_node_2_configuration,
        sc_node_3_configuration)
    return network


def is_unique_connection(conny, connections):
    if connections.count(conny) > 1:
        print (conny + "is more than one")
        return False
    return True


def change_direction(direction):
    if direction == "Incoming":
        return "Outgoing"
    return "Incoming"


def check_is_paid_back_and_unique(reversed_conny, connections):
    if connections.count(reversed_conny) > 1:
        print (reversed_conny + "is more than one")
        return False
    if connections.count(reversed_conny) < 1:
        print (reversed_conny + " does not exist and it should be against " + reversed(reversed_conny))
        return False
    return True


def reversed(conny):
    params = conny.split(":")
    node_1 = params[0]
    node_2 = params[1]
    direction = params[2]
    return params[1] + ":" + params[0] + ":" + change_direction(direction)


def on_itself(conny):
    if conny.split(":")[0] == conny.split(":")[1]:
        print "node " + conny.split(":")[0] + " has a connection with itself"
        return True
    return False  # False is ok!!

def do_a_selfie(conny):
    return conny.split(":")[0] + ":" + conny.split(":")[0] + ":" + conny.split(":")[2]


def check_all_connections_are_ok(sc_nodes, put_error=False):
    if len(sc_nodes) < 3:
        print "please, at least three nodes..."
        return False

    # get connections List
    connections = []
    for i in range(len(sc_nodes)):
        for peer in sc_nodes[i].node_connectedPeers()["result"]["peers"]:
            connections.append(str(sc_p2p_port(i)) + ":" + peer['address'].split(":")[1] + ":" + peer['connectionType'])
    # put an error
    if put_error:
        connections.append(do_a_selfie(connections[0]))
        # connections.append(connections[0])
        #connections.append(reversed(connections[0]))
        # check connections
    for conny in connections:
        # print conny
        if on_itself(conny):
            return False
        if not is_unique_connection(conny, connections):
            return False
        if not check_is_paid_back_and_unique(reversed(conny), connections):
            return False
    return True


# **********************************************************************************************
# *************************************** CLASS ************************************************
# **********************************************************************************************

class SCNodeUnidirectionalConnection(SidechainTestFramework):
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
        # network = create_three_nodes_network(mc_node_1)

        # *********** multiple nodes network ***********
        network = sc_create_multiple_nodes_network(mc_node_1, self.number_of_sidechain_nodes)

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

        #print_peers_connections(sc_nodes)
        print("do first check... ")
        assert_true(check_all_connections_are_ok(sc_nodes),
                    "just started... connections are not ok:\n" + print_peers_connections(sc_nodes))
        print("first check ok...")

        # KILLING a node and restarting it to check if they reconnect each other correctly
        # ******** KILL ********
        print "\n"
        print "tryin to kill node /127.0.0.1:" + str(sc_p2p_port(1))
        stop_sc_node(sc_nodes[1], 1)
        print "node /127.0.0.1:" + str(sc_p2p_port(1)) + " killed"
        print "\n"

        time.sleep(2)

        # ******** RESTART ********
        print "restarting node /127.0.0.1:" + str(sc_p2p_port(1))
        sc_nodes[1] = start_sc_node(1, self.options.tmpdir)
        print "node /127.0.0.1:" + str(sc_p2p_port(1)) + " restarted"
        print "\n"
        time.sleep(12)
        # Check again
        assert_true(check_all_connections_are_ok(sc_nodes),
                    "after killing node " + str(
                        sc_p2p_port(1)) + " connections are not ok:\n" + print_peers_connections(sc_nodes))

        print("second check ok...")

if __name__ == "__main__":
    SCNodeUnidirectionalConnection().main()
