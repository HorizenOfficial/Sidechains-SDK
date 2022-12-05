#!/usr/bin/env python3
import time
from decimal import *

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, check_wallet_coins_balance
from httpCalls.block.forgingInfo import http_block_forging_info
from test_framework.util import assert_equal, start_nodes, \
    websocket_port_by_mc_node_index

"""
Check that after the hard fork, FT with amount < 54 satoshi is no longer possible
Configuration:
    Start 1 MC node and 1 SC node.
    SC node 1 connected to the MC node 1.
Test:
    For the SC node:
        - Before the fork, do a FT with 50 satoshi, it should not fail.
        - Generate a cert, check that ftScFee is 0
        - Switch to the next epoch, where the fork is enabled
        - Before new cert is generated, it should be still possible to do a FT with 50 satoshi
        - Generate another cert, verify that ftScFee is 54 
        - Assert that FT with 50 satoshi fails
        - Assert that FT with 55 satoshi succeeds
"""


class SCFTLimitFork(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):

        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        _50cent = 0.00000050

        self.do_ft(_50cent)
        self.generate_certificate()

        # check we are still in pre-fork epoch and cert has 0 ftScFee
        forging_info = http_block_forging_info(sc_node)
        assert_equal(forging_info["bestEpochNumber"], 2)
        we0_cert_hash = mc_node.getrawmempool()[0]
        we0_cert = mc_node.getrawtransaction(we0_cert_hash, 1)
        assert_equal(we0_cert["cert"]["ftScFee"], 0)

        # switch to the next consensus epoch
        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        forging_info = http_block_forging_info(sc_node)
        assert_equal(forging_info["bestEpochNumber"], 3)
        check_wallet_coins_balance(sc_node, 100.00000050)

        # before new cert is created, it should be possible to do Ft with 50 satoshi
        self.do_ft(_50cent)

        # generate new cert, it should contain new value for ftScFee = 54 satoshi
        self.generate_certificate()
        we0_cert_hash = mc_node.getrawmempool()[0]
        print("Withdrawal epoch 0 certificate hash = " + we0_cert_hash)
        we0_cert = mc_node.getrawtransaction(we0_cert_hash, 1)
        assert_equal(we0_cert["cert"]["ftScFee"], Decimal(54) / Decimal(100000000))

        # get to the next withdrawal epoch to enable new certificate
        self.sync_all()
        mc_node.generate(9)
        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        mc_node.generate(1)
        generate_next_blocks(sc_node, "first node", 1)
        # try to do another FT with 50 satoshi, assert it fails
        try:
            self.do_ft(_50cent)
        except Exception as e:
            assert_equal('16: bad-sc-tx-not-applicable', e.error['message'])

        # 55 satoshi and above should be successful
        self.do_ft(0.00000055)
        mc_node.generate(1)
        generate_next_blocks(sc_node, "first node", 1)
        check_wallet_coins_balance(sc_node, 100.00000155)

    def generate_certificate(self):
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        mc_node.generate(9)
        generate_next_blocks(sc_node, "first node", 1)
        mc_node.generate(1)
        generate_next_blocks(sc_node, "first node", 1)

        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node.
            # For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

    def do_ft(self, amount):
        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]

        mempool_size_init = mc_node.getmempoolinfo()["size"]
        sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount = amount
        mc_return_address = mc_node.getnewaddress()

        ft_args = [{
            "toaddress": sc_address,
            "amount": ft_amount,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }]
        # Sleep for 1 second to let MC synchronize wallet
        time.sleep(1)
        mc_node.sc_send(ft_args)
        assert_equal(mempool_size_init + 1, mc_node.getmempoolinfo()["size"],
                     "Forward Transfer expected to be added to mempool.")


if __name__ == "__main__":
    SCFTLimitFork().main()
