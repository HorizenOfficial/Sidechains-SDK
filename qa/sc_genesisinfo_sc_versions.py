#!/usr/bin/env python3

import pprint
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.mc_test.mc_test import CertTestUtils
from test_framework.util import start_nodes, \
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
    Before bootstrapping the SC, create 2 alien sidechains, one with version=0 the other with version=1 with a single
    custom field elements cfg as 16 bits only.
    Advance their epoch mining 10 MC blocks and sends their certificates to the mainchain, so to have them both
    in the SC creation block. Verify that the bootstrapping tool is able to verify the sc commitment tree hash.
    Then create other two alien sidechains of both versions, sends certificates for all sidechains and continue forging
    Verify that all is correctly working.
"""


class SCGenesisInfoScVersions(SidechainTestFramework):

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
        #----------------------------------------
        # advance mc chain and reach sc version support fork
        mc_node.generate(450)
        self.mcTest = CertTestUtils(self.options.tmpdir)

        ret = create_alien_sidechain(self.mcTest, mc_node, scVersion=0, epochLength=10, customHexTag="5c00", feCfgList=[16])
        self.scid0_ver0 = ret['scid']
        self.crtx0 = ret['txid']
        print("scid = {}".format(self.scid0_ver0))
        time.sleep(1)

        ret = create_alien_sidechain(self.mcTest, mc_node, scVersion=1, epochLength=10, customHexTag="5c01", feCfgList=[16])
        self.scid0_ver1 = ret['scid']
        self.crtx1 = ret['txid']
        print("scid = {}".format(self.scid0_ver1))
        time.sleep(1)

        mc_node.generate(10)

        cert = create_certificate_for_alien_sc(self.mcTest, self.scid0_ver0, mc_node, fePatternArray=["003f"]) # b'00111111'
        print("cert = {}".format(cert))
        time.sleep(1)

        cert = create_certificate_for_alien_sc(self.mcTest, self.scid0_ver1, mc_node, fePatternArray=["003f"]) # b'00111111'
        print("cert = {}".format(cert))
        time.sleep(1)

        print("Mc height = {}".format(mc_node.getblockcount()))
        print("MC mempool content: {}".format(mc_node.getrawmempool()))
        #----------------------------------------
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)



    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        bheight = mc_node.getblockcount()
        bhex    = mc_node.getblock(str(bheight))
        print("Mc height = {}".format(bheight))
        print("Mc block txs   = {}".format(bhex['tx']))
        print("Mc block certs = {}".format(bhex['cert']))
        # check we have 2 transactions (coinbase + this sc creation tx) and 2 certificates for the alien sidechains
        # in the same block
        assert_equal(2, len(bhex["tx"]), "MC block expected to contain 2 transactions.")
        assert_equal(2, len(bhex["cert"]), "MC block expected to contain 2 Certificates.")

        ret = create_alien_sidechain(self.mcTest, mc_node, scVersion=0, epochLength=9, customHexTag="5c02", feCfgList=[16])
        self.scid1_ver0 = ret['scid']
        crtx1 = ret['txid']

        assert_true(crtx1 in mc_node.getrawmempool(), "Sc Creation is expected to be added to mempool.")

        ret = create_alien_sidechain(self.mcTest, mc_node, scVersion=1, epochLength=9, customHexTag="5c03", feCfgList=[16])
        self.scid1_ver1 = ret['scid']
        crtx2 = ret['txid']

        assert_true(crtx2 in mc_node.getrawmempool(), "Sc Creation is expected to be added to mempool.")

        # Generate MC block and SC block
        mcblock_hash1 = mc_node.generate(1)[0]

        assert_true(crtx1 in mc_node.getblock(mcblock_hash1)['tx'])
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
        # there are 4 alien scs + 1 bootstrapped here
        assert_equal(5, len(scInfoItems), "Unexpected number of sc info in MC")
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
        create_certificate_for_alien_sc(self.mcTest, self.scid0_ver0, mc_node, fePatternArray=["0100"])
        time.sleep(1)

        create_certificate_for_alien_sc(self.mcTest, self.scid0_ver1, mc_node, fePatternArray=["0100"])
        time.sleep(1)

        # in version 0 we can have at most 3f for not to have an invalid fe module
        create_certificate_for_alien_sc(self.mcTest, self.scid1_ver0, mc_node, fePatternArray=["ff3f"])# b'1111 1111' '0011' 1111'
        time.sleep(1)

        create_certificate_for_alien_sc(self.mcTest, self.scid1_ver1, mc_node, fePatternArray=["ffff"])
        time.sleep(1)

        # Generate MC block and verify that certificates are present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction (the coinbase only).")
        assert_equal(5, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 5 Certificates.")

        #assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0], "MC block expected to contain certificate.")
        print("MC block with withdrawal certificates = {0}\n".format(str(mc_node.getblock(we1_2_mcblock_hash, False))))

        #input("-----------")
        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)
        #input("-----------")

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
    SCGenesisInfoScVersions().main()
