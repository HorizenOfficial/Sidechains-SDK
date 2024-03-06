#!/usr/bin/env python3
import time


from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, bootstrap_sidechain_nodes, \
    AccountModel, \
    try_to_generate_block_in_slots, disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks, start_sc_nodes, \
    EVM_APP_BINARY
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SC_CREATION_VERSION_2
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from test_framework.util import assert_equal, assert_true, websocket_port_by_mc_node_index
import pprint

"""
Configuration:
    Start 1 MC node and 2 SC node.
    SC node 1 connected to the MC node 1.

    ActiveSlotCoefficientFork:
        - Epoch: 35,  ActiveSlotCoefficient: 0.05, ConsensusSlotsInEpoch: 1500

    This test doesn't support --allforks.
Test:
    - Perform a FT.
    - Verify that the forging info are coherent with the default consensus params fork
    - Forge block to reach the consensus epoch of fork activation
    - Disconnect SC node 2
    - Try to forge in every slot of an epoch and verify that we filled 4-5% of the total slots
    - Reconnect SC node 2 and verify that it is able to sync
"""



class SCActiveSlotCoefficientTest(AccountChainSetup):
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
            '-max_hist_rew_len', str(2000), EVM_APP_BINARY, self.API_KEY)

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
        for _ in range(33):
            generate_next_block(sc_node, "first", force_switch_to_next_epoch=True)

        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 35)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1500)
        current_best_slot_number = forging_info["bestBlockSlotNumber"]

        # Disconnect SC node 1 and SC node 2
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        slot_until_next_epoch = 1500 - current_best_slot_number

        #Try to generate a block for every slot of the epoch
        forged_block_ids = try_to_generate_block_in_slots(sc_node,slot_until_next_epoch)
        block_created_percentage = len(forged_block_ids) / slot_until_next_epoch * 100

        #Verify that the we have more or less 5% of slots filled
        print("block_created_percentage={}".format(block_created_percentage))
        assert_true(block_created_percentage > 4.0 and block_created_percentage < 6.0)

        # Reconnect the SC node 1 and the SC node 2 and verify that SC node 2 is able to sync blocks
        connect_sc_nodes(self.sc_nodes[0], 1)
        generate_next_block(sc_node, "second node")
        sync_sc_blocks(self.sc_nodes, wait_for=300)
        node1_best_block = sc_node.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)



if __name__ == "__main__":
    SCActiveSlotCoefficientTest().main()
