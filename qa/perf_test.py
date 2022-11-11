import logging
import math
import multiprocessing
import random
import time
import urllib.request
from multiprocessing import Pool, Value
from time import sleep
import os
import gzip
import csv
import numpy as np
import psutil
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LatencyConfig
from httpCalls.block.best import http_block_best
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.block.forging import http_start_forging, http_stop_forging
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress, sendCoinsToAddressExtended, \
    sendCointsToMultipleAddress
from httpCalls.transaction.makeForgerStake import makeForgerStake
from httpCalls.transaction.createCoreTransaction import http_create_core_transaction
from httpCalls.transaction.sendTransaction import http_send_transaction
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.wallet.allBoxesOfType import http_wallet_allBoxesOfType
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.wallet.createVrfSecret import http_wallet_createVrfSecret
from test_framework.util import assert_false, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_equal
from SidechainTestFramework.scutil import assert_true, bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    deserialize_perf_test_json, connect_sc_nodes, start_sc_nodes_with_multiprocessing, generate_next_block
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


def get_number_of_transactions_for_node(node):
    return len(allTransactions(node, False)["transactionIds"])


def send_transactions_per_second(sc_node_index, txs_creator_node, txs_bytes, destination_addresses, utxo_amount,
                                 start_time, test_run_time,
                                 transactions_per_second, extended_transaction=False, big_transaction=False,
                                 send_coins_to_address=False):
    iteration = 0
    response_times = []
    request_type = ""

    # Run until
    while time.time() - start_time < test_run_time:
        i = 0
        tps_start_time = time.time()
        # Log response time average and cpu/ram usage
        if iteration != 0 and iteration % 10 == 0:
            logging.debug(f"Transactions_Per_Second - Node{sc_node_index}:: Average {request_type} response time:: "
                          f"{np.array(response_times).mean()}")
            get_cpu_ram_usage()
        # Send transactions until the transactions_per_second value has been reached
        while i < transactions_per_second:
            try:
                if send_coins_to_address:
                    if big_transaction:
                        request_type = "sendCointsToMultipleAddress"
                        request_start = time.time()
                        sendCointsToMultipleAddress(txs_creator_node, destination_addresses,
                                                    [utxo_amount for _ in range(len(destination_addresses))], 0)
                        request_end = time.time()
                        response_times.append(request_end - request_start)
                    elif extended_transaction:
                        request_type = "sendCoinsToAddressExtended"
                        request_start = time.time()
                        sendCoinsToAddressExtended(txs_creator_node, random.choice(destination_addresses), utxo_amount,
                                                   0)
                        request_end = time.time()
                        response_times.append(request_end - request_start)
                    else:
                        request_type = "sendCoinsToAddress"
                        request_start = time.time()
                        sendCoinsToAddress(txs_creator_node, random.choice(destination_addresses), utxo_amount, 0)
                        request_end = time.time()
                        response_times.append(request_end - request_start)
                else:
                    request_type = "sendTransaction"
                    request_start = time.time()
                    http_send_transaction(txs_creator_node, txs_bytes[i + iteration * transactions_per_second])
                    request_end = time.time()
                    response_times.append(request_end - request_start)

            except Exception as e:
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
            # logging.debug(f"WARNING:  Node{sc_node_index} Number of transactions sent has exceeded 1 second - Adjust the initial_txs and test_run_time config values ")
            logging.debug(f"Node{sc_node_index} TIME TO COMPLETE {transactions_per_second} TPS: {completion_time}(s)")
        iteration += 1

# TODO: Multi Machine
def get_cpu_ram_usage(cpu_usage=True, ram_usage=False):
    if cpu_usage:
        per_cpu = psutil.cpu_percent(interval=1, percpu=True)
        cpu_usage_message = "CPU CORE % USAGE: "
        for idx, usage in enumerate(per_cpu):
            cpu_usage_message += f" CORE_{idx}: {usage}%"
        logging.debug(cpu_usage_message)
    if ram_usage:
        mem_usage = psutil.virtual_memory()
        logging.debug(f"Memory Total: {mem_usage.total / (1024 ** 3):.2f}GB")
        logging.debug(f"Memory Free: {mem_usage.percent}%")
        logging.debug(f"Used: {mem_usage.used / (1024 ** 3):.2f}GB")

# TODO: Multi Machine
def get_running_process_cpu_ram_usage():
    start_time = time.time()
    running_processes = ""
    for process in [psutil.Process(pid) for pid in psutil.pids()]:
        try:
            name = process.name()
            mem = process.memory_percent()
            cpu = process.cpu_percent(interval=0.5)

            if cpu > 0:
                running_processes += f"{process.name()}, "
        except psutil.NoSuchProcess as e:
            logging.debug(e.pid, "killed before analysis")
        else:
            if cpu > 0:
                logging.debug("Name:", name)
                logging.debug("CPU%:", cpu)
                logging.debug("MEM%:", mem)
    logging.debug(f"RUNNING PROCESSES: {running_processes}")
    end_time = time.time()
    logging.debug(f"TIME TAKEN FOR CPU/RAM STATS: {end_time - start_time}")


