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
    assert_true
from SidechainTestFramework.sc_forging_util import *

"""
TODO add 

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Test:
    TODO    
"""

def get_sc_data(scid, node):
    ret = node.getscinfo(scid)['items'][0]
    sc_creating_height = ret['createdAtBlockHeight']
    sc_version = ret['sidechainVersion']
    sc_constant = ret['constant']
    sc_customData = ret['customData']
    epochLen = ret['withdrawalEpochLength']
    current_height = node.getblockcount()
    epoch_number = (current_height - sc_creating_height + 1) // epochLen - 1
    end_epoch_block_hash = node.getblockhash(sc_creating_height - 1 + ((epoch_number + 1) * epochLen))
    epoch_cum_tree_hash = node.getblock(end_epoch_block_hash)['scCumTreeHash']
    return epoch_number, epoch_cum_tree_hash, sc_version, sc_constant, sc_customData

def get_field_element_with_padding(field_element, sidechain_version):
    FIELD_ELEMENT_STRING_SIZE = 32 * 2

    if sidechain_version == 0:
        return field_element.rjust(FIELD_ELEMENT_STRING_SIZE, "0")
    elif sidechain_version == 1:
        return field_element.ljust(FIELD_ELEMENT_STRING_SIZE, "0")
    else:
        assert(False)

def swap_bytes(input_buf):
    return codecs.encode(codecs.decode(input_buf, 'hex')[::-1], 'hex').decode()

