#!/usr/bin/env python3
import logging
from typing import List, Any

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SCForgerConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import start_sc_nodes, generate_next_blocks, \
    bootstrap_sidechain_nodes, generate_secrets, generate_vrf_secrets, generate_next_block
from httpCalls.block.forgingInfo import http_block_forging_info
from httpCalls.transaction.openStake import createOpenStakeTransaction
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress, sendCointsToMultipleAddress, \
    sendCoinsToAddressDryRun
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import assert_true, assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain

"""
    Setup 1 SC Node with a closed list of forger.
    Reach consensus epoch 2.
    Prepare next transaction to be forcefully included into the next epoch block: 
     1. tx with amount less than dust threshold
     2. open stake tx
    Try forging a new block that enables the Fork1.
    Block with forced tx 1 should fail against the stake.
    Block with forced tx 2 should succeed.
"""


class SidechainForkOneForcedTransactionsTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1
    number_of_forgers = 2
    allowed_forger_propositions: List[Any] = generate_secrets("seed", number_of_forgers)
    allowed_forger_vrf_public_keys = generate_vrf_secrets("seed", number_of_forgers)

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start 1 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        allowedForgers = []
        for i in range(0, self.number_of_forgers):
            allowedForgers += [
                [self.allowed_forger_propositions[i].publicKey, self.allowed_forger_vrf_public_keys[i].publicKey]]
        forger_configuration = SCForgerConfiguration(True, allowedForgers)
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            forger_options=forger_configuration,
            initial_signing_private_keys=list(map(lambda forger: forger.secret, self.allowed_forger_propositions)),
            max_fee=10000000000000
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def find_box(self, boxes, proposition):
        for box in boxes:
            if (box["typeName"] == "ZenBox" and box["proposition"]["publicKey"] == proposition):
                return box
        return {}

    def run_test(self):
        self.sync_all()
        sc_node1 = self.sc_nodes[0]
        mc_node1 = self.nodes[0]
        forger_amount = 1000
        sc_fee = 0

        # Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)
        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      self.allowed_forger_propositions[0].publicKey,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()
        new_public_key = http_wallet_createPrivateKey25519(self.sc_nodes[0])

        # Create some ZenBoxes
        logging.info("Create some ZenBoxes")
        sendCointsToMultipleAddress(sc_node1,
                                    list(map(lambda forger: forger.publicKey, self.allowed_forger_propositions)),
                                    [1000] * len(self.allowed_forger_propositions), 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        sendCoinsToAddress(sc_node1, new_public_key, 1500, 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        allBoxes = http_wallet_allBoxes(sc_node1)
        new_public_key_box = self.find_box(allBoxes, new_public_key)
        assert_true(new_public_key_box != {})

        forging_info = http_block_forging_info(sc_node1)
        assert_equal(2, forging_info["bestEpochNumber"])

        # Generate block with unverified tx with low amount, it should fail to validate agains stake
        low_amount_tx_bytes = sendCoinsToAddressDryRun(sc_node1, new_public_key, 0, 0)["transactionBytes"]
        try:
            generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True,
                                forced_tx=[low_amount_tx_bytes])
            assert_true(False, "Forced transaction should fail to verify agains stake")
        except Exception as e:
            assert_true("There was an internal server error." in e.error)

        # Generate block with forced unverified openStakeTransaction, it should succeed and transaction be included
        forger0_box = self.find_box(allBoxes, self.allowed_forger_propositions[0].publicKey)
        open_stake_tx_bytes = createOpenStakeTransaction(sc_node1, forger0_box["id"], new_public_key, forger_index=0, fee=forger0_box["value"])["transactionBytes"]
        block_id = generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True, forced_tx=[open_stake_tx_bytes])
        forging_info = http_block_forging_info(sc_node1)
        assert_equal(3, forging_info["bestEpochNumber"])
        block = sc_node1.block_findById(blockId=block_id)
        assert_equal("OpenStakeTransaction", block["result"]["block"]["sidechainTransactions"][0]["typeName"])


if __name__ == "__main__":
    SidechainForkOneForcedTransactionsTest().main()
