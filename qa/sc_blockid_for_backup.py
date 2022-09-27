#!/usr/bin/env python3
import logging

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, initialize_chain_clean, start_nodes, connect_nodes_bi, websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import start_sc_nodes, generate_next_blocks, bootstrap_sidechain_nodes
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from httpCalls.backup.blockIdForBackup import getBlockIdForBackup
import time
from httpCalls.block.best import http_block_best
"""
    Setup 1 SC Node. Advanced of some epochs and test the /csw/sidechainBlockItToRollback endpoint
    This endpoint should return the sidechain block id containing the mainchain block reference of the MC block with 
    height =  Genesis_MC_block_height + (current_epoch-2) * withdrawalEpochçength -1
"""
class SidechainBlockIdForBackupTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1
    withdrawalEpochLength = 10

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start 1 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            True,
            automatic_fee_computation=False
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, self.withdrawalEpochLength),
                                         sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        self.sync_all()
        sc_node1 = self.sc_nodes[0]
        mc_node1 = self.nodes[0]

        assert_true(sc_node1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
            "Node 1 submitter expected to be enabled.")

        sc_address_1 = http_wallet_createPrivateKey25519(sc_node1)

        ####################### EPOCH 0 ####################
        logging.info("####################### EPOCH 0 ####################")

        # Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      sc_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        sc_creation_block_height = 480
        sc_creation_block = mc_node1.getblock(str(sc_creation_block_height),2)
        assert_true(len(sc_creation_block["tx"][1]["vsc_ccout"]) == 1)

        #Call the backup/getSidechainBlockIdForBackup endpoint and verify it returns an error (we still not have 2 epoch)
        logging.info("Call the backup/getSidechainBlockIdForBackup endpoint and verify it returns an error (we still not have 2 epoch)")
        res = getBlockIdForBackup(sc_node1)
        assert_true("error" in res)
        assert_equal(res["error"]["code"], "0801")

        #Generate some MC blocks
        mc_node1.generate(self.withdrawalEpochLength-2)

        #Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)
        
        #This block contains the reference to the MC block 459 that will be the first block available to retrieve with the
        # backup/getSidechainBlockIdForBackup endpoint.
        blockIdToRollback = http_block_best(sc_node1)["id"]

        # Generate first mc block of the next epoch
        mc_node1.generate(1)
        generate_next_blocks(sc_node1, "first node", 1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node1.getmempoolinfo()["size"] == 0 and sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node1.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        ####################### EPOCH 1 ####################
        logging.info("####################### EPOCH 1 ####################")
        assert_equal(mc_node1.getscinfo(self.sc_nodes_bootstrap_info.sidechain_id)["items"][0]["state"], "ALIVE")
        assert_equal(mc_node1.getscinfo(self.sc_nodes_bootstrap_info.sidechain_id)["items"][0]["epoch"], 1)

        #Call the backup/getSidechainBlockIdForBackup endpoint and verify it returns an error (we still not have 2 epoch)
        logging.info("Call the backup/getSidechainBlockIdForBackup endpoint and verify it returns an error (we still not have 2 epoch)")
        res = getBlockIdForBackup(sc_node1)
        assert_true("error" in res)
        assert_equal(res["error"]["code"], "0801")

        #Generate some MC blocks
        mc_node1.generate(self.withdrawalEpochLength -1)

        #Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)

        mc_node1.generate(1)
        generate_next_blocks(sc_node1, "first node", 1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node1.getmempoolinfo()["size"] == 0 and sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node1.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        ####################### EPOCH 2 ####################
        logging.info("####################### EPOCH 2 ####################")
        assert_equal(mc_node1.getscinfo(self.sc_nodes_bootstrap_info.sidechain_id)["items"][0]["state"], "ALIVE")
        assert_equal(mc_node1.getscinfo(self.sc_nodes_bootstrap_info.sidechain_id)["items"][0]["epoch"], 2)
        
        #Call the backup/getSidechainBlockIdForBackup endpoint and verify it returns an error (we are asking for the MC height 449)
        res = getBlockIdForBackup(sc_node1)
        assert_true("error" in res)
        assert_equal(res["error"]["code"], "0801")

        #Generate some MC blocks
        mc_node1.generate(self.withdrawalEpochLength -1)

        #Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)

        mc_node1.generate(1)
        generate_next_blocks(sc_node1, "first node", 1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node1.getmempoolinfo()["size"] == 0 and sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node1.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        ####################### EPOCH 3 ####################
        logging.info("####################### EPOCH 3 ####################")

        #Call the backup/getSidechainBlockIdForBackup endpoint and verify it returns the blockIdToRollback
        logging.info("Call the backup/getSidechainBlockIdForBackup endpoint and verify it returns the blockIdToRollback")
        res = getBlockIdForBackup(sc_node1)
        logging.info(res)
        assert_equal(res["result"]["blockId"], blockIdToRollback)

if __name__ == "__main__":
    SidechainBlockIdForBackupTest().main()
