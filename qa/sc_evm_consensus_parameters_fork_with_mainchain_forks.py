#!/usr/bin/env python3
import logging
import time

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, generate_next_block, AccountModel, \
    try_to_generate_block_in_slots, disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks
from test_framework.util import initialize_chain_clean, websocket_port_by_mc_node_index, \
    connect_nodes_bi, disconnect_nodes_bi, forward_transfer_to_sidechain, assert_equal, assert_true, fail

"""
Check the correct behavior of the consensus parameter fork activation with a mainchain fork

This test doesn't support --allforks.

Configuration:
    Start 2 MC node and 2 SC node.
    SC node 1 connected to the MC node 1 and SC node 2
    SC node 2 connected to the SC node 1

    ConsensusParameterFork:
        - Epoch: 0,  ConsensusSlotsInEpoch: 720
        - Epoch: 20, ConsensusSlotsInEpoch: 1000
        - Epoch: 30, ConsensusSlotsInEpoch: 1500
        - Epoch: 35, ActiveSlotCoefficient: 0.05

Test:
    - Perform FTs to SC node 1
    - Create sidechain blocks till the epoch 19 (the one before the consensus parameter fork activation) and check the forging info
    - Check the consensus parameters change
        - On sidechain, reach the epoch before consensus parameter change fork activation
        - Disconnect MC node 1 and MC node 2
        - Generate 1 mainchain block on MC node 1
        - Generate 1 block and SC node 1 in the activation fork epoch 20 and check that the consensus parameters are updated and corrected
        - Generate 2 mainchain blocks on MC node 2
        - Reconnect the mainchain nodes and check that MC node 1 will drop its last block in favor of the two created on MC node 2
        - Create 1 sidechain block and check that we are at the same height of the block reverted given the mainchain fork and that the consensus parameters are correct 
    - Check the slot coefficient change
        - Disconnect SC node 1 and SC node 2
        - On sidechain, reach the epoch before slot coefficient change fork activation
        - Disconnect MC node 1 and MC node 2
        - Generate 1 mainchain block on MC node 1
        - Generate 1 block and SC node 1 in the activation fork epoch 35
        - Generate 2 mainchain blocks on MC node 2
        - Reconnect the mainchain nodes and check that MC node 1 will drop its last block in favor of the two created on MC node 2
        - On SC node 1 create blocks till the end of the epoch 35 and check that the real slot coefficient is comparable with the one introduced with the fork
        - Connect SC node and SC node 2 and check the block synchronization
"""

