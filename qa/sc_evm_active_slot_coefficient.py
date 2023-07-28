#!/usr/bin/env python3
import time


from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, bootstrap_sidechain_nodes, AccountModel, \
    disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks, try_to_generate_block_in_slots
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SC_CREATION_VERSION_2
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from test_framework.util import assert_equal, assert_true, websocket_port_by_mc_node_index
from httpCalls.block.best import http_block_best
import pprint

"""
Configuration:
    Start 1 MC node and 2 SC node.
    SC node 1 connected to the MC node 1.

    ActiveSlotCoefficientFork:
        - Epoch: 35,  ActiveSlotCoefficient: 0.05, ConsensusSlotsInEpoch: 1500

Test:
    - Perform a FT.
    - Verify that the forging info are coherent with the default consensus params fork
"""



class SCActiveSlotCoefficientTest(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=1, withdrawalEpochLength=10, circuittype_override=KEY_ROTATION_CIRCUIT, forward_amount=100)


    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                cert_submitter_enabled=True,
                cert_signing_enabled=True,
                api_key='Horizen')
            ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, forward_amount=100,
                                                        withdrawal_epoch_length=10,
                                                        sc_creation_version=SC_CREATION_VERSION_2,
                                                        is_non_ceasing=True,
                                                        circuit_type=KEY_ROTATION_CIRCUIT),
                                            *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, block_timestamp_rewind = (720 * 120 * 5), model=AccountModel)


    def run_test(self):
        time.sleep(0.1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        ft_amount_in_zen = 10
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestEpochNumber"], 2)
        assert_equal(forging_info["consensusSlotsInEpoch"], 720)

        # Reach the last slot before the activation of the ConsensusParameterFork
        for _ in range(33):
            generate_next_block(sc_node, "first", force_switch_to_next_epoch=True)

        forging_info = sc_node.block_forgingInfo()["result"]
        assert_equal(forging_info["bestEpochNumber"], 35)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1500)
        current_best_slot_number = forging_info["bestSlotNumber"]
        slot_until_next_epoch = 1500 - current_best_slot_number

        #Try to generate a block for every slot of the epoch
        forged_block_ids = try_to_generate_block_in_slots(sc_node,slot_until_next_epoch)
        block_created_percentage = len(forged_block_ids) / slot_until_next_epoch * 100

        #Verify that the we have more or less 5% of slots filled
        assert_true(block_created_percentage > 4.0 and block_created_percentage < 6.0)
        forging_info = sc_node.block_forgingInfo()["result"]

        print(block_created_percentage)
        print(slot_until_next_epoch)
        pprint.pprint(forging_info)



if __name__ == "__main__":
    SCActiveSlotCoefficientTest().main()
