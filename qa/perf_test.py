from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from httpCalls.block.forging import http_start_forging
from httpCalls.transaction.sendCoinsToAddress import sendCointsToMultipleAddress
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    deserialize_perf_test_json, connect_sc_nodes
from performance.perf_data import PerformanceData, NodeData


def get_node_configuration(mc_node, sc_node_data, perf_data):
    sc_nodes = list(sc_node_data)
    node_configuration = []

    topology = perf_data["network_topology"]
    print(str(topology))
    max_connections = 0
    last_index = sc_nodes.index(sc_node_data[-1])

    for index, sc_node in enumerate(sc_nodes):
        if (index == 0 or index == last_index) and topology == 1:
            max_connections = 1
        elif (index != 0 and index != last_index) and topology == 1:
            max_connections = 2
        elif topology == 2:
            max_connections = 2
        elif index == 0 and topology == 3:
            max_connections = len(sc_node_data) - 1
        elif index != 0 and topology == 3:
            max_connections = 1

        node_configuration.append(
            SCNodeConfiguration(
                mc_connection_info=MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                max_connections=max_connections,
                block_rate=perf_data["block_rate"],
                latency=sc_node["latency_settings"]
            )
        )
    return node_configuration


class PerformanceTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    perf_test_data = deserialize_perf_test_json("./performance/perf_test.json")
    perf_data: dict[str, PerformanceData] = perf_test_data
    sc_node_data: dict[str, NodeData] = perf_test_data["nodes"]

    def setup_nodes(self):
        # Start 1 MC node
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_nodes = get_node_configuration(mc_node, self.sc_node_data, self.perf_data)

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, 100),
            *sc_nodes
        )

        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(len(self.sc_node_data), self.options.tmpdir)

    def set_topology(self):
        topology = self.perf_test_data["network_topology"]
        node_count = len(self.sc_nodes)
        node_final_position = node_count - 1
        node = 0
        # Daisy Chain Topology
        if topology == 1 or topology == 2:
            while node <= node_final_position:
                connect_sc_nodes(self.sc_nodes[node], node + 1)
                node += 1
        # Ring Topology
        if topology == 2:
            # Connect final node to first node
            connect_sc_nodes(self.sc_nodes[node], 1)
        # Star Topology
        if topology == 3:
            if node_count < 4:
                raise Exception("Star Topology requires 4 or more nodes")
            while node <= node_final_position:
                if node == 0:
                    node += 1
                connect_sc_nodes(self.sc_nodes[node], 1)
                node += 1
        self.sc_sync_all()

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        self.set_topology()

        # Start forging on nodes where forger == true
        for index, node in list(self.sc_node_data):
            if node["forger"]:
                http_start_forging(sc_nodes[index])

        # Declare SC Address
        sc_address = ""

        # Get tx creator node
        for index, node in list(self.sc_node_data):
            if node["tx_creator"]:
                # create 1 FTs in the same MC block to SC
                sc_address = sc_nodes[index].wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
                ft_amount = 1000
                mc_return_address = mc_nodes[0].getnewaddress()

                forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                              sc_address, ft_amount, mc_return_address)
                self.sc_sync_all()

        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_nodes[0], "first node", 1)[0]
        self.sc_sync_all()
        # Get initial transactions from perf test json (TypeDict)
        initial_txs = int(self.sc_node_data["initial_txs"])

        sc_addresses = [sc_address] * initial_txs
        amounts = [1000 / initial_txs for _ in range(initial_txs)]
        sendCointsToMultipleAddress(sc_nodes[0], sc_addresses, amounts, 0)
        self.sc_sync_all()

        generate_next_blocks(sc_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        #TODO Send transaction to another node


if __name__ == "__main__":
    PerformanceTest().main()
