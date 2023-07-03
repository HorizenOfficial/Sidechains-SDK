#!/usr/bin/env python3
import os

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SC_CREATION_VERSION_2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, assert_true, generate_next_blocks, \
    assert_equal, generate_next_block
from httpCalls.block.forgingInfo import http_block_forging_info
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.transaction.vote.sendVoteMessageToSidechain import send_vote_message_to_sidechain
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import start_nodes, websocket_port_by_mc_node_index, \
    forward_transfer_to_sidechain, assert_false


class CrossChainForkActivation(SidechainTestFramework):
    sidechain_id = None
    number_of_sidechain_nodes = 1
    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)
    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)
        # return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir, extra_args=['-agentlib'])

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=True,
            cert_signing_enabled=True,
            sc2sc_proving_key_file_path=os.path.join(self.options.tmpdir, "proving"),
            sc2sc_verification_key_file_path=os.path.join(self.options.tmpdir, "verification")
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, withdrawal_epoch_length=10,
                                                        sc_creation_version=SC_CREATION_VERSION_2,
                                                        is_non_ceasing=True,
                                                        circuit_type=self.options.certcircuittype),
                                         sc_node_configuration)
        self.sidechain_id = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5).sidechain_id

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        user_address = http_wallet_createPrivateKey25519(sc_node)

        assert_true(sc_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        # we need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sidechain_id,
                                      mc_node,
                                      user_address,
                                      1000,
                                      mc_node.getnewaddress())

        generate_next_blocks(sc_node, "first_node", 1)

        result = send_vote_message_to_sidechain(sc_node, user_address, 'a6902df6488e8c4434125423a6735609e9818e18009035aa28c8b79fa9974130', self.sidechain_id, user_address, 10)
        send_tx_result = sendTransaction(sc_node, result["transactionBytes"])

        assert_true("error" in send_tx_result)
        assert_equal(send_tx_result["error"]["detail"], "Cannot have a cross chain message box if Sc2Sc feature is not active")

        for _ in range(3):
            generate_next_blocks(sc_node, "fist_node", 5)
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)

        consensus_epoch_data = http_block_forging_info(sc_node)
        assert_equal(consensus_epoch_data["bestEpochNumber"], 5)

        result = send_vote_message_to_sidechain(sc_node, user_address, 'a6902df6488e8c4434125423a6735609e9818e18009035aa28c8b79fa9974130', self.sidechain_id, user_address, 10)
        send_tx_result = sendTransaction(sc_node, result["transactionBytes"])
        assert_false("error" in send_tx_result)

        generate_next_block(sc_node, "first_node")
        block_best = sc_node.block_best()
        assert_true(block_best["result"]["block"]["sidechainTransactions"] != [])
        assert_equal(block_best["result"]["block"]["sidechainTransactions"][0]["typeName"], "SendVoteMessageTransaction")

if __name__ == "__main__":
    CrossChainForkActivation().main()