#!/usr/bin/env python3
import logging
import math
import time
import multiprocessing
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
from httpCalls.transaction.sendCoinsToAddressAccount import sendCoinsToAddressAccount
from httpCalls.transaction.createCoreTransactionAccount import http_create_core_transaction_account
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, assert_equal
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, start_sc_nodes_with_multiprocessing, generate_next_blocks, \
    deserialize_perf_test_json, connect_sc_nodes, convertZenniesToWei, convertZenToZennies, AccountModelBlockVersion, EVM_APP_BINARY
from performance.perf_data import NetworkTopology, TestType
from SidechainTestFramework.account.address_util import format_evm

# Declare global thread safe values used for multiprocessing tps test
counter = Value('i', 0)
errors = Value('i', 0)

def get_latency_config(perf_data):
    return LatencyConfig(
        perf_data["latency_settings"]["get_peer_spec"],
        perf_data["latency_settings"]["peer_spec"],
        perf_data["latency_settings"]["transaction"],
        perf_data["latency_settings"]["block"],
        perf_data["latency_settings"]["request_modifier_spec"],
        perf_data["latency_settings"]["modifiers_spec"]
    )

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

# Initialise the threadsafe globals
def init_globals(count, err):
    global counter
    global errors
    counter = count
    errors = err

def get_number_of_transactions_for_node(node):
    return len(allTransactions(node, False)["transactionIds"])


