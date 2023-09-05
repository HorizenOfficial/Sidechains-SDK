#!/usr/bin/env python3
import logging
import time
from datetime import datetime

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, stop_sc_node, start_sc_node, wait_for_sc_node_initialization, \
    generate_next_block, EVM_APP_BINARY, AccountModel, disconnect_sc_nodes_bi
from test_framework.util import assert_equal, assert_true, fail, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index
from httpCalls.block.forgingInfo import http_block_forging_info


"""
Test that the eth_syncing rpc method answer in a correct way if the node is currently syncing a batch of new blocks
from another peer

Configuration:
    Start 1 MC nodes and 2 SC nodes.

Test:
    - Test that node can detect `isSync` after being restarted when the ActiveSlotCoefficientFork has been activated.
"""

WITHDRAWAL_EPOCH_LENGTH = 10

class EvmSyncStatus(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # start MC node
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1', '-scproofqueuesize=0']])

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False)

        sc_node_2_configuration = SCNodeConfiguration(MCConnectionInfo(), False)

        self.network = SCNetworkConfiguration(SCCreationInfo(mc_node, 600, WITHDRAWAL_EPOCH_LENGTH),
                                              sc_node_1_configuration, sc_node_2_configuration)
        bootstrap_sidechain_nodes(
            self.options,
            self.network,
            model=AccountModel,
            block_timestamp_rewind=(720 * 120 * 10)
        )

    def sc_setup_nodes(self):
        # start both SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir, binary=[EVM_APP_BINARY] * 2,
                              extra_args=self.debug_extra_args)
    def check_sync_status(self, starting_block_height, execute_stop=False ):
        logging.info("Syncing...")
        t_0 = datetime.now()
        self.sync_sc_blocks(starting_block_height, execute_stop)
        t_1 = datetime.now()
        u_sec = (t_1 - t_0).microseconds
        sec = (t_1 - t_0).seconds
        logging.info("SC node 1 synced in {}.{} secs".format(sec, u_sec))

        node_check_blocks_density_freq = 15
        logging.info("Wait 30 seconds for SC node 1 to stop consider itself syncing")
        time.sleep(node_check_blocks_density_freq * 2)

        is_sync = self.sc_nodes[1].rpc_eth_syncing()["result"]
        logging.info("Current SC node 1 LAST sync info: " + str(is_sync))
        if type(is_sync) is bool and bool(is_sync):
            fail("SC node 1 still consider itself syncing")

    def sync_sc_blocks(self, starting_block_height, execute_stop=False, wait_for=200):
        # wait for maximum wait_for seconds for everybody to have the same block count
        start = time.time()
        sync_status = False
        while True:
            # call eth_syncing rpc method, if the sync status result is true check if the block values are correct
            res = self.sc_nodes[1].rpc_eth_syncing()["result"]
            logging.info("Current SC node 1 sync info: " + str(res))
            logging.info("SC node 1 height: " + str(self.sc_nodes[1].block_best()["result"]["height"]))
            logging.info("SC node 1 connections " + str(self.sc_nodes[1].node_connectedPeers()["result"]["peers"]))
            logging.info("SC node 0 connections " + str(self.sc_nodes[0].node_connectedPeers()["result"]["peers"]))
            if isinstance(res, dict) and "currentBlock" in res:
                sync_status = True

                decimal_starting_block = int(res["startingBlock"], 16)
                decimal_current_block = int(res["currentBlock"], 16)
                decimal_highest_block = int(res["highestBlock"], 16)

                assert_equal(starting_block_height, decimal_starting_block, "unexpected value for startingBlock")

                assert_true(decimal_current_block > 0, "unexpected value for currentBlock")
                assert_true(decimal_current_block >= decimal_starting_block, "startingBlock > currentBlock")

                assert_true(decimal_highest_block > 0, "unexpected value for highestBlock")
                assert_true(decimal_highest_block - decimal_current_block > 0, "currentBlock >= highestBlock")
                assert_true(decimal_highest_block - decimal_starting_block > 0, "startingBlock >= highestBlock")
            elif sync_status:
                # Received status false after it was true for some period of time
                sync_status = False
                break

            if time.time() - start >= wait_for:
                fail("Syncing blocks timeout")

            counts = [int(x.block_best()["result"]["height"]) for x in self.sc_nodes]
            if counts == [counts[0]] * len(counts):
                break

            # Stop node 0 after 15 seconds in execute_stop was set
            if execute_stop and time.time() - start >= 15:
                logging.info("Disconnect sidechain nodes")
                disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
                logging.info("Stopping SC node 0")
                stop_sc_node(self.sc_nodes[0], 0)
                break

            time.sleep(1)

        if not sync_status:
            fail("SC node 1 was in sync but has not detected that it is in sync")

    def run_sc_node(self, sc_node_idx):
        logging.info("Starting SC node " + str(sc_node_idx))
        start_sc_node(sc_node_idx, self.options.tmpdir, binary=EVM_APP_BINARY)
        wait_for_sc_node_initialization(self.sc_nodes)
        time.sleep(3)

    def restart_sc_node(self, sc_node_idx):
        logging.info("Stopping SC node " + str(sc_node_idx))
        stop_sc_node(self.sc_nodes[sc_node_idx], sc_node_idx)
        time.sleep(2)

        self.run_sc_node(sc_node_idx)

    def run_test(self):
        active_slot_coeff_fork_activation_height = 35
        sc_node0 = self.sc_nodes[0]
        sc_node1 = self.sc_nodes[1]

        # Note: in case machine is too fast we need more blocks to be able to get sync
        num_blocks = 1500

        self.sync_all()

        for _ in range(34):
            generate_next_block(sc_node0, "first node", force_switch_to_next_epoch=True)

        consensus_epoch_data = http_block_forging_info(sc_node0)
        assert_equal(consensus_epoch_data["bestBlockEpochNumber"], active_slot_coeff_fork_activation_height)

        # -------------------------------------------------------------------------------------
        # Test 1
        # the test workflow is:
        # stop SC1 node
        # forge 1500 blocks on node SC0
        # restart SC1 and sync the recently created blocks on SC0

        logging.info("Stopping SC node 1")
        stop_sc_node(sc_node1, 1)

        logging.info("SC node 0 generates {} blocks...".format(num_blocks))
        generate_next_blocks(sc_node0, "node 0", num_blocks, verbose=False)

        # run the sidechain node 2 and sync it
        self.run_sc_node(1)
        sc_node_1_height = int(sc_node1.block_best()["result"]["height"])

        logging.info("Connecting SC nodes")
        time.sleep(5)
        connect_sc_nodes(self.sc_nodes[0], 1)

        self.check_sync_status(sc_node_1_height + 1)


if __name__ == "__main__":
    EvmSyncStatus().main()
