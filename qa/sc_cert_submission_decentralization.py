#!/usr/bin/env python2
import json
import time
import math

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_equal, assert_true, assert_false, start_nodes, \
    websocket_port_by_mc_node_index, disconnect_nodes_bi
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, check_box_balance, check_wallet_coins_balance, generate_next_blocks, connect_sc_nodes, \
    sc_connected_peers, disconnect_sc_nodes_bi
from SidechainTestFramework.sc_forging_util import *

"""
Check Pretty Good Decentralization (PGD), the certificate submission decentralization:
1. Creation of Certificate in a network with no certificate signers majority on the single node.

Configuration:
    Start 1 MC node and 3 SC node (with default websocket configuration).
    All SC nodes are connected to the first MC node.
    First SC node is a submitter.
    First SC node obtains 2 keys: (0,1)
    Second SC node obtains 2 keys: (2,3)
    Third SC node obtains 3 keys: (4,5,6)
    
    Network schema:
        N1 <-> N2 <-> N3
Test:
    For the SC node:
        - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
        - generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
        - check epoch 0 certificate with not backward transfers in the MC mempool
        - validate certificate quality, all keys expected to be involved (quality = 7 of 7)
        - exclude third SC node from the network.
        - reach the end of the Withdrawal epoch 1
        - check that Certificate Submission was not started because of lack of singers.
        - Connect back the second and third SC nodes
        - check epoch 1 certificate with not backward transfers in the MC mempool
        - validate certificate quality, all keys expected to be involved (quality = 7 of 7)
"""
class SCCertSubmissionDecentralization(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sc_nodes = 3

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-logtimemicros=1']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True,  # Submitter enabled
            [0, 1],  # certificate submitter is enabled with 2 schnorr PKs
            1  # set max connections to prevent node 1 and node 3 connection
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False,
            [2, 3]  # certificate submitter is enabled with 6 schnorr PKs
        )

        sc_node_3_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False,
            [4, 5, 6],  # certificate submitter is enabled with 6 schnorr PKs
            1  # set max connections to prevent node 1 and node 3 connection
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length),
            sc_node_1_configuration,
            sc_node_2_configuration,
            sc_node_3_configuration)

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network, 720 * 120 * 5)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sc_nodes, self.options.tmpdir)

    def run_test(self):
        # Setup and check network connection
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        sc_node3 = self.sc_nodes[2]
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes 1 and 2
        connect_sc_nodes(sc_node3, 1)  # Connect SC nodes 3 and 2

        self.sc_sync_all()  # Sync SC nodes

        assert_equal(1, sc_connected_peers(sc_node1), "Sc Node 1 expect to have only 1 connection.")
        assert_equal(2, sc_connected_peers(sc_node2), "Sc Node 2 expect to have 2 connections.")
        assert_equal(1, sc_connected_peers(sc_node3), "Sc Node 3 expect to have only 1 connection.")

        # Generate 9 more MC block to finish the first withdrawal epoch, then generate 3 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(9)[8]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        # TODO: generate only 1 sc block as soon as SC block size PR will be merged
        scblock_id2 = generate_next_blocks(sc_node1, "first node", 3)[2]
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node1)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(15)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node1.debug_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Get Certificate for Withdrawal epoch 0
        we0_cert_hash = mc_node.getrawmempool()[0]

        # Generate MC block and verify that certificate is present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 1 Certificate.")
        assert_equal(we0_cert_hash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0], "MC block expected to contain certificate.")
        print("MC block with withdrawal certificate for epoch 0 = {0}\n".format(str(mc_node.getblock(we1_2_mcblock_hash, False))))

        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node1)

        # Verify Certificate quality for epoch 0 on SC side
        mbrefdata = sc_node1.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]
        assert_equal(7, mbrefdata["topQualityCertificate"]["quality"], "Certificate quality is wrong.")

        # Exclude SC Node 3 from the network
        print("Disconnecting SC Node 3 from the network.")
        disconnect_sc_nodes_bi(self.sc_nodes, 1, 2)
        # Check network configuration
        assert_equal(1, sc_connected_peers(sc_node1), "Sc Node 1 expect to have only 1 connection.")
        assert_equal(1, sc_connected_peers(sc_node2), "Sc Node 2 expect to only 1 connection.")
        assert_equal(0, sc_connected_peers(sc_node3), "Sc Node 3 expect to have no connections.")

        # Generate 9 more MC block to finish the second withdrawal epoch, then generate 3 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(8)[7]
        print("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        # TODO: generate only 1 sc block as soon as SC block size PR will be merged
        scblock_id5 = generate_next_blocks(sc_node1, "first node", 3)[2]
        check_mcreferencedata_presence(we1_end_mcblock_hash, scblock_id5, sc_node1)

        # Generate first mc block of the next epoch
        we2_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id6 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_mcreference_presence(we2_1_mcblock_hash, scblock_id6, sc_node1)

        # Wait and check that certificate generation has not started at all.
        time.sleep(15)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node1.debug_isCertGenerationActive()["result"]["state"]:
            print("Wait for possible certificate in mc mempool...")
            time.sleep(2)

        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate was added to Mc node mempool.")

        # Connect back the third SC node and sync the nodes then.
        connect_sc_nodes(sc_node2, 2)  # Connect SC nodes 3 and 2
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(20)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node1.debug_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mmepool.")

        # Get Certificate for Withdrawal epoch 0
        we1_cert_hash = mc_node.getrawmempool()[0]

        # Generate MC block and verify that certificate is present
        we2_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we2_2_mcblock_hash)["cert"]),
                     "MC block expected to contain 1 Certificate.")
        assert_equal(we1_cert_hash, mc_node.getblock(we2_2_mcblock_hash)["cert"][0],
                     "MC block expected to contain certificate.")
        print("MC block with withdrawal certificate for epoch 1 = {0}\n".format(
            str(mc_node.getblock(we2_2_mcblock_hash, False))))

        # Generate SC block and verify that certificate is synced back
        scblock_id7 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_mcreference_presence(we2_2_mcblock_hash, scblock_id7, sc_node1)

        # Verify Certificate quality for epoch 0 on SC side
        mbrefdata = sc_node1.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]
        assert_equal(7, mbrefdata["topQualityCertificate"]["quality"], "Certificate quality is wrong.")


if __name__ == "__main__":
    SCCertSubmissionDecentralization().main()
