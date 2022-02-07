#!/usr/bin/env python3
import time
from decimal import Decimal

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_false, assert_true, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, connect_sc_nodes, generate_next_blocks, generate_next_block
from SidechainTestFramework.sc_forging_util import *

"""
Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 2 SC node(first nod with constant fee, second nod with automatic fee computation)

Test:
    - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
    - generate one more MC and SC block accordingly and await for certificate(with constant fee from the first node) submission to MC node mempool
    - check epoch 0 certificate fee
    - generate MC and SC blocks to reach the end of the Withdrawal epoch 1
    - generate one more MC and SC block accordingly and await for certificate(with no fee from the second node) submission to MC node mempool
    - check epoch 1 certificate fee, certificate fee rate must be close to paytxfee MC parameter
"""

COIN = 100000000  # 1 zec in zatoshis
EXPECTED_CERT_FEE = "0.00023"
CUSTOM_FEE_RATE_ZAT_PER_BYTE = Decimal('2.0')
CUSTOM_FEE_RATE_ZEN_PER_KBYTE = CUSTOM_FEE_RATE_ZAT_PER_BYTE/COIN*1000


class CertFeeConfiguration(SidechainTestFramework):

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-logtimemicros=1', '-paytxfee='+str(CUSTOM_FEE_RATE_ZEN_PER_KBYTE)]] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True,
            automatic_fee_computation=False,
            certificate_fee = EXPECTED_CERT_FEE
        )

        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False,
            automatic_fee_computation=True
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length),
                                         sc_node_1_configuration,
                                         sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(2, self.options.tmpdir)

    def get_fee_rate(self, size, fee):
        return ((fee * COIN) / size)

    def isclose(self, d1, d2, tolerance=Decimal('0.005')):
        dec1 = Decimal(d1)
        dec2 = Decimal(d2)
        return abs(dec1 - dec2) <= tolerance

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        connect_sc_nodes(sc_node_1, 1)

        assert_true(sc_node_1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")
        assert_false(sc_node_2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                     "Node 2 submitter expected to be disabled.")

        # Generate 9 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(9)[8]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        scblock_id2 = generate_next_block(sc_node_1, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node_1)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node_1, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node_1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
            sc_node_2.block_best()
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Get Certificate for Withdrawal epoch 0 and verify it
        we0_certHash = mc_node.getrawmempool()[0]
        print("Withdrawal epoch 0 certificate hash = " + we0_certHash)
        we0_cert = mc_node.getrawtransaction(we0_certHash, 1)
        we0_cert_hex = mc_node.getrawtransaction(we0_certHash)
        print("Withdrawal epoch 0 certificate hex = " + we0_cert_hex)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_cert["cert"]["scid"], "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")

        cert_fee  = mc_node.getrawmempool(True)[we0_certHash]['fee']
        cert_size = mc_node.getrawmempool(True)[we0_certHash]['size']
        assert_equal(Decimal(EXPECTED_CERT_FEE), Decimal(cert_fee), "Certificate fee is differ.")

        rate = self.get_fee_rate(cert_size, cert_fee)
        print("cert fee={}, sz={}, feeRate={}".format(cert_fee, cert_size, rate))

        # Generate MC block and verify that certificate is present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 1 Certificate.")
        assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0], "MC block expected to contain certificate.")
        print("MC block with withdrawal certificate for epoch 0 = {0}\n".format(str(mc_node.getblock(we1_2_mcblock_hash, False))))

        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node_1, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node_1)

        # Disable certificate submission on ferst node and enable on the second node
        sc_node_1.submitter_disableCertificateSubmitter()
        sc_node_2.submitter_enableCertificateSubmitter()
        assert_false(sc_node_1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"], "Node 1 submitter expected to be disabled.")
        assert_true(sc_node_2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"], "Node 2 submitter expected to be enabled.")

        # Generate 9 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(8)[7]
        print("End mc block hash in withdrawal epoch 1 = " + we0_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)
        generate_next_blocks(sc_node_1, "first node", 3)[2]

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id5 =generate_next_blocks(sc_node_1, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id5, sc_node_1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_2.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
            sc_node_2.block_best()
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Get Certificate for Withdrawal epoch 0 and verify it
        we1_certHash = mc_node.getrawmempool()[0]
        print("Withdrawal epoch 0 certificate hash = " + we1_certHash)
        we1_cert = mc_node.getrawtransaction(we1_certHash, 1)
        we1_cert_hex = mc_node.getrawtransaction(we1_certHash)
        print("Withdrawal epoch 0 certificate hex = " + we1_cert_hex)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we1_cert["cert"]["scid"], "Sidechain Id in certificate is wrong.")
        assert_equal(1, we1_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we1_end_epoch_cum_sc_tx_comm_tree_root, we1_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(0, we1_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")

        cert_fee  = mc_node.getrawmempool(True)[we1_certHash]['fee']
        cert_size = mc_node.getrawmempool(True)[we1_certHash]['size']
        rate = self.get_fee_rate(cert_size, cert_fee)
        print("cert fee={}, sz={}, feeRate={}".format(cert_fee, cert_size, rate))
        assert_true(self.isclose(CUSTOM_FEE_RATE_ZAT_PER_BYTE, rate))

if __name__ == "__main__":
    CertFeeConfiguration().main()
