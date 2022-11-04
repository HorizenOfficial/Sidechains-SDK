#!/usr/bin/env python3
import json
import logging
import time
import math

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from test_framework.util import fail, assert_false, assert_true, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block, connect_sc_nodes, disconnect_sc_nodes_bi, sync_sc_blocks
from SidechainTestFramework.sc_forging_util import *

"""
Check multiple certificates processing:
1. Inclusion of multiple certificates for given SC in the same MC block
2. Top quality certificate verification.
3. Different SC chain top quality certificate processing - chain growing prevention.

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    Both SC nodes are forgers and certificate submitters.
    SC node 1 has all schnorr private keys for cert submission
    SC nodes are connected.

Test:
    - create and enable forger stakes for the SC node 1.
    - generate MC blocks to reach one block before the end of the withdrawal epoch (WE)
    - generate SC blocks to sync with MC node.

"""
class SCMultipleCertsNoCeasing(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 15
    sc_creation_amount = 100  # Zen
    sc_node2_bt_amount = 20  # Zen

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                            extra_args=[['-debug=sc', '-logtimemicros=1', '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True, True, list(range(7))  # certificate submitter is enabled with 7 schnorr PKs
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node,
                           self.sc_creation_amount,
                           self.sc_withdrawal_epoch_length,
                           sc_creation_version=SC_CREATION_VERSION_2,
                           is_non_ceasing=True),
            sc_node_1_configuration
        )

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir
                               # , extra_args=['-agentlib']
                              )

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]

        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length - 1  # minus genesis block
        # Send FT to the SC node 2
        ft_amount = self.sc_creation_amount + self.sc_node2_bt_amount
        mc_return_address = mc_node.getnewaddress()
        mc_block_hash_with_ft = mc_make_forward_transfer(mc_node, sc_node1, self.sc_nodes_bootstrap_info.sidechain_id,
                                                         ft_amount, mc_return_address)
        mc_blocks_left_for_we -= 1
        sc_block_id1 = generate_next_block(sc_node1, "first node")
        check_mcreference_presence(mc_block_hash_with_ft, sc_block_id1, sc_node1)

        # Generate SC block with ForgerStake creation TX
        generate_next_block(sc_node1, "first node")
        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)

        # Generate MC blocks to reach one block before the end of the withdrawal epoch (WE)
        mc_block_hashes = mc_node.generate(mc_blocks_left_for_we - 1)
        mc_blocks_left_for_we -= len(mc_block_hashes)

        # Generate 1 more SC block to sync with MC
        generate_next_block(sc_node1, "first node")
        self.sc_sync_all()  # Sync SC nodes

        # Generate MC blocks to switch WE epoch
        logging.info("mc blocks left = " + str(mc_blocks_left_for_we))
        # mc_block_hashes = mc_node.generate(mc_blocks_left_for_we + 1)
        mc_block_hashes = mc_node.generate(mc_blocks_left_for_we + 15)
        # mc_block_hashes = mc_node.generate(mc_blocks_left_for_we + self.sc_withdrawal_epoch_length + 1)



        # Generate 2 SC blocks on both SC nodes and start them automatic cert creation.
        generate_next_block(sc_node1, "first node")  # 1 MC block to reach the end of WE 0
        sc_block = generate_next_block(sc_node1, "first node")  # 1 MC block to trigger Submitter logic
        check_mcreferencedata_presence(mc_block_hashes[mc_blocks_left_for_we + 14], sc_block, sc_node1)

        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length - 1
        # time.sleep(16)  # to be sure that SC node 2 will finish cert creation faster considering cert submission delay

        # Wait for Certificates appearance
        time.sleep(10)
        while (mc_node.getmempoolinfo()["size"] < 1 and
               sc_node1.submitter_isCertGenerationActive()["result"]["state"]):

            logging.info("Wait for certificates in the MC mempool...")
            if (sc_node1.submitter_isCertGenerationActive()["result"]["state"]):
                logging.info("sc_node1 generating certificate now.")

            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to MC node mempool.")

        mc_block_hashes = mc_node.generate(mc_blocks_left_for_we)
        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length
        generate_next_block(sc_node1, "first node")  # Triggers WE 1 certificate submitting

        # Generate MC block with certs
        mc_block_hashes = mc_node.generate(1)
        mc_blocks_left_for_we -= len(mc_block_hashes)
        generate_next_block(sc_node1, "first node")  # Triggers WE 1 certificate submitting

        # Wait for Certificates appearance
        time.sleep(10)
        while (mc_node.getmempoolinfo()["size"] < 1 and
               sc_node1.submitter_isCertGenerationActive()["result"]["state"]):

            logging.info("Wait for certificates in the MC mempool...")
            if (sc_node1.submitter_isCertGenerationActive()["result"]["state"]):
                logging.info("sc_node1 generating certificate now.")

            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificates was not added to MC node mempool.")

        assert_false(sc_node1.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")

        #
        # # Generate MC block with certs
        # mc_block_hash_with_certs = mc_node.generate(1)[0]
        # mc_block_hash_with_certs_hex = mc_node.getblock(mc_block_hash_with_certs, False)
        # logging.info("MC block with 2 Certificates: " + mc_block_hash_with_certs_hex)
        # assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        # assert_equal(1, len(mc_node.getblock(mc_block_hash_with_certs)["cert"]), "MC block expected to contain 2 certs.")
        #
        # # Generate 1 SC block on both SC nodes. Both nodes should be able to grow chain with 2 Certs
        # generate_next_block(sc_node1, "first node")
        #
        # # Generate MC block to reach the certificate submission window end.
        # mc_block_hash = mc_node.generate(1)[0]
        #
        # # Generate 1 SC block on SC node 1. Node 1 should successfully apply SC block and verify MC
        # generate_next_block(sc_node1, "first node")
        #
        # # Generate 1 SC block on SC node 1 to check that it still can growing.
        # generate_next_block(sc_node1, "first node")

if __name__ == "__main__":
    SCMultipleCertsNoCeasing().main()
