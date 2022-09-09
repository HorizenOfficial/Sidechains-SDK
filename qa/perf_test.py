import logging
import math
import random
import time
from multiprocessing import Pool, Value
from time import sleep
from os.path import exists
import csv
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


def get_number_of_transactions_for_node(node):
    return len(allTransactions(node, False)["transactionIds"])


def send_transactions_per_second(txs_creator_node, destination_addresses, utxo_amount, start_time, test_run_time,
                                 transactions_per_second):
    # Run until
    while time.time() - start_time < test_run_time:
        i = 0
        tps_start_time = time.time()
        # Send transactions until the transactions_per_second value has been reached
        while i < transactions_per_second:
            try:
                # Send coins to random destination address
                sendCoinsToAddress(txs_creator_node, random.choice(destination_addresses), utxo_amount, 0)
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
        # Adjust the initial_txs and test_run_time config values until this value is under 1 second.
        elif completion_time > 1:
            print("WARNING:  Number of transactions sent has exceeded 1 second - Adjust the initial_txs and "
                  "test_run_time config values ")


class PerformanceTest(SidechainTestFramework):
    CONFIG_FILE = "./performance/perf_test.json"
    CSV_FILE = "./tps_result.csv"
    sc_nodes_bootstrap_info = None
    perf_data = deserialize_perf_test_json(CONFIG_FILE)
    test_type = TestType(perf_data["test_type"])
    initial_ft_amount = perf_data["initial_ft_amount"]
    sc_node_data = perf_data["nodes"]
    sc_nodes_list = list(sc_node_data)
    initial_txs = perf_data["initial_txs"]
    test_run_time = perf_data["test_run_time"]
    block_rate = perf_data["block_rate"]
    connection_map = {}
    topology = NetworkTopology(perf_data["network_topology"])
    latency_settings = get_latency_config(perf_data)

    csv_data = {"test_type": test_type,
                "initial_ft_amount": initial_ft_amount,
                "test_run_time": test_run_time, "block_rate": block_rate,
                "use_multiprocessing": perf_data["use_multiprocessing"], "initial_txs": initial_txs,
                "network_topology": topology,
                "get_peer_spec_latency": latency_settings.get_peer_spec,
                "peer_spec_latency": latency_settings.peer_spec, "transaction_latency": latency_settings.transaction,
                "block_latency": latency_settings.block,
                "request_modifier_spec_latency": latency_settings.request_modifier_spec,
                "modifiers_spec_latency": latency_settings.modifiers_spec,
                "n_nodes": len(sc_node_data), "n_forgers": sum(map(lambda x: x["forger"] == True, sc_nodes_list)),
                "n_tx_creator": sum(map(lambda x: x["tx_creator"] == True, sc_nodes_list)), "initial_balances": [],
                "mined_transactions": 0, "mined_blocks": 0, "end_test_run_time": 0, "end_balances": [],
                "endpoint_calls": 0, "errors": 0, "not_mined_transactions": 0, "mempool_transactions": 0,
                "tps_total": 0, "tps_mined": 0, "blocks_ts": [], "node_api_errors": 0
                }

    def fill_csv(self):
        if not exists(self.CSV_FILE):
            f = open(self.CSV_FILE, 'w')
            writer = csv.writer(f)

            # write the header
            writer.writerow(list(self.csv_data.keys()))
            # write values
            writer.writerow(list(self.csv_data.values()))

            f.close()
        else:
            f = open(self.CSV_FILE, 'a')
            writer = csv.writer(f)

            # write values
            writer.writerow(list(self.csv_data.values()))

    def get_node_configuration(self, mc_node):
        sc_nodes = list(self.sc_node_data)
        node_configuration = []

        logging.info(f"Network Topology: {self.topology.name}")
        max_connections = 0
        last_index = sc_nodes.index(self.sc_node_data[-1])

        for index, sc_node in enumerate(sc_nodes):
            if (index == 0 or index == last_index) and self.topology == NetworkTopology.DaisyChain:
                max_connections = 1
            elif (index != 0 and index != last_index) and self.topology == NetworkTopology.DaisyChain:
                max_connections = 2
            elif self.topology == NetworkTopology.Ring:
                max_connections = 2
            elif index == 0 and self.topology == NetworkTopology.Star:
                max_connections = len(self.sc_node_data) - 1
            elif index != 0 and self.topology == NetworkTopology.Star:
                max_connections = 1

            node_configuration.append(
                SCNodeConfiguration(
                    mc_connection_info=MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                    max_connections=max_connections,
                    block_rate=self.block_rate,
                    latency_settings=self.latency_settings
                )
            )
        return node_configuration

    def setup_nodes(self):
        # Start 1 MC node
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        print("Initializing test directory " + self.options.tmpdir)
        mc_node = self.nodes[0]
        sc_nodes = self.get_node_configuration(mc_node)

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, 100),
            *sc_nodes
        )

        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * self.block_rate / 2)

    def sc_setup_nodes(self):
        if self.perf_data["use_multiprocessing"]:
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
        node_count = len(self.sc_nodes)
        node_final_position = node_count - 1
        node = 0

        # Daisy Chain Topology
        if self.topology == NetworkTopology.DaisyChain or self.topology == NetworkTopology.Ring:
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
        if self.topology == NetworkTopology.Ring:
            # Connect final node to first node
            connect_sc_nodes(self.sc_nodes[node], 1)
            self.create_node_connection_map(0, [node])
            self.create_node_connection_map(node, [0])
        # Star Topology
        if self.topology == NetworkTopology.Star:
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
            destination_address = http_wallet_createPrivateKey25519(random.choice(self.sc_nodes))
            for i in range(self.initial_txs):
                # Populate the mempool - so don't mine a block
                sendCoinsToAddress(txs_creator_nodes[j], destination_address, utxo_amount, 0)
                if i % 1000 == 0:
                    print("Node " + str(j) + " sent txs: " + str(i))
            print("Node " + str(j) + " totally sent txs: " + str(self.initial_txs))

    def get_best_node_block_ids(self):
        block_ids = {}
        index = 0
        errors = 0
        for node in self.sc_nodes:
            try:
                block = http_block_best(node)
                block_ids[self.sc_nodes.index(node)] = block["id"]
            except Exception as e:
                print(f"Node API ERROR {index}")
                errors += 1
                block = http_block_best(node)
                block_ids[self.sc_nodes.index(node)] = block["id"]
            index += 1
        print("Block ids: " + str(block_ids))
        return (block_ids, errors)

    def find_boxes_of_address(self, boxes, address):
        address_boxes = []
        for box in boxes:
            if box["proposition"]["publicKey"] == address:
                address_boxes.append(box)
        return address_boxes

    def log_node_wallet_balances(self):
        balances = []
        # Output the balance of each node
        for index, node in enumerate(self.sc_nodes):
            wallet_boxes = http_wallet_allBoxesOfType(node, "ZenBox")
            wallet_balance = 0
            for box in wallet_boxes:
                wallet_balance += box["value"]
            balances.append(wallet_balance)
            if self.sc_nodes_list[index]["tx_creator"]:
                print(f"Node{index} (Transaction Creator) Wallet Balance: {wallet_balance}")
            else:
                print(f"Node{index} Wallet Balance: {wallet_balance}")
        return balances

    def get_node_mined_transactions_by_block_id(self, node, block_id):
        result = http_block_findById(node, block_id)
        return result["block"]["sidechainTransactions"]

    def txs_creator_send_transactions_per_second_to_addresses(self, utxo_amount, txs_creators, tps_test):
        start_time = time.time()

        if tps_test:
            # Each node needs to be able to send a number of transactions per second, without going over 1 second, or
            # timing out - May need some fine-tuning.
            transactions_per_second = math.floor(self.initial_txs / self.test_run_time)
            for i in range(len(txs_creators)):
                print(
                    f"Running Throughput: {transactions_per_second} Transactions Per Second for Creator "
                    f"Node(Node{i})...")

            while counter.value < self.initial_txs * len(txs_creators) and (
                    (time.time() - start_time) < self.test_run_time):
                # Create the multiprocess pool
                with Pool(initializer=init_globals, initargs=(counter, errors)) as pool:
                    args = []
                    # Add send_transactions_per_second arguments to args for each tx_creator_node
                    # starmap runs them all in parallel
                    for index, sc_node in enumerate(self.sc_nodes_list):
                        if sc_node["tx_creator"]:
                            # Create array of destination addresses for all nodes excluding the one we send from
                            sc_nodes = self.sc_nodes.copy()
                            del sc_nodes[index]
                            destination_addresses = []
                            for node in sc_nodes:
                                destination_addresses.append(http_wallet_createPrivateKey25519(node))

                            args.append((self.sc_nodes[index], destination_addresses, utxo_amount, start_time,
                                         self.test_run_time, transactions_per_second))
                    pool.starmap(send_transactions_per_second, args)

                print(f"... Sent {counter.value} Transactions ...")
                print(f"Timer: {time.time() - start_time} and counter {counter.value}")
        else:
            # We're not interested in transactions per second, just fire all transactions as fast as possible
            for _ in range(self.initial_txs):
                with Pool(initializer=init_globals, initargs=(counter, errors)) as pool:
                    args = []
                    for txs_creator in txs_creators:
                        # Create a single random node destination address per process
                        destination_address = http_wallet_createPrivateKey25519(random.choice(self.sc_nodes))
                        args = [(txs_creator, destination_address, utxo_amount, 0)]

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
        ft_amount = self.initial_ft_amount
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
                assert_equal(len(allTransactions(node, True)["transactions"]), self.initial_txs * len(txs_creators))

        # Take best block id of every node and assert they all match
        test_start_block_ids, _ = self.get_best_node_block_ids()
        assert_equal(len(set(test_start_block_ids.values())), 1)

        # Output the wallet balance of each node
        print("Node Wallet Balances Before Test...")
        initial_balances = self.log_node_wallet_balances()
        self.csv_data["initial_balances"] = initial_balances

        # Start forging on nodes where forger == true
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Forger found - Node{index} - Start Forging...")
                http_start_forging(self.sc_nodes[index])

        # Start timing
        start_time = time.time()

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
            self.txs_creator_send_transactions_per_second_to_addresses(utxo_amount, txs_creators, True)

        elif self.test_type == TestType.All_Transactions:
            # 1 thread per txs_creator node sending transactions
            self.txs_creator_send_transactions_per_second_to_addresses(utxo_amount, txs_creators, False)

        # stop forging
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Stopping Forger Node{index}")
                http_stop_forging(self.sc_nodes[index])

        # Get end time
        end_time = time.time()
        # Give nodes time to recover
        sleep(30)

        # Take blockhash of every node and verify they are all the same
        test_end_block_ids, api_errors = self.get_best_node_block_ids()
        assert_equal(len(set(test_end_block_ids.values())), 1)

        # TODO: Find balance for the node sender and receiver and verify that it's what we expect
        # sum(balance of each node) => total ZEN present at the end of the test
        # Output the wallet balance of each node
        print("Node Wallet Balances After Test...")
        end_balances = self.log_node_wallet_balances()
        self.csv_data["end_balances"] = end_balances

        # OUTPUT TEST RESULTS
        blocks_per_node = []
        blocks_ts = []
        mempool_transactions = []
        iteration = 0
        for node in self.sc_nodes:
            node_index = self.sc_nodes.index(node)
            start_block_id = test_start_block_ids[node_index]
            current_block_id = test_end_block_ids[node_index]
            total_mined_transactions = 0
            # We don't count the first block, so start from 1
            total_blocks_for_node = 1
            print("### Node" + str(node_index) + " Test Results ###")

            # Retrieve node mempool
            mempool_txs = len(allTransactions(node, False)["transactionIds"])
            print("Node" + str(node_index) + " Mempool remaining transactions: " + str(mempool_txs))
            mempool_transactions.append(mempool_txs)

            while current_block_id != start_block_id:
                number_of_transactions_mined = len(
                    self.get_node_mined_transactions_by_block_id(node, current_block_id))
                total_mined_transactions += number_of_transactions_mined
                print("Node" + str(node_index) + "- BlockId " + str(current_block_id) + " Mined Transactions: " +
                      str(number_of_transactions_mined))
                total_blocks_for_node += 1
                current_block = http_block_findById(node, current_block_id)["block"]
                current_block_id = current_block["parentId"]
                if (iteration == 0):
                    blocks_ts.append(current_block["timestamp"])
            blocks_per_node.append(total_blocks_for_node)
            iteration += 1
            print("Node" + str(node_index) + " Total Blocks: " + str(total_blocks_for_node))
            print("Node" + str(node_index) + " Total Transactions Mined: " + str(total_mined_transactions))
        not_mined_transactions = self.initial_txs * len(txs_creators) - total_mined_transactions
        print(f"Transactions NOT mined: {not_mined_transactions}")
        test_run_time = end_time - start_time
        print(f"\n###\nTEST RUN TIME: {test_run_time} seconds\n###")

        self.csv_data["mined_transactions"] = total_mined_transactions
        self.csv_data["end_test_run_time"] = test_run_time
        self.csv_data["mined_blocks"] = blocks_per_node
        self.csv_data["endpoint_calls"] = counter.value
        self.csv_data["errors"] = errors.value
        self.csv_data["not_mined_transactions"] = not_mined_transactions
        self.csv_data["mempool_transactions"] = mempool_transactions
        self.csv_data["tps_total"] = (mempool_transactions[0] + total_mined_transactions) / test_run_time
        self.csv_data["tps_mined"] = total_mined_transactions / test_run_time
        self.csv_data["blocks_ts"] = blocks_ts
        self.csv_data["node_api_errors"] = api_errors

        self.fill_csv()


if __name__ == "__main__":
    PerformanceTest().main()
