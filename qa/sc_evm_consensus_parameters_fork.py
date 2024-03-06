#!/usr/bin/env python3
import time

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SC_CREATION_VERSION_2
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, bootstrap_sidechain_nodes, \
    AccountModel, \
    disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks, EVM_APP_BINARY
from test_framework.util import assert_equal, websocket_port_by_mc_node_index

"""
This test doesn't support --allforks.

Configuration:
    Start 1 MC node and 2 SC node.
    SC node 1 connected to the MC node 1.
    SC node 2 connected to the MC node 1 and SC node 1.

    ConsensusParameterFork:
        - Epoch: 0,  ConsensusSlotsInEpoch: 720
        - Epoch: 20, ConsensusSlotsInEpoch: 1000
        - Epoch: 30, ConsensusSlotsInEpoch: 1500

Test:
    - Perform a FT.
    - Verify that the forging info are coherent with the default consensus params fork
    - Advance of 17 epochs (we were on epoch 2)
    - Disconnect SC node 2
    - Verify that now the consensus params are changed (we reached the first consensus params fork)
    - Forge an entire new epoch and verify that we reached the first slot of the next epoch
    - Reconnect SC node 2 and verify that it is able to sync
"""



class SCConsensusParamsForkTest(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=10, circuittype_override=KEY_ROTATION_CIRCUIT, forward_amount=100)


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

    def sc_setup_nodes(self):
        return self.sc_setup_nodes_with_extra_arg(
            '-max_hist_rew_len', str(10000), EVM_APP_BINARY, self.API_KEY)

    def run_test(self):
        if self.options.all_forks:
            logging.info("This test cannot be executed with --allforks")
            exit()

        time.sleep(0.1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        ft_amount_in_zen = 10
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 2)
        assert_equal(forging_info["consensusSlotsInEpoch"], 720)

        # Reach the last slot before the activation of the ConsensusParameterFork
        for _ in range(17):
            generate_next_block(sc_node, "first", force_switch_to_next_epoch=True)

        # Disconnect SC node 1 and SC node 2
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 19)
        assert_equal(forging_info["consensusSlotsInEpoch"], 720)

        # Verify that we have the consensusSlotsInEpoch updated
        generate_next_block(sc_node, "first", force_switch_to_next_epoch=True)
        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 20)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000) 
        assert_equal(forging_info["bestBlockSlotNumber"], 1)

        # Verify that we are able to forge an entire epoch using the new value of consensusslotsInEpoch
        generate_next_blocks(sc_node, "first node", 1000)
        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 21)
        assert_equal(forging_info["bestBlockSlotNumber"], 1)

        # Reach the epoch in which the new ConsensusParamterFork is activated
        for _ in range (9):
           generate_next_block(sc_node, "first", force_switch_to_next_epoch=True)

        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 30)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1500)       
        assert_equal(forging_info["bestBlockSlotNumber"], 1)

        # Verify that we are able to forge an entire epoch using the new value of consensusslotsInEpoch
        generate_next_blocks(sc_node, "first node", 1499)
        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 30)
        assert_equal(forging_info["bestBlockSlotNumber"], 1500)

        # Reconnect the SC node 1 and the SC node 2 and verify that SC node 2 is able to sync blocks
        connect_sc_nodes(self.sc_nodes[0], 1)
        generate_next_block(sc_node, "second node")
        sync_sc_blocks(self.sc_nodes, wait_for=300)
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)


if __name__ == "__main__":
    SCConsensusParamsForkTest().main()
