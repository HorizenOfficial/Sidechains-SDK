import logging
import pprint
from time import sleep
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from httpCalls.block.best import http_block_best
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.block.forging import http_start_forging, http_stop_forging
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.sendCoinsToAddress import sendCointsToMultipleAddress, sendCoinsToAddress
from httpCalls.wallet.balance import http_wallet_balance
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_equal
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    deserialize_perf_test_json, connect_sc_nodes, check_wallet_coins_balance, disconnect_sc_nodes, sc_connected_peers
from performance.perf_data import PerformanceData, NodeData, NetworkTopology


def get_node_configuration(mc_node, sc_node_data, perf_data):
    sc_nodes = list(sc_node_data)
    node_configuration = []

    topology = NetworkTopology(perf_data["network_topology"])
    logging.info(f"Network Topology: {topology.name}")
    max_connections = 0
    last_index = sc_nodes.index(sc_node_data[-1])

    for index, sc_node in enumerate(sc_nodes):
        if (index == 0 or index == last_index) and topology == NetworkTopology.DaisyChain:
            max_connections = 1
        elif (index != 0 and index != last_index) and topology == NetworkTopology.DaisyChain:
            max_connections = 2
        elif topology == NetworkTopology.Ring:
            max_connections = 2
        elif index == 0 and topology == NetworkTopology.Star:
            max_connections = len(sc_node_data) - 1
        elif index != 0 and topology == NetworkTopology.Star:
            max_connections = 1

        node_configuration.append(
            SCNodeConfiguration(
                mc_connection_info=MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                max_connections=max_connections,
                block_rate=perf_data["block_rate"],
                latency_settings=sc_node["latency_settings"]
            )
        )
    return node_configuration


class PerformanceTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    perf_test_data = deserialize_perf_test_json("./performance/perf_test.json")
    perf_data: dict[str, PerformanceData] = perf_test_data
    sc_node_data: dict[str, NodeData] = perf_test_data["nodes"]
    sc_nodes_list = list(sc_node_data)
    initial_txs = perf_data["initial_txs"]
    block_rate = perf_data["block_rate"]
    connection_map = {}

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

    def create_node_connection_map(self, key, values):
        for value in values:
            if key in self.connection_map:
                self.connection_map[key].append(value)
            else:
                self.connection_map[key] = [value]

    def set_topology(self):
        topology = NetworkTopology(self.perf_test_data["network_topology"])
        node_count = len(self.sc_nodes)
        node_final_position = node_count - 1
        node = 0

        # Daisy Chain Topology
        if topology == NetworkTopology.DaisyChain or topology == NetworkTopology.Ring:
            while node < node_final_position:
                logging.info(f"NODE CONNECTING: node[{node}] to node[{node+1}] - Final Node is [{node_final_position}]")
                connect_sc_nodes(self.sc_nodes[node], node+1)
                if node == 0:
                    self.create_node_connection_map(node, [node+1])
                else:
                    self.create_node_connection_map(node, [node-1, node+1])
                node += 1
            self.create_node_connection_map(node_final_position, [node-1])
        # Ring Topology
        if topology == NetworkTopology.Ring:
            # Connect final node to first node
            connect_sc_nodes(self.sc_nodes[node], 1)
            self.create_node_connection_map(0, [node])
            self.create_node_connection_map(node, [0])
        # Star Topology
        if topology == NetworkTopology.Star:
            if node_count < 3:
                raise Exception("Star Topology requires 3 or more nodes")
            while node < node_final_position:
                if node == 0:
                    node += 1
                connect_sc_nodes(self.sc_nodes[node], 0)
                self.create_node_connection_map(0, [node])
                self.create_node_connection_map(node, [0])
                node += 1
        print(f"NETWORK TOPOLOGY CONNECTION MAP: {self.connection_map}")
        self.sc_sync_all()

    def find_txs_creators(self):
        txs_creators = []
        for index, node in enumerate(self.sc_nodes_list):
            if node["tx_creator"]:
                txs_creators.append(self.sc_nodes[index])
        return txs_creators

    def disconnect_txs_creator_nodes(self):
        for index, node in enumerate(self.sc_nodes_list):
            if node["tx_creator"]:
                print(f"Node{index} connected peers BEFORE disconnect: {sc_connected_peers(self.sc_nodes[index])}")
                for connected_node in self.connection_map[index]:
                    print(f"DISCONNECTING node{index} from node{connected_node} and node{connected_node} from node{index}")
                    disconnect_sc_nodes(self.sc_nodes[index], connected_node)
                    disconnect_sc_nodes(self.sc_nodes[connected_node], index)
                print(f"Node{index} connected peers AFTER disconnect: {sc_connected_peers(self.sc_nodes[index])}")

    def reconnect_txs_creator_nodes(self):
        for index, node in enumerate(self.sc_nodes_list):
            if node["tx_creator"]:
                print(f"Node{index} connected peers BEFORE reconnect: {sc_connected_peers(self.sc_nodes[index])}")
                for connected_node in self.connection_map[index]:
                    print(f"RECONNECTING node{index} to node{connected_node}")
                    connect_sc_nodes(self.sc_nodes[index], connected_node)
                print(f"Node{index} connected peers AFTER reconnect: {sc_connected_peers(self.sc_nodes[index])}")

    def send_coins_to_creator_node_addresses(self, utxo_amount):
        tx_creators = self.find_txs_creators()

        for creator_node in tx_creators:
            destination_address = creator_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
            for i in range(self.initial_txs):
                # Populate the mempool - so don't mine a block
                sendCoinsToAddress(creator_node, destination_address, utxo_amount, 0)
            assert_equal(len(allTransactions(creator_node, False)["transactionIds"]), self.initial_txs)

    def get_best_node_block_ids(self):
        block_ids = {}
        for node in self.sc_nodes:
            block = http_block_best(node)
            block_ids[self.sc_nodes.index(node)] = block["id"]
        print(block_ids)
        return block_ids

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        self.set_topology()

        # Declare SC Address
        txs_creator_addresses = []
        ft_amount = 1000
        mc_return_address = mc_nodes[0].getnewaddress()

        # Get tx creator node
        txs_creators = self.find_txs_creators()

        # create 1 FTs in the same MC block to SC for every transaction creator node
        for i in range(len(txs_creators)):
            txs_creator_addresses.append(txs_creators[i].wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"])
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                          txs_creator_addresses[i], ft_amount, mc_return_address)
        self.sc_sync_all()

        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        for node in txs_creators:
            # Multiply ft_amount by 1e8 to get zentoshi value
            assert_equal(http_wallet_balance(node), ft_amount*1e8)

        # Create many UTXO in a single transaction to multiple addresses
        # Taking FT box and splitting to many UTXO to enable the creation of multiple transactions for the next part
        # of the test (population of the mempool)
        utxo_amount = 1000 / self.initial_txs
        for i in range(len(txs_creator_addresses)):
            sc_addresses = [txs_creator_addresses[i] for _ in range(self.initial_txs)]

            amounts = [utxo_amount for _ in range(self.initial_txs)]
            assert_equal(len(sc_addresses), len(amounts))
            # TODO if initial_txs > 1000 split and perform sendCointsToMultipleAddress in max 1000 parts
            # Verify that we dont use the previously created UTXO to create the new transaction
            sendCointsToMultipleAddress(txs_creators[i], sc_addresses, amounts, 0)
            self.sc_sync_all()

        generate_next_blocks(sc_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        # disconnect node to fill mempool without effecting other nodes
        self.disconnect_txs_creator_nodes()
        print(f"Node0 connected peers AFTER disconnect: {sc_connected_peers(self.sc_nodes[0])}")
        # send coins to creator nodes
        self.send_coins_to_creator_node_addresses(utxo_amount)
        # Reconnect the node to the network - connect_sc_nodes
        self.reconnect_txs_creator_nodes()

        # Take best block id of every node
        test_start_block_ids = self.get_best_node_block_ids()
        assert_equal(len(set(test_start_block_ids.values())), 1)

        # Start forging on nodes where forger == true
        # while mempool not empty - poll the mempool endpoint
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Forger found - Node{index}")
                http_start_forging(self.sc_nodes[index])
        # Sleep for initial block rate time, before polling transactions more frequently
        sleep(self.block_rate)
        # Wait until mempool empty - this should also mean that other nodes mempools are empty (differences will be performance issues)
        while len(allTransactions(txs_creators[0], False)["transactionIds"]) != 0:
            sleep(3)

        pprint.pprint(len(allTransactions(txs_creators[0], False)["transactionIds"]))
        sleep(200)
        pprint.pprint(len(allTransactions(txs_creators[0], False)["transactionIds"]))
        sleep(30)
        pprint.pprint(len(allTransactions(txs_creators[0], False)["transactionIds"]))

        # stop forging
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Stopping Forger Node{index}")
                http_stop_forging(self.sc_nodes[index])

        # call allTransactions for every node and print the content
        for node in self.sc_nodes:
            mempool_transactions = allTransactions(node, False)["transactionIds"]
            number_of_transactions = len(mempool_transactions)
            print(f"Node{node.index} mempool transactions remaining: {number_of_transactions}")
            if mempool_transactions > 0:
                print(f"Node{node.index} mempool transactions: {mempool_transactions}")


        # Take blockhash of every node and verify they are all the same
        test_end_block_ids = self.get_best_node_block_ids()
        assert_equal(len(set(test_end_block_ids.values())), 1)

        # Take the balance of each node
        for node in self.sc_nodes:
            wallet_balance = http_wallet_balance(node)
            print(f"Node{node.index} Wallet Balance: {wallet_balance}")

if __name__ == "__main__":
    PerformanceTest().main()