class NodeData:
    def __init__(self, forger, tx_creator, machine=None):
        self.forger = forger
        self.tx_creator = tx_creator
        self.machine = machine


class MachineCredentials:
    def __init__(self, ip_address, base_directory, ssh_username, ssh_password):
        self.ip_address = ip_address
        self.base_directory = base_directory
        self.ssh_username = ssh_username
        self.ssh_password = ssh_password


class PerformanceTest(SidechainTestFramework):
    MAX_TRANSACTION_OUTPUT = 998
    FORGING_STAKE_AMOUNT = 1
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
    extended_transaction = perf_data["extended_transaction"]
    big_transaction = perf_data["big_transaction"]
    send_coins_to_address = perf_data["send_coins_to_address"]
    multi_machine = perf_data["multi_machine"]
    connection_map = {}
    topology = NetworkTopology(perf_data["network_topology"])

    csv_data = {"test_type": test_type,
                "initial_ft_amount": initial_ft_amount,
                "test_run_time": test_run_time, "block_rate": block_rate,
                "use_multiprocessing": perf_data["use_multiprocessing"], "initial_txs": initial_txs,
                "network_topology": topology,
                "transaction_latency": [],
                "block_latency": [],
                "modifiers_spec_latency": [],
                "n_nodes": len(sc_node_data), "n_forgers": sum(map(lambda x: x["forger"] == True, sc_nodes_list)),
                "n_tx_creator": sum(map(lambda x: x["tx_creator"] == True, sc_nodes_list)), "initial_balances": [],
                "mined_transactions": 0, "mined_blocks": 0, "end_test_run_time": 0, "end_balances": [],
                "endpoint_calls": 0, "errors": 0, "not_mined_transactions": 0, "mempool_transactions": 0,
                "tps_total": 0, "tps_mined": 0, "blocks_ts": [], "node_api_errors": 0,
                "extended_transaction": extended_transaction,
                "mined_txs": [],
                "big_transaction": big_transaction,
                "send_coins_to_address": send_coins_to_address,
                "block_tps": [],
                "forks": [],
                "blocks_mined": 0,
                "max_forks_length": []
                }

    def get_latency_config(self):
        node_latency_configs = []
        for node in self.sc_node_data:
            node_latency_configs.append(LatencyConfig(
                node["latency_settings"]["get_peer_spec"],
                node["latency_settings"]["peer_spec"],
                node["latency_settings"]["transaction"],
                node["latency_settings"]["block"],
                node["latency_settings"]["request_modifier_spec"],
                node["latency_settings"]["modifiers_spec"]
            ))

        return node_latency_configs

    def get_machine_credentials(self):
        machine_credentials = []
        try:
            machines = self.perf_data["machine_credentials"]
        except Exception:
            raise "Unable to retrieve machine data from the config"

        for machine in machines:
            machine_credentials.append(
                MachineCredentials(machine["ip_address"],
                                   machine["base_directory"],
                                   machine["ssh_username"],
                                   machine["ssh_password"])
            )
        return machine_credentials

    def get_node_data(self):
        node_data = []
        nodes = self.sc_node_data

        if nodes is None:
            raise "No nodes data present in the config"

        for node in nodes:
            if self.multi_machine:
                machine = node["machine"]
                if machine is None or machine == "":
                    raise Exception("No Machine set for node, check config.")
            else:
                machine = ""
            try:
                node_data.append(
                    NodeData(node["forger"],
                             node["tx_creator"],
                             machine))
            except Exception as e:
                raise e

        return node_data

    def fill_csv(self):
        if not os.path.exists(self.CSV_FILE):
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
        max_connections = 100
        last_index = sc_nodes.index(self.sc_node_data[-1])
        latency_configurations = self.get_latency_config()

        if self.multi_machine:
            machines = self.get_machine_credentials()

        for index, _ in enumerate(sc_nodes):
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

            if self.multi_machine:
                try:
                    mc_hostname = urllib.request.urlopen('https://ident.me', timeout=10).read().decode('utf8')
                except Exception as e:
                    raise Exception(f"Unable to access 'https://ident.me' to retrieve public ip address. {e}")
                host_machine = self.sc_node_data[index]["machine"]
                try:
                    machine_credentials = machines[host_machine]
                except Exception as e:
                    raise Exception(f"Unable to retrieve machine credentials for node {e}")
            else:
                mc_hostname = mc_node.hostname
                machine_credentials = None

            node_configuration.append(
                SCNodeConfiguration(
                    mc_connection_info=MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_hostname, websocket_port_by_mc_node_index(0))),
                    max_connections=max_connections,
                    block_rate=self.block_rate,
                    latency_settings=latency_configurations[index],
                    machine_credentials=machine_credentials,
                    log_akka_messages=self.sc_node_data[index]['log_akka_messages'] if 'log_akka_messages'  in self.sc_node_data[index] else "ERROR"
                )
            )
        return node_configuration

    def setup_nodes(self):
        # Start 1 MC node (on local machine)
        if self.multi_machine:
            return start_nodes(1, self.options.tmpdir)
        else:
            return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        print("Initializing test directory " + self.options.tmpdir)
        logging.info("Initializing test directory " + self.options.tmpdir)
        mc_node = self.nodes[0]
        sc_nodes = self.get_node_configuration(mc_node)

        if self.multi_machine:
            machines = self.get_machine_credentials()
        else:
            machines = None

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, self.FORGING_STAKE_AMOUNT),
            *sc_nodes
        )
        self.options.restapitimeout = 20
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * self.block_rate * 5 / 2,
                                                                 machines)

    def sc_setup_nodes(self):
        # TODO Refactor to allow deploying node to single machine without multiprocessing
        if self.perf_data["use_multiprocessing"]:
            node_data = self.get_node_data()
            print(f"Node Data: {node_data}")
            machine_credentials = None
            if self.multi_machine:
                machine_credentials = self.get_machine_credentials()

            return start_sc_nodes_with_multiprocessing(node_data, self.options.tmpdir,
                                                       machine_credentials=machine_credentials)
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
            connect_sc_nodes(self.sc_nodes[node], 0)
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
        # P2P Topology
        if self.topology == NetworkTopology.PeerToPeer:
            for i in range(0, node_count):
                for j in range(0, node_count):
                    existing_node_connection = j in self.connection_map
                    if i != j and (existing_node_connection and i not in self.connection_map[
                        j] or not existing_node_connection):
                        logging.debug(f"Connect {i} to {j}")
                        connect_sc_nodes(self.sc_nodes[i], j)
                        self.create_node_connection_map(i, [j])

        # Strong connected forger Topology
        if self.topology == NetworkTopology.StrongConnectedForgers:
            forgers = self.get_forger_nodes_indexes()
            for i in range(0, node_count):
                if i in forgers:
                    for j in range(0, node_count):
                        if j in forgers:
                            existing_node_connection = j in self.connection_map
                            if i != j and (existing_node_connection and i not in self.connection_map[
                                j] or not existing_node_connection):
                                logging.debug(f"Connect {i} to {j}")
                                connect_sc_nodes(self.sc_nodes[i], j)
                                self.create_node_connection_map(i, [j])
                else:
                    near_forger = random.choice(forgers)
                    logging.debug(f"Connect {i} to {near_forger}")
                    connect_sc_nodes(self.sc_nodes[i], near_forger)
                    self.create_node_connection_map(i, [near_forger])

        logging.info(f"NETWORK TOPOLOGY CONNECTION MAP: {self.connection_map}")
        self.sc_sync_all()

    def get_txs_creators_and_non_creators(self):
        txs_creators = []
        non_txs_creator = []
        for index, node in enumerate(self.sc_nodes_list):
            if node["tx_creator"]:
                assert_false(node["forger"])
                txs_creators.append(self.sc_nodes[index])
            else:
                non_txs_creator.append(self.sc_nodes[index])
        return txs_creators, non_txs_creator

    def get_forger_nodes_indexes(self):
        forgers = []
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                forgers.append(index)
        return forgers

    def find_forger_nodes(self):
        forger_nodes = []
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                forger_nodes.append(self.sc_nodes[index])
        return forger_nodes

    def populate_mempool(self, utxo_amount, txs_creator_nodes, extended_transaction=False, big_transaction=False,
                         send_coins_to_address=True):
        # Get the destination addresses of the transactions
        destination_addresses = self.create_destination_addresses()
        # Get transaction bytes
        txs_bytes = self.create_raw_transactions(txs_creator_nodes, utxo_amount, destination_addresses)

        populate_mempool_start_time = time.time()
        response_times = []

        for j in range(len(txs_creator_nodes)):
            start_time = time.time()
            for i in range(self.initial_txs):
                if send_coins_to_address:

                    if big_transaction:
                        request_type = "sendCointsToMultipleAddress"
                        request_start = time.time()
                        sendCointsToMultipleAddress(txs_creator_nodes[j], destination_addresses,
                                                    [utxo_amount for _ in range(len(destination_addresses))], 0)
                        request_end = time.time()
                        response_times.append(request_end - request_start)
                    elif extended_transaction:
                        request_type = "sendCoinsToAddressExtended"
                        request_start = time.time()
                        sendCoinsToAddressExtended(txs_creator_nodes[j], random.choice(destination_addresses),
                                                   utxo_amount, 0)
                        request_end = time.time()
                        response_times.append(request_end - request_start)
                    else:
                        request_type = "sendCoinsToAddress"
                        request_start = time.time()
                        sendCoinsToAddress(txs_creator_nodes[j], random.choice(destination_addresses), utxo_amount, 0)
                        request_end = time.time()
                        response_times.append(request_end - request_start)
                else:
                    request_type = "sendTransaction"
                    request_start = time.time()
                    http_send_transaction(txs_creator_nodes[j], txs_bytes[j][i])
                    request_end = time.time()
                    response_times.append(request_end - request_start)

                if i != 0 and i % 1000 == 0:
                    end_time = time.time()
                    logging.debug("Creator Node " + str(j) + " sent txs: " + str(i))
                    logging.debug(f"Populate Mempool Node{str(j)} time taken to send {str(i)} transactions: "
                                  f"{end_time - start_time}")
                    logging.debug(f"Populate Mempool Node{str(j)} - Average {request_type} response time: "
                                  f"{np.array(response_times).mean()}")
                    get_cpu_ram_usage()

            logging.debug("Creator Node " + str(j) + " total sent txs: " + str(self.initial_txs))
            populate_mempool_end_time = time.time()
            logging.info(f"Total Time to Populate Mempool Node{str(j)}: "
                         f"{populate_mempool_end_time - populate_mempool_start_time}")

    def get_best_node_block_ids(self):
        block_ids = {}
        index = 0
        errors = 0
        for node in self.sc_nodes:
            n_try = 0
            error = True
            while n_try < 20 and error:
                try:
                    block = http_block_best(node)
                    block_ids[self.sc_nodes.index(node)] = block["id"]
                    error = False
                except Exception as e:
                    logging.warning(f"Node API ERROR {index}")
                    errors += 1
                    n_try += 1

            index += 1
        logging.debug("Block ids: " + str(block_ids))
        return (block_ids, errors)

    def find_boxes_of_address(self, boxes, address):
        address_boxes = []
        for box in boxes:
            if box["proposition"]["publicKey"] == address:
                address_boxes.append(box)
        return address_boxes

    def log_node_wallet_balances(self):
        balances = []
        errors = 0
        # Output the balance of each node
        for index, node in enumerate(self.sc_nodes):
            n_try = 0
            error = True
            while n_try < 20 and error:
                try:
                    wallet_boxes = http_wallet_allBoxesOfType(node, "ZenBox")
                    error = False
                except Exception as e:
                    logging.warning(f"Node API ERROR {index}")
                    n_try += 1
                    errors += 1
            wallet_balance = 0
            for box in wallet_boxes:
                wallet_balance += box["value"]
            balances.append(wallet_balance)
            if self.sc_nodes_list[index]["tx_creator"]:
                logging.info(f"Node{index} (Transaction Creator) Wallet Balance: {wallet_balance}")
            else:
                logging.info(f"Node{index} Wallet Balance: {wallet_balance}")
        return (balances, errors)

    def get_node_mined_transactions_by_block_id(self, node, block_id):
        result = http_block_findById(node, block_id)
        return result["block"]["sidechainTransactions"]

    def create_transactions(self, node_index, txs_creator_node, input_boxes, destination_addresses, utxo_amount,
                            transactions):
        txs = []

        while len(input_boxes) != 0:
            if self.big_transaction:
                outputs = []
                for address in destination_addresses:
                    outputs.append({"publicKey": address, "value": int(utxo_amount)})
                inputs = [{"boxId": input_boxes[j]["id"]} for j in range(len(destination_addresses))]
                txs.append(http_create_core_transaction(txs_creator_node, inputs, outputs))
                input_boxes = input_boxes[len(destination_addresses):]
            else:
                outputs = [{"publicKey": random.choice(destination_addresses), "value": int(utxo_amount)}]
                txs.append(http_create_core_transaction(txs_creator_node, [{"boxId": input_boxes[0]["id"]}], outputs))
                input_boxes.pop(0)
            if len(txs) % 1000 == 0:
                logging.debug(f"Node{node_index} Created {len(txs)} transactions...")
        logging.info(f"!! Node{node_index} Finished Creating {len(txs)} Transactions !!")

        transactions.insert(node_index, txs)

    def create_destination_addresses(self):
        # Get the destination addresses of the transactions
        destination_addresses = []
        # Create array of destination addresses for all nodes
        for node in self.sc_nodes:
            destination_addresses.append(http_wallet_createPrivateKey25519(node))
        return destination_addresses

    def create_raw_transactions(self, txs_creators, utxo_amount, destination_addresses):
        # Pre-compute all the transactions bytes that we want to send
        manager = multiprocessing.Manager()
        transactions = manager.list()
        with Pool() as pool:
            args = []
            # Add send_transactions_per_second arguments to args for each tx_creator_node
            # starmap runs them all in parallel
            index = 0
            logging.info("Creating raw transactions...")
            for creator_node in txs_creators:

                # If we want to have a big transaction of 10 outputs we need to multiply the output value by 10
                if self.big_transaction:
                    destination_addresses = []
                    for i in range(0, 10):
                        destination_addresses.append(http_wallet_createPrivateKey25519(creator_node))
                # Retrieve all the ZenBoxes of the node creator
                all_boxes = http_wallet_allBoxesOfType(creator_node, "ZenBox")
                assert_true(len(all_boxes) > 0)

                args.append((index, creator_node, all_boxes, destination_addresses, utxo_amount, transactions))
                index += 1
            pool.starmap(self.create_transactions, args)

        return transactions

    def txs_creator_send_transactions_per_second_to_addresses(self, utxo_amount, txs_creators, tps_test,
                                                              send_coins_to_address):
        # Get the destination addresses of the transactions
        destination_addresses = self.create_destination_addresses()
        # Pre-compute all the transactions bytes that we want to send
        transactions = self.create_raw_transactions(txs_creators, utxo_amount,
                                                    destination_addresses) if not send_coins_to_address else [[] for _
                                                                                                              in range(
                len(txs_creators))]

        if tps_test:
            # Each node needs to be able to send a number of transactions per second, without going over 1 second, or
            # timing out - May need some fine-tuning.
            transactions_per_second = math.floor(self.initial_txs / self.test_run_time)

            for i in range(len(txs_creators)):
                logging.info(
                    f"Running Throughput: {transactions_per_second} Transactions Per Second for Creator "
                    f"Node(Node{i})...")

            start_time = self.start_forge()
            while counter.value < self.initial_txs * len(txs_creators) and (
                    (time.time() - start_time) < self.test_run_time):
                # Create the multiprocess pool
                with Pool(initializer=init_globals, initargs=(counter, errors)) as pool:
                    args = []
                    # Add send_transactions_per_second arguments to args for each tx_creator_node
                    # starmap runs them all in parallel
                    index = 0
                    for sc_node in txs_creators:
                        args.append((self.sc_nodes.index(sc_node), sc_node, transactions[index], destination_addresses,
                                     utxo_amount, start_time, self.test_run_time, transactions_per_second,
                                     self.extended_transaction, self.big_transaction, self.send_coins_to_address))
                        index += 1
                    pool.starmap(send_transactions_per_second, args)
            logging.info(f"... Sent {counter.value} Transactions ...")
            logging.info(f"Timer: {time.time() - start_time} and counter {counter.value}")
        else:
            start_time = self.start_forge()
            # We're not interested in transactions per second, just fire all transactions as fast as possible
            for _ in range(self.initial_txs):
                with Pool(initializer=init_globals, initargs=(counter, errors)) as pool:
                    args = []

                    index = 0
                    for sc_node in txs_creators:
                        if self.send_coins_to_address:
                            args = [(sc_node, destination_addresses[index], utxo_amount, 0)]
                        else:
                            args.append((sc_node, transactions[index]))
                        index += 1

                    while counter.value < self.initial_txs and ((time.time() - start_time) < self.test_run_time):
                        try:
                            if self.send_coins_to_address:
                                pool.starmap(sendCoinsToAddress, args)
                            else:
                                pool.starmap(http_send_transaction, args)
                        except Exception:
                            errors.value += 1
                        counter.value += 1
            logging.info(f"Firing Transactions Ended After: {time.time() - start_time}")
            logging.info(f"Total Nodes creator sent {counter.value} transactions out of a possible {self.initial_txs} "
                         f"in {time.time() - start_time} seconds.")

        logging.info(f"Total Nodes creator ERRORS ENCOUNTERED: {errors.value}")
        return start_time

    def start_forge(self):
        # Start forging on nodes where forger == true
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                logging.info(f"Forger found - Node{index} - Start Forging...")
                http_start_forging(self.sc_nodes[index])
        # Start timing
        return time.time()

    def get_fork_length(self, line, max_fork_length):
        tmp = line.split("TPS-TEST:")
        block_removed = tmp[1].split("blocks are to be removed")
        fork_length = int(block_removed[0]) 
        if (fork_length > max_fork_length):
            return fork_length
        else:
            return max_fork_length

    def scan_logs_for_forks(self):
        for i in range(len(self.sc_nodes)):
            assert_true(os.path.exists(self.options.tmpdir+"/sc_node"+str(i)))
            last_fork = 0
            max_fork_length = 0
            for filename in os.scandir(self.options.tmpdir+"/sc_node"+str(i)+"/log/"):
                if (".gz" in filename.name):
                    with gzip.open(filename, 'r') as fp:
                        for line in fp:
                            if 'blocks are to be removed' in str(line):
                                max_fork_length = self.get_fork_length(str(line), max_fork_length)
                else:
                    with open(filename, 'r') as fp:
                        logging.info(f"Check node {i} log...")
                        for line in fp:
                            if 'the fork number' in line:
                                tmp = line.split("fork number")
                                last_fork = int(tmp[1].split("has been")[0])
                            if 'blocks are to be removed' in line:
                                max_fork_length = self.get_fork_length(line, max_fork_length)

            self.csv_data["forks"].append(last_fork)
            self.csv_data["max_forks_length"].append(max_fork_length)

    def run_test(self):
        mc_nodes = self.nodes
        self.set_topology()

        # Declare SC Addresses
        txs_creator_addresses = []
        ft_addresses = []
        forger_addresses = []
        forger_vrf_pks = []
        ft_amount = self.initial_ft_amount
        mc_return_address = mc_nodes[0].getnewaddress()

        # Get tx creator nodes and non tx creator nodes
        txs_creators, non_txs_creators = self.get_txs_creators_and_non_creators()

        # Send funds to tx creator nodes
        for i in range(len(txs_creators)):
            ft_addresses.append(http_wallet_createPrivateKey25519(txs_creators[i]))
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                          ft_addresses[i], ft_amount, mc_return_address)
            txs_creator_addresses.append(
                http_wallet_createPrivateKey25519(txs_creators[i]))
        self.sc_sync_all()

        # Generate 2 SC blocks to include all the FTs.
        forger_nodes = self.find_forger_nodes()
        generate_next_blocks(forger_nodes[0], "first node", 1)[0]
        self.sc_sync_all()
        generate_next_blocks(forger_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        # Verify that every tx_creator node received the FT
        for node in txs_creators:
            # Multiply ft_amount by 1e8 to get zentoshi value
            assert_equal(http_wallet_balance(node), ft_amount * 1e8)

        # Send funds to every forger nodes
        # We skip the first one because it already receives it in the sidechain creation phase
        for i in range(1, len(forger_nodes)):
            # Create new forger entity
            forger_addresses.append(http_wallet_createPrivateKey25519(forger_nodes[i]))
            forger_vrf_pks.append(http_wallet_createVrfSecret(forger_nodes[i]))

            # Send some funds
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                          forger_addresses[-1], self.FORGING_STAKE_AMOUNT, mc_return_address)
        self.sc_sync_all()
        generate_next_blocks(forger_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        # Create many UTXOs in a single transaction to multiple addresses
        # Taking FT box and splitting into many UTXOs to enable the creation of multiple transactions for the next part
        # of the test (population of the mempool)
        if self.big_transaction:
            utxo_amount = (ft_amount * 1e8 / self.initial_txs) / 10
            tx_to_create = int(self.initial_txs * 10 / self.MAX_TRANSACTION_OUTPUT)
            utxo_to_create = self.initial_txs * 10
        else:
            utxo_amount = ft_amount * 1e8 / self.initial_txs
            tx_to_create = int(self.initial_txs / self.MAX_TRANSACTION_OUTPUT)
            utxo_to_create = self.initial_txs

        for i in range(len(txs_creator_addresses)):

            # Get FT box id (we should have only 1 ZenBox right now)
            zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
            ft_box = self.find_boxes_of_address(zen_boxes, ft_addresses[i])
            assert_true(len(ft_box), 0)

            logging.info("Creating initial UTXOs...")
            created_utxos = 0
            # We have 1k boxes limit per transaction = 1 input + 998 outputs + change output
            for _ in range(tx_to_create):
                # Create SidechainCoreTransaction that spend the split the FT box into many boxes.
                outputs = [{"publicKey": txs_creator_addresses[i], "value": utxo_amount} for _ in
                           range(self.MAX_TRANSACTION_OUTPUT)]
                outputs.append(
                    {"publicKey": ft_addresses[i],
                     "value": ft_box[0]["value"] - utxo_amount * self.MAX_TRANSACTION_OUTPUT})
                raw_tx = http_create_core_transaction(txs_creators[i], [{"boxId": ft_box[0]["id"]}], outputs)
                res = http_send_transaction(txs_creators[i], raw_tx)
                assert_true("transactionId" in res)
                logging.info("Sent " + res["transactionId"] + " with UTXO: " + str(self.MAX_TRANSACTION_OUTPUT))

                self.sc_sync_all()
                generate_next_blocks(forger_nodes[0], "first node", 1)[0]
                self.sc_sync_all()

                created_utxos += self.MAX_TRANSACTION_OUTPUT

                zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
                ft_box = self.find_boxes_of_address(zen_boxes, ft_addresses[i])
                assert_true(len(ft_box), 0)

            remaining_utxos = utxo_to_create - created_utxos
            if remaining_utxos > 0:
                raw_tx = http_create_core_transaction(txs_creators[i], [{"boxId": ft_box[0]["id"]}],
                                                      [{"publicKey": txs_creator_addresses[i], "value": utxo_amount} for
                                                       _ in range(remaining_utxos)])
                res = http_send_transaction(txs_creators[i], raw_tx)
                assert_true("transactionId" in res)
                logging.info("Sent " + res["transactionId"] + "with UTXO: " + str(remaining_utxos))

                self.sc_sync_all()
                generate_next_blocks(forger_nodes[0], "first node", 1)[0]
                self.sc_sync_all()

        # Check that every tx_creator node received the correct amount of UTXOs
        for i in range(len(txs_creators)):
            zen_boxes = http_wallet_allBoxesOfType(txs_creators[i], "ZenBox")
            filtered_boxes = self.find_boxes_of_address(zen_boxes, txs_creator_addresses[i])
            assert_equal(len(filtered_boxes), utxo_to_create)
        if self.test_type == TestType.Mempool or self.test_type == TestType.Mempool_Timed:
            # Populate the mempool
            logging.info("Populating the mempool...")
            self.populate_mempool(utxo_amount, txs_creators, self.extended_transaction, self.big_transaction,
                                  self.send_coins_to_address)
            # Give mempool time to update
            force_wait_for_mempool = 60
            logging.info(f"... SLEEPING FOR {force_wait_for_mempool} SECONDS UNTIL MEMPOOL READY ...")
            sleep(force_wait_for_mempool)
            logging.info("!! Mempool ready !!")

            # Verify that all the nodes have the correct amount of transactions in the mempool
            for node in self.sc_nodes:
                assert_equal(len(allTransactions(node, True)["transactions"]), self.initial_txs * len(txs_creators))

        # Verify that every forgers node received the FT and create forging stakes.
        for i in range(1, len(forger_nodes)):
            # Multiply ft_amount by 1e8 to get zentoshi value
            assert_equal(http_wallet_balance(forger_nodes[i]), self.FORGING_STAKE_AMOUNT * 1e8)
            makeForgerStake(forger_nodes[i], forger_addresses[i - 1], forger_addresses[i - 1], forger_vrf_pks[i - 1],
                            self.FORGING_STAKE_AMOUNT * 1e8)

        self.sc_sync_all()
        generate_next_blocks(forger_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        # Verify that every forgers node created the correct forging stakes.
        for node in forger_nodes:
            boxes = http_wallet_allBoxesOfType(node, "ForgerBox")
            assert_equal(len(boxes), 1)
            assert_equal(boxes[0]["value"], self.FORGING_STAKE_AMOUNT * 1e8)

        # Advance of 2 consensus epochs to maturate the new forging stakes
        generate_next_block(forger_nodes[0], "first node", force_switch_to_next_epoch=True)[0]
        self.sc_sync_all()
        generate_next_block(forger_nodes[0], "first node", force_switch_to_next_epoch=True)[0]
        self.sc_sync_all()

        # Take best block id of every node and assert they all match
        test_start_block_ids, _ = self.get_best_node_block_ids()
        assert_equal(len(set(test_start_block_ids.values())), 1, "Start BlockId's are not equal")

        # Output the wallet balance of each node
        logging.info("Node Wallet Balances Before Test...")
        initial_balances = self.log_node_wallet_balances()
        self.csv_data["initial_balances"] = initial_balances

        ##########################################################
        ##################### TEST VERSION 1 #####################
        ##########################################################

        if self.test_type == TestType.Mempool:
            ######## RUN UNTIL NODE CREATORS MEMPOOLS ARE EMPTY ########
            # Wait until mempool empty - this should also mean that other nodes mempools are empty (differences will be performance issues)
            start_time = self.start_forge()
            end_test = False
            while not end_test:
                end_test = True
                for creator_node in txs_creators:
                    remaining_txs = None
                    # Don't poll if we haven't mined a block yet, reduce calls to allTransactions
                    if time.time() - start_time > self.block_rate:
                        try:
                            request_start_time = time.time()
                            remaining_txs = len(allTransactions(creator_node, False)["transactionIds"])
                            request_end_time = time.time()
                            logging.info(
                                f"Creator Node{txs_creators.index(creator_node)} allTransactions response time: "
                                f"{request_end_time - request_start_time}")
                        except Exception:
                            logging.warning(
                                f"Creator Node{txs_creators.index(creator_node)} - Unable to retrieve mempool "
                                f"transactions")
                    if remaining_txs != 0:
                        end_test = False
                        break

                get_cpu_ram_usage()
                # TODO: Optimise sleep to ensure it isn't effecting end time value
                # TODO: Sleep until at least 2 blocks have been mined, first call is expensive when mempool filled
                # Sleep for length of block rate, avoid polling allTransactions too often
                sleep(self.block_rate)

        elif self.test_type == TestType.Mempool_Timed:
            ######## RUN UNTIL TIMER END ########
            start_time = self.start_forge()
            while time.time() - start_time < self.test_run_time:
                sleep(1)

        ##########################################################
        ##################### TEST VERSION 2 #####################
        ##########################################################

        elif self.test_type == TestType.Transactions_Per_Second:
            # 1 thread per txs_creator node sending transactions
            start_time = self.txs_creator_send_transactions_per_second_to_addresses(utxo_amount, txs_creators, True,
                                                                                    self.send_coins_to_address)

        elif self.test_type == TestType.All_Transactions:
            # 1 thread per txs_creator node sending transactions
            start_time = self.txs_creator_send_transactions_per_second_to_addresses(utxo_amount, txs_creators, False,
                                                                                    self.send_coins_to_address)

        # stop forging
        for index, node in enumerate(forger_nodes):
            logging.info(f"Stopping Forger Node{index}")
            request_start_time = time.time()
            http_stop_forging(node)
            request_end_time = time.time()
            logging.debug(f"Stop Forging took: {request_end_time - request_start_time}s")

        # Get end time
        end_time = time.time()
        # Give nodes time to recover
        sleep(30)

        # Take blockhash of every node and verify they are all the same
        test_end_block_ids, api_errors = self.get_best_node_block_ids()
        if len(set(test_end_block_ids.values())) != 1:
            try:
                for forger in forger_nodes:
                    generate_next_blocks(forger, "first node", 1)[0]
                self.sc_sync_all()
            except Exception as e:
                print(e)
        # TODO: Find balance for the node sender and receiver and verify that it's what we expect
        # sum(balance of each node) => total ZEN present at the end of the test
        # Output the wallet balance of each node
        logging.info("Node Wallet Balances After Test...")
        end_balances, wallet_balance_api_errors = self.log_node_wallet_balances()
        self.csv_data["end_balances"] = end_balances
        api_errors += wallet_balance_api_errors

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
            logging.info("### Node" + str(node_index) + " Test Results ###")

            # Retrieve node mempool
            mempool_txs = len(allTransactions(node, False)["transactionIds"])
            logging.info("Node" + str(node_index) + " Mempool remaining transactions: " + str(mempool_txs))
            mempool_transactions.append(mempool_txs)

            while current_block_id != start_block_id:
                transactions_mined = self.get_node_mined_transactions_by_block_id(node, current_block_id)
                number_of_transactions_mined = len(transactions_mined)
                total_mined_transactions += len(transactions_mined)
                logging.info("Node" + str(node_index) + "- BlockId " + str(current_block_id) + " Mined Transactions: " +
                             str(number_of_transactions_mined))
                total_blocks_for_node += 1
                current_block = http_block_findById(node, current_block_id)["block"]
                current_block_id = current_block["parentId"]
                if (iteration == 0):
                    blocks_ts.append(current_block["timestamp"])
                    self.csv_data["mined_txs"].append(number_of_transactions_mined)
                    self.csv_data["block_tps"].append(number_of_transactions_mined / self.block_rate)
                    io_number = 10 if self.big_transaction else 1
                    for tx in transactions_mined:
                        assert_equal(len(tx["unlockers"]), io_number)
                        assert_equal(len(tx["newBoxes"]), io_number)
            blocks_per_node.append(total_blocks_for_node)
            iteration += 1
            logging.info(
                "Node" + str(node_index) + " Total Blocks (Including start block): " + str(total_blocks_for_node))
            logging.info("Node" + str(node_index) + " Total Transactions Mined: " + str(total_mined_transactions))
        not_mined_transactions = self.initial_txs * len(txs_creators) - total_mined_transactions
        logging.info(f"Transactions NOT mined: {not_mined_transactions}")
        test_run_time = end_time - start_time
        logging.info(f"\n###\nTEST RUN TIME: {test_run_time} seconds\n###")
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
        self.csv_data["blocks_mined"] = len(blocks_ts)

        # Get latency configuration values
        latency_configurations = self.get_latency_config()
        for config in latency_configurations:
            self.csv_data["transaction_latency"].append(config.transaction)
            self.csv_data["block_latency"].append(config.block)
            self.csv_data["modifiers_spec_latency"].append(config.modifiers_spec)

        self.scan_logs_for_forks()
        self.fill_csv()


if __name__ == "__main__":
    PerformanceTest().main()
