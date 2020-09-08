#!/usr/bin/env python2
import json
import time
import math
import psutil
import os
import sys

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, check_box_balance, check_wallet_balance, generate_next_blocks, get_sc_node_pids
from SidechainTestFramework.sc_forging_util import *


"""
Repeatedly creates certificates with no backward transfers.
It's useful to monitor memory usage by MC/SC node with tools such as htop, visualvm, ...

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Test:
    For the SC node:
        - verify that all keys/boxes/balances are coherent with the default initialization
        - verify the MC block is included
        - verify that all keys/boxes/balances are changed
        - while(true):
        -- generate MC and SC blocks to reach the end of the Withdrawal epoch 0
        -- generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
        -- check epoch 0 certificate with not backward transfers in the MC mempool
        -- mine 1 more MC block and forge 1 more SC block, check Certificate inclusion into SC block
"""

class SCBackwardTransfer(SidechainTestFramework):

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-logtimemicros=1']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def create_snapshot(self, process):
        print("MEM_USAGE on start: " + time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()) + " "
              + str(process.memory_info().rss / 1024 ** 2) + "Mb")
        sys.stdout.flush()
        exec_str = "pmap -x " + str(process.pid) + " > " + "java_proc_mem_snapshot_" + time.strftime("%Y.%m.%d_%H.%M.%S", time.localtime())
        os.system(exec_str)
        exec_jcmd = "jcmd " + str(process.pid) + " VM.native_memory > jvm_mem_snapshot_" + time.strftime("%Y.%m.%d_%H.%M.%S", time.localtime())
        os.system(exec_jcmd)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Check that MC block with sc creation tx is referenced in the genesis sc block
        mcblock_hash0 = mc_node.getbestblockhash()
        scblock_id0 = sc_node.block_best()["result"]["block"]["id"]
        check_mcreference_presence(mcblock_hash0, scblock_id0, sc_node)

        # Check that MC block with sc creation tx height is the same as in genesis info.
        sc_creation_mc_block_height = mc_node.getblock(mcblock_hash0)["height"]
        assert_equal(sc_creation_mc_block_height, self.sc_nodes_bootstrap_info.mainchain_block_height,
                     "Genesis info expected to have the same genesis mc block height as in MC node.")

        # check all keys/boxes/balances are coherent with the default initialization
        check_wallet_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account_balance)
        check_box_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account, 3, 1,
                                 self.sc_nodes_bootstrap_info.genesis_account_balance)

        # generate 1 additional mc_block
        mc_node.generate(1)[0]

        num_certificates = 2

        print ("Print processes")
        process_id = get_sc_node_pids()
        for pid in process_id:
            print(str(pid))

        process = psutil.Process(process_id[0])
        print(process.name())

        for iter in range(num_certificates):
            #print("MEM_USAGE on start: " + time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime()) + " " + str(process.memory_info().rss/1024**2) + "Mb")
            self.create_snapshot(process)

            # Generate 8 more MC block to finish the withdrawal epoch, then generate 3 more SC block to sync with MC.
            we0_end_mcblock_hash = mc_node.generate(8)[7]
            scblock_id2 = generate_next_blocks(sc_node, "first node", 3)[2]
            check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)

            # Generate first mc block of the next epoch
            we1_1_mcblock_hash = mc_node.generate(1)[0]
            print("End mc block hash in withdrawal epoch " + str(iter) + " = " + we0_end_mcblock_hash)
            scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
            check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)

            print("MEM_USAGE before cert generation: " + time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()) + " "
                  + str(process.memory_info().rss/1024**2) + "Mb")
            sys.stdout.flush()

            # Wait until Certificate will appear in MC node mempool
            attempts = 25
            while mc_node.getmempoolinfo()["size"] == 0 and attempts > 0:
                print("Wait for certificate in mc mempool...")
                time.sleep(10)
                attempts -= 1
                sc_node.block_best() # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
            assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mmepool.")

            print("MEM_USAGE after cert generation: " + time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
                  + " " + str(process.memory_info().rss/1024**2) + "Mb")
            sys.stdout.flush()

            # Get Certificate for Withdrawal epoch 0 and verify it
            we0_certHash = mc_node.getrawmempool()[0]
            print("Withdrawal epoch " + str(iter) + " certificate hash = " + we0_certHash)
            we0_cert = mc_node.getrawcertificate(we0_certHash, 1)
            assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_cert["cert"]["scid"], "Sidechain Id in certificate is wrong.")
            assert_equal(we0_end_mcblock_hash, we0_cert["cert"]["endEpochBlockHash"], "Sidechain endEpochBlockHash in certificate is wrong.")
            assert_equal(0, we0_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")

            # Generate MC block and verify that certificate is present
            we1_2_mcblock_hash = mc_node.generate(1)[0]
            assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
            assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
            assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 1 Certificate.")
            assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0], "MC block expected to contain certificate.")

            # Generate SC block and verify that certificate is synced back
            scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
            check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)

if __name__ == "__main__":
    SCBackwardTransfer().main()
