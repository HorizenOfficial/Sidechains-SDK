#!/usr/bin/env python3
import logging
import time
from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.utils import convertZenToZennies
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SC_CREATION_VERSION_2
from SidechainTestFramework.scutil import generate_next_block, bootstrap_sidechain_nodes, \
    AccountModel, disconnect_sc_nodes_bi, connect_sc_nodes, sync_sc_blocks, try_to_generate_block_in_slots
from test_framework.util import assert_equal, websocket_port_by_mc_node_index, forward_transfer_to_sidechain, \
    assert_true

"""
Configuration:
    Start 1 MC node and 2 SC node.
    SC node 1 connected to the MC node 1.
    SC node 2 connected to the MC node 1 and SC node 1.

    ConsensusParameterFork:
        - Epoch: 0,  ConsensusSlotsInEpoch: 720
        - Epoch: 20, ConsensusSlotsInEpoch: 1000
        - Epoch: 30, ConsensusSlotsInEpoch: 1500
        - Epoch: 35, ActiveSlotCoefficient: 0.05

Test:
    - Perform FTs to SC node 1 and SC node 2
    - Verify that the forging info are coherent with the default consensus params fork (720 slots per epoch)
    - Make SC node 2 a forger node
    - Check the consensus parameters change
        - Advance of 14 epochs (we were on epoch 5)
        - Disconnect SC node 2
        - Switch epoch and create 20 blocks on SC node 1 and verify that the forging info are coherent with the fork (1000 slots per epoch)
        - Switch epoch and create 1 block on SC node 2 and verify that the forging info are coherent with the fork (1000 slots per epoch)
        - Reconnect SC node 2 and verify that it is able to drop its blocks from fork in favor of the ones from SC node 1
        - Verify that the forging info are coherent in both nodes
        - Switch to new epoch and verify that the forging info are coherent in both nodes
    - Check the slot coefficient change
        - Advance of 13 epochs before the slot coefficient fork activation
        - Disconnect SC node 2
        - Switch epoch and create 5 blocks on SC node 1
        - Switch epoch and create 1 block on SC node 2
        - Reconnect SC node 2 and verify that it is able to drop its blocks from fork in favor of the ones from SC node 1
        - On SC node 1 create blocks till the end of the epoch 35 and check that the real slot coefficient is comparable with the one introduced with the fork
"""

class SCConsensusParamsForkWithSidechainForksTest(AccountChainSetup):
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


    def run_test(self):
        time.sleep(0.1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        ft_amount_in_zen = 10
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 2)
        assert_equal(forging_info["consensusSlotsInEpoch"], 720)

        # --------------------------------------------------------------------------------------------------------------
        # Send a FT to the SC node 2 and make it a forger node
        logging.info("Send a FT to the SC node 2")
        sc_node2_address = sc_node2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      sc_node2_address,
                                      11,
                                      self.mc_return_address)

        self.block_id = generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_node2_vrfPubKey = sc_node2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        sc_node2_blockSignPubKey = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]

        result = ac_makeForgerStake(sc_node2, sc_node2_address, sc_node2_blockSignPubKey, sc_node2_vrfPubKey,
                                    convertZenToZennies(10))
        self.sc_sync_all()

        generate_next_block(sc_node1, "first node")
        self.sc_sync_all()

        # Checking the receipt
        tx_id = result['result']['transactionId']
        receipt = sc_node2.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_id))
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Make forger stake with native smart contract as owner should create a failed tx")

        self.block_id = generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        self.block_id = generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # --------------------------------------------------------------------------------------------------------------
        # Consensus parameters change test
        # --------------------------------------------------------------------------------------------------------------
        # Reach the last slot before the activation of the ConsensusParameterFork
        for _ in range(14):
            generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)

        # --------------------------------------------------------------------------------------------------------------
        # Disconnect SC node 1 and SC node 2
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        # Generate 1 block to switch epoch and then 20 blocks on SC node 1
        self.block_id = generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        for _ in range(20):
            generate_next_block(sc_node1, "first")
        # Check that the consensus parameters change fork is applied
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 20)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)

        # Generate 1 block to switch epoch and then 5 blocks on SC node 2
        self.block_id = generate_next_block(sc_node2, "second", force_switch_to_next_epoch=True)
        for _ in range(1):
            generate_next_block(sc_node2, "second")
        # Check that the consensus parameters change fork is applied
        forging_info = sc_node2.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 20)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)

        # --------------------------------------------------------------------------------------------------------------
        # Reconnect the SC node 1 and the SC node 2 and verify that SC node 2 is able to drop its blocks and, given the
        # longest chain rule apply the SC node 1 blocks
        connect_sc_nodes(self.sc_nodes[0], 1)

        sync_sc_blocks(self.sc_nodes, wait_for=20)
        node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 20)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)
        forging_info = sc_node2.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 20)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)

        # --------------------------------------------------------------------------------------------------------------
        # Switch epochs and check that everything is correct
        self.block_id = generate_next_block(sc_node2, "second", force_switch_to_next_epoch=True)
        node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 21)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)
        forging_info = sc_node2.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 21)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1000)

        # --------------------------------------------------------------------------------------------------------------
        # Active slot coefficient test
        # --------------------------------------------------------------------------------------------------------------
        # Reach the last slot before the activation of the custom active slot coefficient
        for _ in range(13):
            generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)

        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 34)

        # --------------------------------------------------------------------------------------------------------------
        # Disconnect SC node 1 and SC node 2
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)
        node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)

        # Generate 1 block to switch epoch and then 20 blocks on SC node 1
        self.block_id = generate_next_block(sc_node1, "first", force_switch_to_next_epoch=True)
        for _ in range(5):
            generate_next_block(sc_node1, "first")
        # Check that the consensus parameters change fork is applied
        forging_info = sc_node1.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 35)

        # Generate 1 block to switch epoch and then 5 blocks on SC node 2
        self.block_id = generate_next_block(sc_node2, "second", force_switch_to_next_epoch=True)
        for _ in range(1):
            generate_next_block(sc_node2, "second")
        # Check that the consensus parameters change fork is applied
        forging_info = sc_node2.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 35)

        # --------------------------------------------------------------------------------------------------------------
        # Reconnect the SC node 1 and the SC node 2 and verify that SC node 2 is able to drop its blocks and, given the
        # longest chain rule apply the SC node 1 blocks
        connect_sc_nodes(self.sc_nodes[0], 1)

        sync_sc_blocks(self.sc_nodes, wait_for=20)
        node1_best_block = sc_node1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)
        forging_info = sc_node1.block_forgingInfo()["result"]

        assert_equal(forging_info["bestBlockEpochNumber"], 35)
        assert_equal(forging_info["consensusSecondsInSlot"], 5)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1500)
        forging_info = sc_node2.block_forgingInfo()["result"]
        assert_equal(forging_info["bestBlockEpochNumber"], 35)
        assert_equal(forging_info["consensusSecondsInSlot"], 5)
        assert_equal(forging_info["consensusSlotsInEpoch"], 1500)

        # --------------------------------------------------------------------------------------------------------------
        current_best_slot_number = forging_info["bestBlockSlotNumber"]
        slot_until_next_epoch = 1500 - current_best_slot_number

        #Try to generate a block for every slot of the epoch
        forged_block_ids = try_to_generate_block_in_slots(sc_node1,slot_until_next_epoch)
        block_created_percentage = len(forged_block_ids) / slot_until_next_epoch * 100

        #Verify that we have more or less 5% of slots filled
        assert_true(4.0 < block_created_percentage < 6.0)


if __name__ == "__main__":
    SCConsensusParamsForkWithSidechainForksTest().main()