class SCConsensusParamsForkWithMainchainForksTest(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_mc_nodes=2, number_of_sidechain_nodes=2)

    def setup_network(self, split=False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def sc_setup_chain(self):


        mc_node = self.nodes[0]
        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                cert_submitter_enabled=True,
                cert_signing_enabled=True,
                api_key='Horizen'),

            SCNodeConfiguration(
                MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled,
                allow_unprotected_txs=True)

        ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, forward_amount=100,
                                                        withdrawal_epoch_length=10,
                                                        sc_creation_version=SC_CREATION_VERSION_2,
                                                        is_non_ceasing=True,
                                                        circuit_type=KEY_ROTATION_CIRCUIT),
                                         *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, block_timestamp_rewind = (720 * 120 * 5), model=AccountModel)

    def run_test(self):
        if self.options.all_forks:
            logging.info("This test cannot be executed with --allforks")
            exit()

        mc_node1 = self.nodes[0]
        mc_node2 = self.nodes[1]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Synchronize mc_node1 and mc_node2
        self.sync_all()

        # Do FT to SC node 1 and generate 1 MC block on the first MC node
        mc_address_1 = mc_node1.getnewaddress()
        sc_address_1 = sc_node1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        ft_amount = 10
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      sc_address_1,
                                      ft_amount,
                                      mc_address_1,
                                      generate_block=False)
        time.sleep(2)
        mc_address_2 = mc_node2.getnewaddress()
        mc_node1.sendtoaddress(mc_address_2, 2)
        self.sync_all()
        mc_node1.generate(1)
        generate_next_block(sc_node1, "first node")

        # verify FT inclusion
        initial_balance = http_wallet_balance(sc_node1, sc_address_1)
        assert_equal(convertZenToWei(ft_amount), initial_balance)

        # --------------------------------------------------------------------------------------------------------------
        # Consensus parameters change test
        # --------------------------------------------------------------------------------------------------------------
        # Generate sidechain blocks till the epoch before the consensus parameter fork activation
        for _ in range(17):
            generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 19)
        assert_equal(forging_info["consensusSlotsInEpoch"], 720)
        assert_equal(forging_info["bestBlockEpochNumber"], 19)

        # --------------------------------------------------------------------------------------------------------------
        # Synchronize mc_node1 and mc_node2, then disconnect them
        self.sync_all()
        disconnect_nodes_bi(self.nodes, 0, 1)

        # --------------------------------------------------------------------------------------------------------------
        # Generate 1 more MC block on MC node 1
        mc_node1.generate(1)[0]
        # Check forging info
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["consensusSecondsInSlot"], 12)
        assert_equal(forging_info["consensusSlotsInEpoch"], 720)
        assert_equal(forging_info["bestBlockEpochNumber"], 19)

        # --------------------------------------------------------------------------------------------------------------
        # Generate a new block on the sidechain switching to the next epoch where there will be the fork activation
        # che the forging info
        generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        sc_block_fork_height = sc_node1.rpc_eth_getBlockByNumber("latest", "true")["result"]["number"]
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["consensusSecondsInSlot"], 18)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)
        assert_equal(forging_info["bestBlockEpochNumber"], 20)

        # --------------------------------------------------------------------------------------------------------------
        # Generate another 2 MC blocks on the second MC node
        mc_node2.generate(1)[0]
        fork_mcblock_hash2 = mc_node2.generate(1)[0]
        time.sleep(1)

        # --------------------------------------------------------------------------------------------------------------
        # Connect and synchronize MC node 1 to MC node 2
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()
        time.sleep(5)
        # MC Node 1 should replace mcblock_hash1 Tip with [fork_mcblock_hash1, fork_mcblock_hash2]
        assert_equal(fork_mcblock_hash2, mc_node1.getbestblockhash())

        # --------------------------------------------------------------------------------------------------------------
        # Generate a new block on the sidechain, check that we are at the same height of the block previously created
        # and that was reverted, also check that the consensus parameters are correct
        generate_next_block(sc_node1, "first")
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["consensusSecondsInSlot"], 18)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)
        assert_equal(forging_info["bestBlockEpochNumber"], 20)
        assert_equal(forging_info["bestBlockSlotNumber"], 2)         # the slot number of the block that is reverted was 1
        sc_node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")["result"]["number"]
        assert_equal(sc_node1_best_block, sc_block_fork_height)

        # --------------------------------------------------------------------------------------------------------------
        # Active slot coefficient test
        # --------------------------------------------------------------------------------------------------------------
        # Disconnect the SC node 2, we will connect them at the end of the test to check if the synchronization is possible
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)

        # Reach the last slot before the activation of the custom active slot coefficient
        for _ in range(14):
            generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)

        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 34)

        # --------------------------------------------------------------------------------------------------------------
        # Synchronize mc_node1 and mc_node2, then disconnect them
        self.sync_all()
        disconnect_nodes_bi(self.nodes, 0, 1)

        # --------------------------------------------------------------------------------------------------------------
        # Generate 1 more MC block on MC node 1
        mc_node1.generate(1)[0]

        # --------------------------------------------------------------------------------------------------------------
        # Generate a new block on the sidechain switching to the next epoch where there will be the fork activation
        # che the forging info
        generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        sc_block_fork_height = sc_node1.rpc_eth_getBlockByNumber("latest", "true")["result"]["number"]
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["consensusSecondsInSlot"], 5)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1500)
        assert_equal(forging_info["bestBlockEpochNumber"], 35)

        # --------------------------------------------------------------------------------------------------------------
        # Generate another 2 MC blocks on the second MC node
        mc_node2.generate(1)[0]
        fork_mcblock_hash2 = mc_node2.generate(1)[0]
        time.sleep(1)

        # --------------------------------------------------------------------------------------------------------------
        # Connect and synchronize MC node 1 to MC node 2
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()
        time.sleep(5)
        # MC Node 1 should replace mcblock_hash1 Tip with [fork_mcblock_hash1, fork_mcblock_hash2]
        assert_equal(fork_mcblock_hash2, mc_node1.getbestblockhash())

        # --------------------------------------------------------------------------------------------------------------
        # Generate a new block on the sidechain, check that we are at the same height of the block previously created
        # and that was reverted, also check that the consensus parameters are correct
        # There is no reason to check the bestBlockSlotNumber because with a slot coefficient of 5% we can't guess its value beforehand
        generate_next_block(sc_node1, "first")
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["consensusSecondsInSlot"], 5)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1500)
        assert_equal(forging_info["bestBlockEpochNumber"], 35)
        sc_node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")["result"]["number"]
        assert_equal(sc_node1_best_block, sc_block_fork_height)

        # --------------------------------------------------------------------------------------------------------------
        current_best_slot_number = forging_info["bestBlockSlotNumber"]
        slot_until_next_epoch = 1500 - current_best_slot_number

        #Try to generate a block for every slot of the epoch
        forged_block_ids = try_to_generate_block_in_slots(sc_node1,slot_until_next_epoch)
        block_created_percentage = len(forged_block_ids) / slot_until_next_epoch * 100

        #Verify that we have more or less 5% of slots filled
        assert_true(4.0 < block_created_percentage < 6.0)

        # --------------------------------------------------------------------------------------------------------------
        # Connect the 2 sidechain nodes and check that the SC node 2 is able to sync
        connect_sc_nodes(self.sc_nodes[0], 1)
        sync_sc_blocks(self.sc_nodes, wait_for=20)
        node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)


if __name__ == "__main__":
    SCConsensusParamsForkWithMainchainForksTest().main()
