#!/usr/bin/env python3

import codecs
import pprint
import time
from decimal import Decimal

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.mc_test.mc_test import CertTestUtils, generate_random_field_element_hex
from test_framework.util import assert_false, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, \
    assert_true, create_alien_sidechain, create_certificate_for_alien_sc
from SidechainTestFramework.sc_forging_util import *

"""
Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Test:
    Create 2 alien sidechains of version 0 and 1. Test that a MC referenced block containing 3 certificates, one from the local SC and
    2 from the aliens with custom field elements of length less than  32 bytes, is correctly handled when forging takes
    place.    
"""


class SCVersionsAndMCCertificates(SidechainTestFramework):

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length, btr_data_length=0
                                                        , sc_creation_version=1), sc_node_configuration)

        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        print("Mc height = {}".format(mc_node.getblockcount()))
        self.mcTest = CertTestUtils(self.options.tmpdir)

        ret = create_alien_sidechain(self.mcTest, mc_node, scVersion=0, epochLength=self.sc_withdrawal_epoch_length-1, customHexTag="5c00", feCfgList=[128, 128])
        self.scid_ver0 = ret['scid']
        crtx1 = ret['txid']
        assert_true(crtx1 in mc_node.getrawmempool(), "Sc Creation is expected to be added to mempool.")

        ret = create_alien_sidechain(self.mcTest, mc_node, scVersion=1, epochLength=self.sc_withdrawal_epoch_length-1, customHexTag="5c01", feCfgList=[128, 128])
        self.scid_ver1 = ret['scid']
        crtx2 = ret['txid']
        assert_true(crtx2 in mc_node.getrawmempool(), "Sc Creation is expected to be added to mempool.")

        # Generate MC block and SC block
        mcblock_hash1 = mc_node.generate(1)[0]

        #assert_true(crtx1 in mc_node.getblock(mcblock_hash1)['tx'])
        assert_true(crtx2 in mc_node.getblock(mcblock_hash1)['tx'])

        scblock_id1 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node)

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        scblock_id2 = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)

        expEndEpochHeight = None
        scInfoItems = mc_node.getscinfo("*")['items']
        assert_equal(3, len(scInfoItems), "Unexpected number of sc info in MC")
        for it in scInfoItems:
            scid = it['scid']
            if expEndEpochHeight == None:
                # all scs must have the same end epoch height
                expEndEpochHeight = it['endEpochHeight']
            assert_equal(it['endEpochHeight'], expEndEpochHeight, "Unexpected end epoch height for sc={}".format(scid))


        pprint.pprint(scInfoItems)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        sc_cert_hash = mc_node.getrawmempool()[0]


        #-----------------------------------------------------------------------------------------------------------------------
        # in version 0 we must be careful not to have an invalid fe module, for instance 3f as last byte id ok (b'00111111')
        cert1= create_certificate_for_alien_sc(self.mcTest, self.scid_ver0, mc_node, fePatternArray=["1c5478a72455c080282cef347940143f", "4b21c0f7bc8d1d4f02b7b0cc2e78073f"])
        time.sleep(1)
        cert2= create_certificate_for_alien_sc(self.mcTest, self.scid_ver1, mc_node, fePatternArray=["1c5478a72455c080282cef34794014c8", "4b21c0f7bc8d1d4f02b7b0cc2e78072b"])
        time.sleep(1)

        mempool = mc_node.getrawmempool()
        assert_true(cert1 in mempool)
        assert_true(cert2 in mempool)


        # Generate MC block and verify that certificate is present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction (the coinbase only).")
        assert_equal(3, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 3 Certificates.")

        print("MC block with withdrawal certificates for epoch 0 = {0}\n".format(str(mc_node.getblock(we1_2_mcblock_hash, False))))

        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)

        # Verify Certificate for epoch 0 on SC side
        mbref = sc_node.block_best()["result"]["block"]["mainchainBlockReferencesData"]
        mbrefdata = mbref[0]
        pprint.pprint(mbref)
        #input("______")
        we0_sc_cert = mbrefdata["topQualityCertificate"]
        assert_equal(len(mbrefdata["lowerCertificateLeaves"]), 0)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_sc_cert["sidechainId"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_sc_cert["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_sc_cert["endCumulativeScTxCommitmentTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(0, len(we0_sc_cert["backwardTransferOutputs"]), "Backward transfer amount in certificate is wrong.")
        assert_equal(sc_cert_hash, we0_sc_cert["hash"], "Certificate hash is different to the one in MC.")



if __name__ == "__main__":
    SCVersionsAndMCCertificates().main()