# TODO create the transaction with the createCoreTransaction endpoint and then broadcast them with the sendRawTransaction
def send_transactions_per_second(txs_creator_node, destination_address, tx_amount, start_time, test_run_time,
                                 transactions_per_second, extended_transaction=False):
    # Run until
    nonce = 0
    while time.time() - start_time < test_run_time:
        i = 0
        tps_start_time = time.time()
        # Send transactions until the transactions_per_second value has been reached
        while i < transactions_per_second:
            try:
                sendCoinsToAddressAccount(txs_creator_node, destination_address, tx_amount, nonce)
            except Exception:
                with errors.get_lock():
                    errors.value += 1
            with counter.get_lock():
                counter.value += 1
            i += 1
            nonce += 1
        completion_time = time.time() - tps_start_time
        # Remove execution time from the 1 second to get as close to X number of TPS as possible
        if completion_time < 1:
            sleep(1 - completion_time)
        # If completion time exceeds 1 second, it's no longer x transactions per second.
        # Lower the max_tps_per_process config value until this value is under 1 second.
        elif completion_time > 1:
            print("WARNING:  Number of transactions sent has exceeded 1 second - decrease max_tps_per_process value.")

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
    extended_transaction = perf_data["extended_transaction"]
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
                "tps_total": 0, "tps_mined": 0, "blocks_ts": [], "node_api_errors": 0, "extended_transaction": extended_transaction
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

    def get_node_configuration(self, mc_node, sc_node_data, perf_data):
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
                    block_rate=self.block_rate,
                    latency_settings=self.latency_settings
                )
            )
        return node_configuration

    def setup_nodes(self):
        # Start 1 MC node
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_nodes = self.get_node_configuration(mc_node, self.sc_node_data, self.perf_data)

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, 100),
            *sc_nodes
        )

        # self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * self.block_rate / 2)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, block_timestamp_rewind=720*self.block_rate/2, blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        if self.perf_data["use_multiprocessing"]:
            return start_sc_nodes_with_multiprocessing(len(self.sc_node_data), dirname=self.options.tmpdir, binary=[EVM_APP_BINARY]*len(self.sc_node_data))
        else:
            return start_sc_nodes(len(self.sc_node_data), dirname=self.options.tmpdir, binary=[EVM_APP_BINARY]*len(self.sc_node_data))
        #return start_sc_nodes(len(self.sc_node_data), dirname=self.options.tmpdir, binary=[EVM_APP_BINARY]*len(self.sc_node_data))

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
            connect_sc_nodes(self.sc_nodes[node], 0)
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

    def populate_mempool(self, tx_amount, txs_creator_nodes, non_creator_nodes):

        for j in range(len(txs_creator_nodes)):
            for i in range(self.initial_txs):
                destination_address = non_creator_nodes[0].wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
                # Populate the mempool - so don't mine a block
                sendCoinsToAddressAccount(txs_creator_nodes[j], destination_address, tx_amount, i)
                if (i % 1000 == 0):
                    print("Node " + str(j) + " sent txs: " + str(i))
            print('transaction number: ')
            print(len(allTransactions(txs_creator_nodes[j], False)["transactionIds"]))
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

    def node_wallet_balances_account(self, node):
        addresses_list = node.wallet_allPublicKeys()['result']['propositions']
        wallet_balance = 0
        for addresses in addresses_list:
            if 'address' in addresses:
                wallet_balance += http_wallet_balance(node, addresses['address'])
        return wallet_balance

    def log_node_wallet_balances_account(self):
        for index, node in enumerate(self.sc_nodes):
            addresses_list = node.wallet_allPublicKeys()['result']['propositions']
            wallet_balance = 0
            for addresses in addresses_list:
                if 'address' in addresses:
                    wallet_balance += http_wallet_balance(node, addresses['address'])
            if (self.sc_nodes_list[index]["tx_creator"]):
                print(f"Node{index} (Transaction Creator) Wallet Balance: {wallet_balance}")
            else:
                print(f"Node{index} Wallet Balance: {wallet_balance}")


    def get_node_mined_transactions_by_block_id(self, node, block_id):
        result = http_block_findById(node, block_id)
        return result["block"]["sidechainTransactions"]

    def create_transactions(self, node_index, txs_creator_node, destination_address, tx_amount,
                            transactions):
        print('here in create transactions')
        txs = []
        # FIXME use a variable parameter changing this method signature, we have to use the input intial_txs
        transactions_counter = 500
        nonce = 0
        # print('result ', http_create_core_transaction_account(txs_creator_node, destination_address, tx_amount, nonce))
        while nonce < transactions_counter:
            #sendCoinsToAddressAccount(txs_creator_node, destination_address, tx_amount, nonce)
            txs.append(http_create_core_transaction_account(txs_creator_node, destination_address, tx_amount, nonce, None))
            nonce += 1
        transactions.insert(node_index, txs)

    def create_raw_transactions(self, txs_creator_nodes, destination_address, tx_amount):
        # Pre-compute all the transactions bytes that we want to send
        manager = multiprocessing.Manager()
        transactions = manager.list()
        with Pool() as pool:
            args = []
            # Add send_transactions_per_second arguments to args for each txs_creator_node
            # starmap runs them all in parallel
            index = 0
            logging.info("Creating raw transactions...")
            for creator_node in txs_creator_nodes:

                args.append((index, creator_node, destination_address, tx_amount, transactions))
                index += 1

            pool.starmap(self.create_transactions, args)

        return transactions


    def txs_creator_send_transactions_per_second_to_addresses(self, tx_amount, txs_creator_nodes, non_creator_nodes,
                                                              tps_test):

        destination_address = non_creator_nodes[0].wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # TODO create raw transaction here
        # Pre-compute all the transactions bytes that we want to send
        transactions = self.create_raw_transactions(txs_creator_nodes, tx_amount, destination_address)

        # FIXME don't call the api directly but define a function that given the transaction creator nodes return the list of signed raw transactions
        #transactionAddresses = createCoreTransactionAccount(txs_creator_node, destination_address, tx_amount, nonce)

        # TODO this has to be moved after this point
        start_time = time.time()

        if tps_test:
            # Each node needs to be able to send a number of transactions per second, without going over 1 second, or
            # timing out - May need some fine-tuning.
            transactions_per_second = math.floor(self.initial_txs / self.test_run_time)
            for i in range(len(txs_creator_nodes)):
                print(
                    f"Running Throughput: {transactions_per_second} Transactions Per Second for Creator "
                    f"Node(Node{i})...")

            while counter.value < self.initial_txs * len(txs_creator_nodes) and (
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
                            args.append((self.sc_nodes[index], destination_address, tx_amount, start_time,
                                         self.test_run_time, transactions_per_second))
                    pool.starmap(send_transactions_per_second, args)

                print(f"... Sent {counter.value} Transactions ...")
                print(f"Timer: {time.time() - start_time} and counter {counter.value}")

        # We're not interested in transactions per second, just fire all transactions as fast as possible
        else:
            for _ in range(self.initial_txs):
                with Pool(initializer=init_globals, initargs=(counter, errors)) as pool:
                    i = 0
                    args = [(txs_creator_node, destination_address, tx_amount, i)]
                    while counter.value < self.initial_txs and ((time.time() - start_time) < self.test_run_time):
                        try:
                            pool.starmap(sendCoinsToAddressAccount, args)
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
            print(txs_creators[i].wallet_createPrivateKeySecp256k1())
            ft_addresses.append(txs_creators[i].wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"])
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id, mc_nodes[0],
                                          ft_addresses[i], ft_amount, mc_return_address)
            txs_creator_addresses.append(
                txs_creators[i].wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"])
        self.sc_sync_all()

        # Generate 1 SC block to include FTs.
        forger_nodes = self.find_forger_nodes()
        generate_next_blocks(forger_nodes[0], "first node", 1)[0]
        self.sc_sync_all()

        # Verify that every tx_creator node received the FT
        for node in txs_creators:
            # Multiply ft_amount by 1e8 to get zentoshi value
            ft_amount_in_zennies = convertZenToZennies(ft_amount)
            ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)
            assert_equal(self.node_wallet_balances_account(node), ft_amount_in_wei)

        # --------------------------------------------------------------------------------------------------------------
        # --------------------------------------------------------------------------------------------------------------
        # tx_amount = ft_amount_in_zennies / self.initial_txs
        tx_amount = 10
        print('tx_amount: ', tx_amount)
        print(self.test_type)
        if self.test_type == TestType.Mempool or self.test_type == TestType.Mempool_Timed:
            # Populate the mempool
            print("Populating the mempool...")
            self.populate_mempool(tx_amount, txs_creators, non_txs_creators)
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
        self.log_node_wallet_balances_account()

        # Start forging on nodes where forger == true
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Forger found - Node{index} - Start Forging...")
                http_start_forging(self.sc_nodes[index])

        # Start timing
        start_time = time.time()
        number_of_txs_creators = len(txs_creators)
        print(start_time)
        print(self.test_type)

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
                    remaining_txs = None
                    # Don't poll if we haven't mined a block yet, reduce calls to allTransactions
                    if time.time() - start_time > self.block_rate:
                        try:
                            request_start_time = time.time()
                            remaining_txs = len(allTransactions(creator_node, False)["transactionIds"])
                            print('remaing transactions: ', remaining_txs)
                            request_end_time = time.time()
                            logging.info(f"Creator Node{txs_creators.index(creator_node)} allTransactions response time: "
                                         f"{request_end_time - request_start_time}")
                        except Exception:
                            logging.warning(
                                f"Creator Node{txs_creators.index(creator_node)} - Unable to retrieve mempool "
                                f"transactions")
                    if remaining_txs != 0:
                        end_test = False
                        break
                sleep(self.block_rate)


        elif self.test_type == TestType.Mempool_Timed:
            ######## RUN UNTIL TIMER END ########
            while time.time() - start_time < self.test_run_time:
                sleep(1)

        ##########################################################
        ##################### TEST VERSION 2 #####################
        ##########################################################

        elif self.test_type == TestType.Transactions_Per_Second:
            # 1 thread per txs_creator node sending transactions
            self.txs_creator_send_transactions_per_second_to_addresses(tx_amount, txs_creators, non_txs_creators, True)

        elif self.test_type == TestType.All_Transactions:
            # 1 thread per txs_creator node sending transactions
            self.txs_creator_send_transactions_per_second_to_addresses(tx_amount, txs_creators, non_txs_creators, False)

        # stop forging
        for index, node in enumerate(self.sc_nodes_list):
            if node["forger"]:
                print(f"Stopping Forger Node{index}")
                http_stop_forging(self.sc_nodes[index])

        # Get end time
        end_time = time.time()
        sleep(30)

        # Take blockhash of every node and verify they are all the same
        test_end_block_ids, api_errors = self.get_best_node_block_ids()
        assert_equal(len(set(test_end_block_ids.values())), 1)

        # TODO: Find balance for the node sender and receiver and verify that it's what we expect
        # sum(balance of each node) => total ZEN present at the end of the test
        # Output the wallet balance of each node
        print("Node Wallet Balances After Test...")
        end_balances = self.log_node_wallet_balances_account()
        self.csv_data["end_balances"] = end_balances

        # Get information from all nodes
        for i in range(len(self.sc_nodes)):
            # Retrieve node mempool
            mempool_transactions = allTransactions(self.sc_nodes[i], False)["transactionIds"]
            number_of_transactions = len(mempool_transactions)
            print(f"Node {i} mempool transactions remaining: {number_of_transactions}")
            # Print mempool of each node if we're running timed test
            if self.test_type == TestType.Mempool_Timed:
                if number_of_transactions > 0:
                    print(f"Node {i} mempool transactions: {mempool_transactions}")

        # Take blockhash of every node and verify they are all the same
        test_end_block_ids, _ = self.get_best_node_block_ids()
        assert_equal(len(set(test_end_block_ids.values())), 1)

        # TODO: Find balance for the node sender and receiver and verify that it's what we expect
        # sum(balance of each node) => total ZEN present at the end of the test
        # Output the wallet balance of each node
        print("Node Wallet Balances After Test...")
        self.log_node_wallet_balances_account()

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