def get_spendable(node, min_amount):
    # get a UTXO in node's wallet with minimal amount
    utx = False
    listunspent = node.listunspent()
    for aUtx in listunspent:
        if aUtx['amount'] > min_amount:
            utx = aUtx
            change = aUtx['amount'] - min_amount
            break

    if utx == False:
        print(listunspent)

    assert_equal(utx!=False, True)
    return utx, change

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
        #----------------------------------------
        # advance mc chain and reach sc version support fork
        mc_node.generate(450)
        self.mcTest = CertTestUtils(self.options.tmpdir)

        # bootstrapping tool to be fixed for supporting versions. Currently assumes ver0 fe bytes handling
        ret = self.create_sidechain(scVersion=0, epochLength=10, customHexTag="5c00")
        self.scid0 = ret['scid']
        self.crtx0 = ret['txid']

        print("scid = {}".format(self.scid0))
        mc_node.generate(10)

        cert = self.create_certificate(self.scid0, mc_node, fePattern="003f") # b'00111111'

        print("cert = {}".format(cert))
        print("Mc height = {}".format(mc_node.getblockcount()))
        print("MC mempool content: {}".format(mc_node.getrawmempool()))
        #----------------------------------------
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def create_sidechain(self, scVersion, epochLength, customHexTag):
        sc_tag = customHexTag
        vk = self.mcTest.generate_params(sc_tag)
        constant = generate_random_field_element_hex()
        # we use this field to store sc_tag, used by mcTool when creating certificates
        customData = sc_tag
        cswVk = ""
        feCfg = [16]
        cmtCfg = []

        cmdInput = {
            "version": scVersion,
            "withdrawalEpochLength": epochLength,
            "toaddress": "dada",
            "amount": 0.1,
            "wCertVk": vk,
            "constant": constant,
            'customData': customData,
            'wCeasedVk': cswVk,
            'vFieldElementCertificateFieldConfig': feCfg,
            'vBitVectorCertificateFieldConfig': cmtCfg
        }
        try:
            ret = self.nodes[0].sc_create(cmdInput)
        except Exception as e:
            print(e)
            assert_true(False)
        self.sync_all()
        time.sleep(1)
        return ret

    def create_certificate(self, scid, mc_node, fePattern=None):
        epoch_number_1, epoch_cum_tree_hash_1, sc_version, constant, sc_tag = get_sc_data(scid, mc_node)

        print("sc_tag[{}]".format(sc_tag))
        print("constant={}".format(constant))
        print("sc_version={}".format(sc_version))

        # cfgs for SC2: [16], []
        # we must be careful with ending bits for having valid fe.
        if fePattern==None:
            fePattern = "0100"
        vCfe = [fePattern]
        vCmt = []

        MBTR_SC_FEE = 0.0
        FT_SC_FEE = 0.0
        CERT_FEE = Decimal('0.0001')

        # get a UTXO
        utx, change = get_spendable(mc_node, CERT_FEE)

        inputs  = [ {'txid' : utx['txid'], 'vout' : utx['vout']}]
        outputs = { mc_node.getnewaddress() : change }

        # serialized fe for the proof has 32 byte size
        fe1 = get_field_element_with_padding(fePattern, sc_version)
        scid_swapped = str(swap_bytes(scid))

        scProof = self.mcTest.create_test_proof(
            sc_tag, scid_swapped, epoch_number_1, 10, MBTR_SC_FEE, FT_SC_FEE, epoch_cum_tree_hash_1, constant, [], [],
            [fe1])

        print("cum =", epoch_cum_tree_hash_1)
        params = {
            'scid': scid,
            'quality': 10,
            'endEpochCumScTxCommTreeRoot': epoch_cum_tree_hash_1,
            'scProof': scProof,
            'withdrawalEpochNumber': epoch_number_1,
            'vFieldElementCertificateField': vCfe,
            'vBitVectorCertificateField':vCmt
        }

        try:
            rawcert = mc_node.createrawcertificate(inputs, outputs, [], params)
            signed_cert = mc_node.signrawtransaction(rawcert)
            cert = mc_node.sendrawtransaction(signed_cert['hex'])
        except Exception as e:
            print("Send certificate failed with reason {}".format(e))
            assert (False)

        self.sync_all() # we have just one mc node, it will not sleep t all
        time.sleep(1)
        assert_equal(True, cert in self.nodes[0].getrawmempool())
        return cert

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        print("Mc height = {}".format(mc_node.getblockcount()))

        ret = self.create_sidechain(0, epochLength=9, customHexTag="5c01")
        self.scid1_ver0 = ret['scid']
        crtx1 = ret['txid']

        assert_true(crtx1 in mc_node.getrawmempool(), "Sc Creation is expected to be added to mempool.")

        ret = self.create_sidechain(1, epochLength=9, customHexTag="5c02")
        self.scid2_ver1 = ret['scid']
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
        assert_equal(4, len(scInfoItems), "Unexpected number of sc info in MC")
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


        #-----------------------------------------------------------------------------------------------------------------------
        self.create_certificate(self.scid0, mc_node)
        self.create_certificate(self.scid1_ver0, mc_node, fePattern="ff3f")# b'1111 1111' '0011' 1111'
        self.create_certificate(self.scid2_ver1, mc_node, fePattern="ffff")


        #-----------------------------------------------------------------------------------------------------------------------

        # Check that certificate generation skipped because mempool have certificate with same quality
        '''
        generate_next_blocks(sc_node, "first node", 1)[0]
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")
        '''

        # Get Certificate for Withdrawal epoch 0 and verify it
        we0_certHashList = mc_node.getrawmempool()
        cert_found = False
        for certHash in we0_certHashList:
            print("Withdrawal epoch 0 certificate hash = " + certHash)
            we0_cert = mc_node.getrawtransaction(certHash, 1)
            we0_cert_hex = mc_node.getrawtransaction(certHash)
            print("Withdrawal epoch 0 certificate hex = " + we0_cert_hex)
            we0_cert_scid = we0_cert["cert"]["scid"]

            if self.sc_nodes_bootstrap_info.sidechain_id == we0_cert_scid:
                #assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_cert["cert"]["scid"], "Sidechain Id in certificate is wrong.")
                assert_equal(0, we0_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
                assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                         "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
                assert_equal(0, we0_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")
                cert_found = True
                we0_certHash = certHash

        assert_true(cert_found)

        # Generate MC block and verify that certificate is present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction (the coinbase only).")
        assert_equal(4, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 4 Certificates.")

        #assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0], "MC block expected to contain certificate.")
        print("MC block with withdrawal certificates for epoch 0 = {0}\n".format(str(mc_node.getblock(we1_2_mcblock_hash, False))))

        #input("-----------")
        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)
        #input("-----------")

        # Check that certificate generation skipped because chain have certificate with same quality
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")

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
        assert_equal(we0_certHash, we0_sc_cert["hash"], "Certificate hash is different to the one in MC.")



if __name__ == "__main__":
    SCVersionsAndMCCertificates().main()
