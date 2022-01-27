#!/usr/bin/env python3
import json
import time
import math
from decimal import Decimal

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from test_framework.util import fail, assert_false, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, initialize_chain_clean
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block, connect_sc_nodes, disconnect_sc_nodes_bi, sync_sc_blocks
from SidechainTestFramework.sc_forging_util import *

"""
Check CertificateSubmitter reaction if top quality certificate in MC has no Coins DB record:
Note:
    Before the certificate (and cert proof) creation Submitter validates the quality against the MC known cert quality.
    Check that MC can return a valid inchain cert data even if all the outputs of the cert were spend.
    So, no coin available and no way to get the block access so the cert access.
    We need to be sure that MC can deal with such case (zend_oo PR 214) and SC is compatible with it.

Configuration:
    Start 2 MC nodes and 2 SC nodes (with default websocket configuration).
    SC node 1 is a forger.
    SC node 2 is a certificate submitter.
    SC nodes are connected.
    MC nodes are connected.

Test:
    - Send some coins to MC node 2
    - Reach the end of the withdrawal epoch and generate cert by SC node 2.
    - Spend all UTXOs of MC node 2
    - Reach the next submission window and be sure that SC node was able to produce a certificate (no websocket errors on MC side).
"""
class SCCertNoCoinRecord(SidechainTestFramework):

    number_of_mc_nodes = 2
    number_of_sidechain_nodes = 2

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 15
    sc_creation_amount = 100  # Zen

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-logtimemicros=1']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(self.nodes[0].hostname, websocket_port_by_mc_node_index(0))),
            False, True, list(range(1))  # certificate submitter is disabled
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(self.nodes[1].hostname, websocket_port_by_mc_node_index(1))),
            True, True, list(range(7))  # certificate submitter is enabled with 7 schnorr PKs
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(self.nodes[0], self.sc_creation_amount, self.sc_withdrawal_epoch_length),
            sc_node_1_configuration,
            sc_node_2_configuration)

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length - 1  # minus genesis block

        mc_node1 = self.nodes[0]
        mc_node2 = self.nodes[1]

        # Connect and sync MC nodes
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()

        assert_equal(0, mc_node2.getbalance(), "MC node 2 expect to have no Zen at all")

        # Send 1 Zen to MC node 2
        mc2_address = mc_node2.getnewaddress()
        mc_node1.sendtoaddress(mc2_address, 1)

        # Generate 1 MC block to see the balances.
        mc_node1.generate(1)
        self.sync_all()
        mc_blocks_left_for_we -= 1
        assert_equal(1, mc_node2.getbalance(), "MC node 2 expect to have some Zen")

        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        # Generate MC blocks to reach the end of the withdrawal epoch (WE)
        mc_block_hashes = mc_node1.generate(mc_blocks_left_for_we)
        mc_blocks_left_for_we -= len(mc_block_hashes)

        # Generate 1 more SC block to sync with MC
        generate_next_block(sc_node1, "first node")
        self.sc_sync_all()  # Sync SC nodes

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node1.generate(1)[0]
        scblock_id3 = generate_next_block(sc_node1, "first node")
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node1)
        self.sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node2.getmempoolinfo()["size"] == 0 and sc_node2.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node2.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        self.sync_all()
        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length - 1

        # Generate 1 MC block to see the balances.
        mc_node1.generate(1)
        self.sync_all()
        mc_blocks_left_for_we -= 1

        # Spend all current UTXOs of the MC node 2 to remove the Cert coin record
        mc2_amount = mc_node2.getbalance()
        utxo_to_pay_bt_fee = Decimal("0.01")

        # Send all coins of MC node 2 to node 1
        mc1_address = mc_node1.getnewaddress()
        mc_node2.sendtoaddress(mc1_address, mc2_amount - utxo_to_pay_bt_fee, "", "", True)

        # Generate 1 MC block to see the balances.
        mc_node1.generate(1)
        self.sync_all()
        mc_blocks_left_for_we -= 1

        # Check that node 2 has no balances, it means that Cert Coin record was removed since all outputs were spent.
        assert_equal(utxo_to_pay_bt_fee, mc_node2.getbalance(), "MC node 2 expect to have no Zen at all")

        # Generate MC blocks to reach the end of the withdrawal epoch (WE)
        mc_block_hashes = mc_node1.generate(mc_blocks_left_for_we)
        mc_blocks_left_for_we -= len(mc_block_hashes)

        # Generate 1 more SC block to sync with MC
        generate_next_block(sc_node1, "first node")
        self.sc_sync_all()  # Sync SC nodes

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node1.generate(1)[0]
        scblock_id3 = generate_next_block(sc_node1, "first node")
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node1)
        self.sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node2.getmempoolinfo()["size"] == 0 and sc_node2.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node2.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")


if __name__ == "__main__":
    SCCertNoCoinRecord().main()
