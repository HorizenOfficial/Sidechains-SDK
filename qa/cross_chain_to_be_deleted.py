import os
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, assert_true, generate_next_blocks, \
    assert_equal, generate_next_block
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.transaction.vote.redeem import redeem
from httpCalls.sc2sc.createRedeemMessage import createRedeemMessage
from httpCalls.transaction.vote.sendVoteMessageToSidechain import sendVoteMessageToSidechain
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from test_framework.util import initialize_chain_clean, start_nodes, websocket_port_by_mc_node_index, \
    forward_transfer_to_sidechain

# THIS TEST WILL BE REMOVED WHEN THE STF WILL SUPPORT MULTIPLE SIDECHAINS AT ONCE
class CrossChainToBeRemoved(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1

    def __init__(self):
        self.sc_nodes_bootstrap_info = None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=True,
            automatic_fee_computation=True,
            sc2sc_proving_key_file_path=os.path.join(self.options.tmpdir, "proving"),
            sc2sc_verification_key_file_path=os.path.join(self.options.tmpdir, "verification")
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node_1, 600, 10, sc_creation_version=SC_CREATION_VERSION_2, is_non_ceasing=True, circuit_type=KEY_ROTATION_CIRCUIT),
            sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 1200)

    def sc_setup_nodes(self):
        #return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir, extra_args=['-agentlib'])
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        public_key_user = self.sc_nodes_bootstrap_info.genesis_account.publicKey
        self.sc_sync_all()

        assert_true(sc_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        # we need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      public_key_user,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node, "", 1)[0]
        self.sc_sync_all()

        # check that the wallet balance is doubled now (forging stake + the forward transfer) (we need to convert to
        # zentoshi also)
        user_balance = (self.sc_nodes_bootstrap_info.genesis_account_balance * 2) * 100000000
        assert_equal(http_wallet_balance(sc_node), user_balance)

        user_address = http_wallet_createPrivateKey25519(sc_node)

        sc_id = self.sc_nodes_bootstrap_info.sidechain_id
        result = sendVoteMessageToSidechain(sc_node, user_address, '8', sc_id, user_address, 100)
        sendTransaction(sc_node, result["transactionBytes"])

        self.sc_sync_all()
        generate_next_blocks(sc_node, "", 1)[0]
        self.sc_sync_all()

        for i in range(10):
            ####### FIRST ROUND
            mc_node.generate(9)
            generate_next_block(sc_node, "")
            self.sc_sync_all()

            time.sleep(5)

            mc_node.generate(1)
            generate_next_block(sc_node, "")
            self.sc_sync_all()

            time.sleep(10)

        time.sleep(20)

        redeem_message = createRedeemMessage(sc_node, 'VERSION_1', 1, sc_id, user_address, sc_id, user_address, '00000008')["result"]["redeemMessage"]
        message = redeem_message["message"]

        redeem_tx = redeem(
            sc_node,
            user_address,
            redeem_message["certificateDataHash"],
            redeem_message["nextCertificateDataHash"],
            redeem_message["scCommitmentTreeRoot"],
            redeem_message["nextScCommitmentTreeRoot"],
            redeem_message["proof"],
            int(message["messageType"]),
            message["senderSidechain"],
            message["sender"],
            message["receiverSidechain"],
            message["receiver"],
            message["payload"],
            100
        )
        sendTransaction(sc_node, redeem_tx["result"]["transactionBytes"])

        generate_next_block(sc_node, "")
        self.sc_sync_all()

        all_boxes = http_wallet_allBoxes(sc_node)
        assert_true(any(box["typeName"] == "CrossChainMessageBox" for box in all_boxes), "Expected a CrossChainMessageBox but none were found")
        assert_true(any(box["typeName"] == "CrossChainRedeemMessageBox" for box in all_boxes), "Expected a CrossChainRedeemMessageBox but none were found")


if __name__ == "__main__":
    CrossChainToBeRemoved().main()
