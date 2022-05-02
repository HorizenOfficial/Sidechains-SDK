#!/usr/bin/env python3
import json
import time
import math

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
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
    Start 1 MC node and 2 SC nodes (with default websocket configuration).
    Both SC nodes are forgers and certificate submitters.
    SC node 1 has all schnorr private keys for cert submission
    SC node 2 has less schnorr private keys than SC node 1 for cert submission.
    SC nodes are connected.

Test:
    - create and enable forger stakes for the SC node 2.
    - generate MC blocks to reach one block before the end of the withdrawal epoch (WE)
    - generate SC blocks to sync with MC node.
    - disconnect SC nodes.
    - SC node 2 generates 1 SC block with one BackwardTransfer request.
    - generate 2 MC blocks to switch WE.
    - SC node 2 generates 2 SC blocks with one MC block ref included in each.
    - SC node 2 automatically starts generating Certificate -> than do submit.
    - SC node 1 generates 2 SC blocks with one MC block ref included in each.
    - SC node 1 automatically starts generating Certificate with better quality -> than do submit.
    - MC node generate 1 block and checks certificates inclusion.
    - Both SC nodes generate 1 SC block each and checks certificates inclusion.
    - generate MC blocks to reach end submission window. 
    - SC nodes generates blocks to sync with MC:
        * SC node 1 can sync with MC, because follows the top quality cert data.
        * SC node 2 can't grow SC chain, because it follows wrong cert data.
    - Connect SC nodes again and check that SC node 2 synced to the state of SC Node 1
"""
class SCMultipleCerts(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

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
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True, True, list(range(6))  # certificate submitter is enabled with 6 schnorr PKs
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, self.sc_creation_amount, self.sc_withdrawal_epoch_length),
            sc_node_1_configuration,
            sc_node_2_configuration)

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length - 1  # minus genesis block
        # Send FT to the SC node 2
        ft_amount = self.sc_creation_amount + self.sc_node2_bt_amount
        mc_return_address = mc_node.getnewaddress()
        mc_block_hash_with_ft = mc_make_forward_transfer(mc_node, sc_node2, self.sc_nodes_bootstrap_info.sidechain_id,
                                                         ft_amount, mc_return_address)
        mc_blocks_left_for_we -= 1
        sc_block_id1 = generate_next_block(sc_node1, "first node")
        check_mcreference_presence(mc_block_hash_with_ft, sc_block_id1, sc_node1)
        self.sc_sync_all()  # Sync SC nodes

        # Make and activate forging stake for the SC node 1
        sc_create_forging_stake_mempool(sc_node2, self.sc_creation_amount)
        self.sc_sync_all()  # Sync SC nodes mempools
        # Generate SC block with ForgerStake creation TX
        generate_next_block(sc_node1, "first node")
        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()  # Sync SC nodes
        # Generate SC block on SC node 2 for the next consensus epoch
        generate_next_block(sc_node2, "second node", force_switch_to_next_epoch=True)
        self.sc_sync_all()  # Sync SC nodes

        # Generate MC blocks to reach one block before the end of the withdrawal epoch (WE)
        mc_block_hashes = mc_node.generate(mc_blocks_left_for_we - 1)
        mc_blocks_left_for_we -= len(mc_block_hashes)

        # Generate 1 more SC block to sync with MC
        generate_next_block(sc_node1, "first node")
        self.sc_sync_all()  # Sync SC nodes

        # Disconnect SC nodes
        disconnect_sc_nodes_bi(self.sc_nodes, 0, 1)

        # For SC node 2 add 1 BT to be included into the Cert to separate it from Cert of the Node 1.
        sc_make_withdrawal_request_mempool(mc_node, sc_node2, self.sc_node2_bt_amount)
        generate_next_block(sc_node2, "second node")  # 1 MC block to reach the end of WE

        # Generate MC blocks to switch WE epoch
        print("mc blocks left = " + str(mc_blocks_left_for_we))
        mc_block_hashes = mc_node.generate(mc_blocks_left_for_we + 1)

        # Generate 2 SC blocks on both SC nodes and start them automatic cert creation.
        generate_next_block(sc_node2, "second node")  # 1 MC block to reach the end of WE
        generate_next_block(sc_node2, "second node")  # 1 MC block to trigger Submitter logic
        time.sleep(16)  # to be sure that SC node 2 will finish cert creation faster considering cert submission delay
        # Note: such an order because of the MC wallet behaviour:
        # if lower quality cert will be generated after the higher,
        # wallet will choose another cert change for fee payment.
        # So the lower quality cert will be rejected by the mempool, because of such dependency.
        generate_next_block(sc_node1, "first node")
        generate_next_block(sc_node1, "first node")

        # Wait for Certificates appearance
        time.sleep(10)
        while (mc_node.getmempoolinfo()["size"] < 2 and
               (sc_node1.submitter_isCertGenerationActive()["result"]["state"]
                or sc_node2.submitter_isCertGenerationActive()["result"]["state"])):

            print("Wait for certificates in the MC mempool...")
            if (sc_node1.submitter_isCertGenerationActive()["result"]["state"]):
                print("sc_node1 generating certificate now.")
            if (sc_node2.submitter_isCertGenerationActive()["result"]["state"]):
                print("sc_node2 generating certificate now.")

            time.sleep(2)
            sc_node1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
            sc_node2.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(2, mc_node.getmempoolinfo()["size"], "Certificates was not added to MC node mempool.")

        # Try to generate one more certificate with same quality in order to check that submission attempt will be skipped
        # because sc_node1 cannot produce certificate with better quality
        generate_next_block(sc_node1, "first node")
        time.sleep(2)

        assert_false(sc_node1.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")

        # Generate MC block with certs
        mc_block_hash_with_certs = mc_node.generate(1)[0]
        mc_block_hash_with_certs_hex = mc_node.getblock(mc_block_hash_with_certs, False)
        print("MC block with 2 Certificates: " + mc_block_hash_with_certs_hex)
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(2, len(mc_node.getblock(mc_block_hash_with_certs)["cert"]), "MC block expected to contain 2 certs.")

        # Generate 1 SC block on both SC nodes. Both nodes should be able to grow chain with 2 Certs
        generate_next_block(sc_node1, "first node")
        generate_next_block(sc_node2, "second node")

        # Generate MC block to reach the certificate submission window end.
        mc_block_hash = mc_node.generate(1)[0]

        # Generate 1 SC block on SC node 1. Node 1 should successfully apply SC block and verify MC
        generate_next_block(sc_node1, "first node")

        # Generate 1 SC block on SC node 2. Node 2 must fail on apply block, because of inconsistent top quality cert.
        error_occur = False
        try:
            generate_next_block(sc_node2, "second node")
        except SCAPIException as e:
            print("Expected SCAPIException: " + e.error)
            error_occur = True

        assert_true(error_occur, "Node 2 wrongly verified top quality cert as a valid one.")

        # Generate 1 SC block on SC node 1 to check that it still can growing.
        generate_next_block(sc_node1, "first node")

        # Connect the SC Nodes again and check that they are synced to the Node 1 State.
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes
        sync_sc_blocks(self.sc_nodes)  # Sync SC nodes
        assert_equal(sc_node1.block_best(), sc_node2.block_best(), "SC nodes are not synced as expected.")


if __name__ == "__main__":
    SCMultipleCerts().main()
