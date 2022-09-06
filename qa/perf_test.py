import logging
import math
import random
import time
from multiprocessing import Pool, Value
from time import sleep
from requests import RequestException
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LatencyConfig
from httpCalls.block.best import http_block_best
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.block.forging import http_start_forging, http_stop_forging
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.wallet.allBoxesOfType import http_wallet_allBoxesOfType
from httpCalls.transaction.createCoreTransaction import http_create_core_transaction
from httpCalls.transaction.sendTransaction import http_send_transaction
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_equal
from SidechainTestFramework.scutil import assert_true, bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    deserialize_perf_test_json, connect_sc_nodes, start_sc_nodes_with_multiprocessing
from performance.perf_data import NetworkTopology, TestType

# Declare global thread safe values used for multiprocessing tps test
counter = Value('i', 0)
errors = Value('i', 0)


# Initialise the threadsafe globals
def init_globals(count, err):
    global counter
    global errors
    counter = count
    errors = err


def get_latency_config(perf_data):
    return LatencyConfig(
        perf_data["latency_settings"]["get_peer_spec"],
        perf_data["latency_settings"]["peer_spec"],
        perf_data["latency_settings"]["transaction"],
        perf_data["latency_settings"]["block"],
        perf_data["latency_settings"]["request_modifier_spec"],
        perf_data["latency_settings"]["modifiers_spec"]
    )


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
                latency_settings=get_latency_config(perf_data)
            )
        )
    return node_configuration


def get_number_of_transactions_for_node(node):
    return len(allTransactions(node, False)["transactionIds"])


def send_transactions_per_second(txs_creator_node, destination_address, utxo_amount, tps_per_process,
                                 start_time, test_run_time):
    # Run until
    while time.time() - start_time < test_run_time:
        i = 0
        tps_start_time = time.time()
        # Send transactions until the maximum tps value has been reached for each process (thread).
        while i < tps_per_process:
            try:
                sendCoinsToAddress(txs_creator_node, destination_address, utxo_amount, 0)
            except Exception:
                with errors.get_lock():
                    errors.value += 1
            with counter.get_lock():
                counter.value += 1
            i += 1
        completion_time = time.time() - tps_start_time
        # Remove execution time from the 1 second to get as close to X number of TPS as possible
        if completion_time < 1:
            sleep(1 - completion_time)
        # If completion time exceeds 1 second, it's no longer x transactions per second.
        # Lower the max_tps_per_process config value until this value is under 1 second.
        elif completion_time > 1:
            print("WARNING:  Number of transactions sent has exceeded 1 second - decrease max_tps_per_process value.")


class PerformanceTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    perf_data = deserialize_perf_test_json("./performance/perf_test.json")
    test_type = TestType(perf_data["test_type"])
    sc_node_data = perf_data["nodes"]
    sc_nodes_list = list(sc_node_data)
    initial_txs = perf_data["initial_txs"]
    test_run_time = perf_data["test_run_time"]
    block_rate = perf_data["block_rate"]
    connection_map = {}

    def setup_nodes(self):
        # Start 1 MC node
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        print("Initializing test directory "+self.options.tmpdir)
        mc_node = self.nodes[0]
        sc_nodes = get_node_configuration(mc_node, self.sc_node_data, self.perf_data)

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, 100),
            *sc_nodes
        )

        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * self.block_rate / 2)

    def sc_setup_nodes(self):
        if(self.perf_data["use_multithreading"]):
            return start_sc_nodes_with_multiprocessing(len(self.sc_node_data), self.options.tmpdir)
        else:
            return start_sc_nodes(len(self.sc_node_data), self.options.tmpdir)

    def create_node_connection_map(self, key, values):
        for value in values:
            if key in self.connection_map:
                self.connection_map[key].append(value)
            else:
                self.connection_map[key] = [value]

    def set_topology(self):
        topology = NetworkTopology(self.perf_data["network_topology"])
        node_count = len(self.sc_nodes)
        node_final_position = node_count - 1
        node = 0

        # Daisy Chain Topology
        if topology == NetworkTopology.DaisyChain or topology == NetworkTopology.Ring:
            while node < node_final_position:
                logging.info(
                    f"NODE CONNECTING: node[{node}] to node[{node + 1}] - Final Node is [{node_final_position}]")
                connect_sc_nodes(self.sc_nodes[node], node + 1)
                if node == 0:
                    self.create_node_connection_map(node, [node + 1])
                else:
                    self.create_node_connection_map(node, [node - 1, node + 1])
                node += 1
            self.create_node_connection_map(node_final_position, [node - 1])
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

    def get_txs_creators_and_non_creators(self):
        txs_creators = []
        non_txs_creator = []
        for index, node in enumerate(self.sc_nodes_list):
            if node["tx_creator"]:
                txs_creators.append(self.sc_nodes[index])
            else:
                non_txs_creator.append(self.sc_nodes[index])
        return txs_creators, non_txs_creator

    def find_forger_nodes(self):
        forger_nodes = []
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                forger_nodes.append(self.sc_nodes[index])
        return forger_nodes

    def populate_mempool(self, utxo_amount, txs_creator_nodes, non_creator_nodes):

        for j in range(len(txs_creator_nodes)):
            destination_address = http_wallet_createPrivateKey25519(random.choice(non_creator_nodes))
            for i in range(self.initial_txs):
                # Populate the mempool - so don't mine a block
                sendCoinsToAddress(txs_creator_nodes[j], destination_address, utxo_amount, 0)
                if (i % 1000 == 0):
                    print("Node " + str(j) + " sent txs: " + str(i))
            assert_equal(len(allTransactions(txs_creator_nodes[j], False)["transactionIds"]), self.initial_txs)
            print("Node " + str(j) + " totally sent txs: " + str(self.initial_txs))

    def get_best_node_block_ids(self):
        block_ids = {}
        for node in self.sc_nodes:
            block = http_block_best(node)
            block_ids[self.sc_nodes.index(node)] = block["id"]
        print("Block ids: " + str(block_ids))
        return block_ids

    def find_boxes_of_address(self, boxes, address):
        address_boxes = []
        for box in boxes:
            if box["proposition"]["publicKey"] == address:
                address_boxes.append(box)
        return address_boxes

    def log_node_wallet_balances(self):
        # Output the balance of each node
        for index, node in enumerate(self.sc_nodes):
            wallet_boxes = http_wallet_allBoxesOfType(node, "ZenBox")
            wallet_balance = 0
            for box in wallet_boxes:
                wallet_balance += box["value"]
            if self.sc_nodes_list[index]["tx_creator"]:
                print(f"Node{index} (Transaction Creator) Wallet Balance: {wallet_balance}")
            else:
                print(f"Node{index} Wallet Balance: {wallet_balance}")

    def get_node_mined_transactions_by_block_id(self, node, block_id):
        result = http_block_findById(node, block_id)
        return result["block"]["sidechainTransactions"]

    def txs_creator_send_transactions_per_second_to_addresses(self, utxo_amount, txs_creator_nodes, non_creator_nodes,
                                                              tps_test):
        start_time = time.time()

        if tps_test:
            # Each process needs to be able to send a number of transactions per second, without going over 1 second.
            # May need some fine-tuning depending on what machine this is running on. Default to 100.
            try:
                max_tps_per_process = self.perf_data["max_tps_per_process"]
            except Exception:
                raise ValueError("Value: 'max_tps_per_process' not found, ensure it's present in json config")

            tps = math.floor(self.initial_txs / self.test_run_time)
            # Decide number of processes we need to use, as each process needs to be able to fire x transactions in
            # 1 second or less. e.g. 100 tps and if each process can comfortably handle 10 transactions in
            # under 1 second we need 10 processes running 10 tps in parallel to get 100 tps total.
            max_processes = math.ceil(tps / max_tps_per_process)
            tps_per_process = math.ceil(tps / max_processes)

            for i in range(len(txs_creator_nodes)):
                print(f"Running Throughput: {tps} Transactions Per Second for Creator Node(Node{i})...")
                
            while counter.value < self.initial_txs * len(txs_creator_nodes) and ((time.time() - start_time) < self.test_run_time):
                # Create the multiprocess pool
                with Pool(initializer=init_globals, initargs=(counter, errors)) as pool:
                    i = 0
                    args = []
                    # Add send_transactions_per_second arguments to args for each process required
                    # starmap runs them all in parallel
                    while i < max_processes:
                        for tx_creator_node in txs_creator_nodes:
                            # Create a single random node destination address per process
                            destination_address = http_wallet_createPrivateKey25519(random.choice(non_creator_nodes))
                            args.append((tx_creator_node, destination_address, utxo_amount,
                                        tps_per_process, start_time, self.test_run_time))
                        i += 1
                    pool.starmap(send_transactions_per_second, args)

                print(f"... Sent {counter.value} Transactions ...")
                print(f"Timer: {time.time() - start_time} and counter {counter.value}")
        # We're not interested in transactions per second, just fire all transactions as fast as possible
        else:
            for i in range(len(txs_creator_nodes)):
                print(f"Running Throughput: {tps} Transactions Per Second for Creator Node(Node{i})...")
            for _ in range(self.initial_txs):
                with Pool(initializer=init_globals, initargs=(counter, errors)) as pool:
                    for tx_creator_node in txs_creator_nodes:
                        # Create destination address for random node for each tx
                        destination_address = http_wallet_createPrivateKey25519(random.choice(non_creator_nodes))
                        args = [(tx_creator_node, destination_address, utxo_amount, 0)]
                        while counter.value < self.initial_txs and ((time.time() - start_time) < self.test_run_time):
                            try:
                                pool.starmap(sendCoinsToAddress, args)
                            except Exception:
                                errors.value += 1
                            counter.value += 1
            print(f"Firing Transactions Ended After: {time.time() - start_time}")
            print(f"Total Nodes creator sent {counter.value} transactions out of a possible {self.initial_txs} "
                  f"in {time.time() - start_time} seconds.")

        print(f"Total Nodes creator ERRORS ENCOUNTERED: {errors.value}")

    def run_test(self):
        mc_nodes = self.nodes
        self.set_topology()

        # Declare SC Addresses
        txs_creator_addresses = []
        ft_addresses = []
        ft_amount = 1000
        mc_return_address = mc_nodes[0].getnewaddress()

        # Get tx creator nodes and non tx creator nodes
        txs_creators, non_txs_creators = self.get_txs_creators_and_non_creators()

        # create 1 FT to every transaction creator node
        for i in range(len(txs_creators)):
            ft_addresses.append(http_wallet_createPrivateKey25519(txs_creators[i]))
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                          ft_addresses[i], ft_amount, mc_return_address)
            txs_creator_addresses.append(
                http_wallet_createPrivateKey25519(txs_creators[i]))
        self.sc_sync_all()

        # Generate 1 SC block to include FTs.
        forger_nodes = self.find_forger_nodes()
        generate_next_blocks(forger_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        # Verify that every tx_creator node received the FT
        for node in txs_creators:
            # Multiply ft_amount by 1e8 to get zentoshi value
            assert_equal(http_wallet_balance(node), ft_amount * 1e8)

        # Create many UTXOs in a single transaction to multiple addresses
        # Taking FT box and splitting into many UTXOs to enable the creation of multiple transactions for the next part
        # of the test (population of the mempool)
        utxo_amount = ft_amount * 1e8 / self.initial_txs
        for i in range(len(txs_creator_addresses)):

            # Get FT box id (we should have only 1 ZenBox right now)
            zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
            ft_box = self.find_boxes_of_address(zen_boxes, ft_addresses[i])
            assert_true(len(ft_box), 0)

            print("Creating initial UTXOs...")
            created_utxos = 0
            # We have 1k boxes limit per transaction = 1 input + 998 outputs + change output
            max_transaction_output = 998
            for _ in range(int(self.initial_txs / max_transaction_output)):
                # Create SidechainCoreTransaction that spend the split the FT box into many boxes.
                outputs = [{"publicKey": txs_creator_addresses[i], "value": utxo_amount} for _ in
                           range(max_transaction_output)]
                outputs.append(
                    {"publicKey": ft_addresses[i], "value": ft_box[0]["value"] - utxo_amount * max_transaction_output})
                raw_tx = http_create_core_transaction(txs_creators[i], [{"boxId": ft_box[0]["id"]}], outputs)
                res = http_send_transaction(txs_creators[i], raw_tx)
                assert_true("transactionId" in res)
                print("Sent " + res["transactionId"] + " with UTXO: " + str(max_transaction_output))

                self.sc_sync_all()
                generate_next_blocks(forger_nodes[0], "first node", 1)[0]
                self.sc_sync_all()

                created_utxos += max_transaction_output

                zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
                ft_box = self.find_boxes_of_address(zen_boxes, ft_addresses[i])
                assert_true(len(ft_box), 0)

            remaining_utxos = self.initial_txs - created_utxos
            if remaining_utxos > 0:
                raw_tx = http_create_core_transaction(txs_creators[i], [{"boxId": ft_box[0]["id"]}],
                                                      [{"publicKey": txs_creator_addresses[i], "value": utxo_amount} for
                                                       _ in range(remaining_utxos)])
                res = http_send_transaction(txs_creators[i], raw_tx)
                assert_true("transactionId" in res)
                print("Sent " + res["transactionId"] + "with UTXO: " + str(remaining_utxos))

                self.sc_sync_all()
                generate_next_blocks(forger_nodes[0], "first node", 1)[0]
                self.sc_sync_all()

        # Check that every tx_creator node received the correct amount of UTXOs
        for i in range(len(txs_creators)):
            zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
            filtered_boxes = self.find_boxes_of_address(zen_boxes, txs_creator_addresses[i])
            assert_equal(len(filtered_boxes), self.initial_txs)

        if self.test_type == TestType.Mempool or self.test_type == TestType.Mempool_Timed:
            # Populate the mempool
            print("Populating the mempool...")
            self.populate_mempool(utxo_amount, txs_creators, non_txs_creators)
            # Give mempool time to update
            sleep(3)
            print("Mempool ready!")

            # Verify that all the nodes have the correct amount of transactions in the mempool
            for node in self.sc_nodes:
                assert_equal(len(allTransactions(node, True)["transactions"]), self.initial_txs)

        # Take best block id of every node and assert they all match
        test_start_block_ids = self.get_best_node_block_ids()
        assert_equal(len(set(test_start_block_ids.values())), 1)

        # Output the wallet balance of each node
        print("Node Wallet Balances Before Test...")
        self.log_node_wallet_balances()

        # Start forging on nodes where forger == true
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Forger found - Node{index} - Start Forging...")
                http_start_forging(self.sc_nodes[index])

        # Start timing
        start_time = time.time()
        number_of_txs_creators = len(txs_creators)

        ##########################################################
        ##################### TEST VERSION 1 #####################
        ##########################################################

        if self.test_type == TestType.Mempool:
            ######## RUN UNTIL NODE CREATORS MEMPOOLS ARE EMPTY ########
            # Wait until mempool empty - this should also mean that other nodes mempools are empty (differences will be performance issues)
            end_test = False
            while not end_test:
                end_test = True
                for creator_node in txs_creators:
                    if len(allTransactions(creator_node, False)["transactionIds"]) != 0:
                        end_test = False
                        break
                # TODO: Check this sleep is efficient
                sleep(self.block_rate - 2)

        elif self.test_type == TestType.Mempool_Timed:
            ######## RUN UNTIL TIMER END ########
            while time.time() - start_time < self.test_run_time:
                sleep(1)

        ##########################################################
        ##################### TEST VERSION 2 #####################
        ##########################################################

        elif self.test_type == TestType.Transactions_Per_Second:
            # 1 thread per txs_creator node sending transactions
            self.txs_creator_send_transactions_per_second_to_addresses(utxo_amount, txs_creators,
                                                                           non_txs_creators, True)

        elif self.test_type == TestType.All_Transactions:
            # 1 thread per txs_creator node sending transactions
            self.txs_creator_send_transactions_per_second_to_addresses(utxo_amount, txs_creators,
                                                                           non_txs_creators, False)

        # stop forging
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Stopping Forger Node{index}")
                http_stop_forging(self.sc_nodes[index])

        # Get end time
        end_time = time.time()
        sleep(10)

        # Get information from all nodes
        for i in range(len(self.sc_nodes)):
            # Retrieve node mempool
            try:
                mempool = allTransactions(self.sc_nodes[i], False)
                mempool_transactions = mempool["transactionIds"]
            except Exception:
                raise RequestException("Unable to retrieve mempool transactions")
            number_of_transactions = len(mempool_transactions)
            print(f"Node {i} mempool transactions remaining: {number_of_transactions}")
            # Print mempool of each node if we're running timed test
            if self.test_type == TestType.Mempool_Timed:
                if number_of_transactions > 0:
                    print(f"Node {i} mempool transactions: {mempool_transactions}")

        # Take blockhash of every node and verify they are all the same
        test_end_block_ids = self.get_best_node_block_ids()
        assert_equal(len(set(test_end_block_ids.values())), 1)

        # TODO: Find balance for the node sender and receiver and verify that it's what we expect
        # sum(balance of each node) => total ZEN present at the end of the test
        # Output the wallet balance of each node
        print("Node Wallet Balances After Test...")
        self.log_node_wallet_balances()

        # OUTPUT TEST RESULTS
        for node in self.sc_nodes:
            node_index = self.sc_nodes.index(node)
            start_block_id = test_start_block_ids[node_index]
            current_block_id = test_end_block_ids[node_index]
            total_mined_transactions = 0
            # We don't count the first block, so start from 1
            total_blocks_for_node = 1
            print("### Node" + str(node_index) + " Test Results ###")
            print("Node" + str(node_index) + " Mempool remaining transactions: " + str(
                len(allTransactions(node, False)["transactionIds"])))
            while current_block_id != start_block_id:
                number_of_transactions_mined = len(
                    self.get_node_mined_transactions_by_block_id(node, current_block_id))
                total_mined_transactions += number_of_transactions_mined
                print("Node" + str(node_index) + "- BlockId " + str(current_block_id) + " Mined Transactions: " +
                      str(number_of_transactions_mined))
                total_blocks_for_node += 1
                current_block_id = http_block_findById(node, current_block_id)["block"]["parentId"]
            print("Node" + str(node_index) + " Total Blocks: " + str(total_blocks_for_node))
            print("Node" + str(node_index) + " Total Transactions Mined: " + str(total_mined_transactions))
            print(f"Transactions NOT mined: {self.initial_txs * len(txs_creators) - total_mined_transactions}")
        print(f"\n###\nTEST RUN TIME: {end_time - start_time} seconds\n###")


if __name__ == "__main__":
    PerformanceTest().main()
