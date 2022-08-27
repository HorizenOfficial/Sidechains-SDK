import logging
import pprint
from time import sleep
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from httpCalls.block.best import http_block_best
from httpCalls.block.forging import http_start_forging, http_stop_forging
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.sendCoinsToAddress import sendCointsToMultipleAddress, sendCoinsToAddress
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.wallet.allBoxesOfType import http_wallet_allBoxesOfType
from httpCalls.transaction.createCoreTransaction import http_create_core_transaction
from httpCalls.transaction.sendTransaction import http_send_transaction
from httpCalls.transaction.broadcastMempool import http_broadcast_mempool
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_equal
from SidechainTestFramework.scutil import assert_true, bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
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
    perf_data = perf_test_data
    sc_node_data = perf_test_data["nodes"]
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

        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

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

        for j in range (len(tx_creators)):
            destination_address = tx_creators[j].wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
            for i in range(self.initial_txs):
                # Populate the mempool - so don't mine a block
                sendCoinsToAddress(tx_creators[j], destination_address, utxo_amount, 0)
                if (i%1000 == 0):
                    print("Node "+str(j)+" sent txs: "+str(i))
            assert_equal(len(allTransactions(tx_creators[j], False)["transactionIds"]), self.initial_txs)

    def get_best_node_block_ids(self):
        block_ids = {}
        for node in self.sc_nodes:
            block = http_block_best(node)
            block_ids[self.sc_nodes.index(node)] = block["id"]
        pprint.pprint("Starting block ids: \n" + str(block_ids))
        return block_ids
    
    def find_boxes_of_address(self, boxes, address):
        address_boxes = []
        for box in boxes:
            if (box["proposition"]["publicKey"] == address):
                address_boxes.append(box)
        return address_boxes
            
    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        self.set_topology()

        # Declare SC Addresses
        txs_creator_addresses = []
        ft_addresses = []
        ft_amount = 1000
        mc_return_address = mc_nodes[0].getnewaddress()

        # Get tx creator nodes
        txs_creators = self.find_txs_creators()

        # create 1 FT to every transaction creator node
        for i in range(len(txs_creators)):
            ft_addresses.append(txs_creators[i].wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"])
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                          ft_addresses[i], ft_amount, mc_return_address)
            txs_creator_addresses.append(txs_creators[i].wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"])     
        self.sc_sync_all()

        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        #Verify that every tx_creator node received the FT
        for node in txs_creators:
            # Multiply ft_amount by 1e8 to get zentoshi value
            assert_equal(http_wallet_balance(node), ft_amount*1e8)

        ##########################################################
        ##################### TEST VERSION 1 #####################
        ##########################################################

        # Create many UTXOs in a single transaction to multiple addresses
        # Taking FT box and splitting into many UTXOs to enable the creation of multiple transactions for the next part
        # of the test (population of the mempool)
        utxo_amount = 1000 * 1e8 / self.initial_txs
        for i in range(len(txs_creator_addresses)):

            #Get FT box id (we should have only 1 ZenBox right now)
            zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
            ft_box = self.find_boxes_of_address(zen_boxes, ft_addresses[i])
            assert_true(len(ft_box), 0)

            print("Creating inital UTXOs...")
            created_utxos = 0
            for _ in range(int(self.initial_txs / 998)):

                # Create SidechainCoreTransaction that spend the split the FT box into many boxes. 
                # We have 1k boxes limit per transaction = 1 input + 998 outputs + change output
                outputs = [{"publicKey": txs_creator_addresses[i], "value": utxo_amount} for _ in range (998)]
                outputs.append({"publicKey": ft_addresses[i], "value": ft_box[0]["value"] - utxo_amount * 998})
                raw_tx = http_create_core_transaction(txs_creators[i], [{"boxId": ft_box[0]["id"]}], outputs)
                res = http_send_transaction(txs_creators[i], raw_tx)
                assert_true("transactionId" in res)
                print("Sent "+res["transactionId"]+" with UTXO: 998")

                self.sc_sync_all()
                generate_next_blocks(sc_nodes[0], "first node", 1)[0]
                self.sc_sync_all()

                created_utxos += 998

                zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
                ft_box = self.find_boxes_of_address(zen_boxes, ft_addresses[i])
                assert_true(len(ft_box), 0)

            remaining_utxos = self.initial_txs - created_utxos
            if (remaining_utxos > 0):
                raw_tx = http_create_core_transaction(txs_creators[i], [{"boxId": ft_box[0]["id"]}], [{"publicKey": txs_creator_addresses[i], "value": utxo_amount} for _ in range (remaining_utxos)])
                res = http_send_transaction(txs_creators[i], raw_tx)
                assert_true("transactionId" in res)
                print("Sent "+res["transactionId"]+ "with UTXO: "+str(remaining_utxos))

                self.sc_sync_all()
                generate_next_blocks(sc_nodes[0], "first node", 1)[0]
                self.sc_sync_all()
      
      
        # Check that every tx_creator node received the correct amount of UTXOs
        for i in range(len(txs_creators)):
            zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
            filtered_boxes = self.find_boxes_of_address(zen_boxes, txs_creator_addresses[i])
            assert_equal(len(filtered_boxes), self.initial_txs)

        # disconnect node to fill mempool without effecting other nodes
        self.disconnect_txs_creator_nodes()

        # send coins to creator nodes
        print("Populating the mempool...")
        self.send_coins_to_creator_node_addresses(utxo_amount)
        print("Mempool ready!")

        # verify that the creator nodes has the correct amount of transactions in the mempool
        for i in range(len(txs_creators)):
            mempool = allTransactions(txs_creators[i], True)["transactions"]
            assert_equal(len(mempool), self.initial_txs)

        # Reconnect the node to the network - connect_sc_nodes
        self.reconnect_txs_creator_nodes()

        # Take best block id of every node
        test_start_block_ids = self.get_best_node_block_ids()
        assert_equal(len(set(test_start_block_ids.values())), 1)

        # Broadcast node creator mempool to the network
        for node in txs_creators:
            http_broadcast_mempool(node)

        #TODO: this is not working: "Forging is not possible, because of whole consensus epoch is missed: current epoch = 22, parent epoch = 2"
        # Start forging on nodes where forger == true
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Forger found - Node{index} - Start Forging...")
                http_start_forging(self.sc_nodes[index])

        # # Sleep for initial block rate time, before polling transactions more frequently
        # sleep(self.block_rate)
        # # Wait until mempool empty - this should also mean that other nodes mempools are empty (differences will be performance issues)
        # while len(allTransactions(txs_creators[0], False)["transactionIds"]) != 0:
        #     sleep(3)

        ######## RUN UNTIL NODE CREATORS MEMPOOLS ARE EMPTY ########
        end_test = False
        while(not end_test):
            end_test = True
            for creator_node in txs_creators:
                if (len(allTransactions(creator_node, True)["transactions"]) != 0):
                    end_test = False
            sleep(5)

        # stop forging
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Stopping Forger Node{index}")
                http_stop_forging(self.sc_nodes[index])

        sleep(3)
        
        # Get information from all nodes
        for i in range (len(self.sc_nodes)):

            # Retrieve node mempool
            mempool_transactions = allTransactions(self.sc_nodes[i], False)
            number_of_transactions = len(mempool_transactions)
            print(f"Node {i} mempool transactions remaining: {number_of_transactions}")
            #if number_of_transactions > 0:
                #print(f"Node {i} mempool transactions: {mempool_transactions}")

            # Retrieve node balance
            wallet_balance = http_wallet_balance(self.sc_nodes[i])
            print(f"Node {i} Wallet Balance: {wallet_balance}")


        # Take blockhash of every node and verify they are all the same
        test_end_block_ids = self.get_best_node_block_ids()
        assert_equal(len(set(test_end_block_ids.values())), 1)
        
if __name__ == "__main__":
    PerformanceTest().main()
