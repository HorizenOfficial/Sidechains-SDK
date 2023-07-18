import time

from SidechainTestFramework.multi_sc_test_framework import UTXOSidechainInfo, \
    MultiSidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.scutil import assert_true, generate_next_block, generate_next_blocks, assert_equal
from httpCalls.block.forgingInfo import http_block_forging_info
from httpCalls.sc2sc.createRedeemMessage import createRedeemMessage
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.transaction.vote.sendVoteMessageToSidechain import send_vote_message_to_sidechain
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.transaction.vote.redeemVoteMessage import redeem_vote_message
from test_framework.util import forward_transfer_to_sidechain


class CrossChainMessageUtxo(MultiSidechainTestFramework):
    def __init__(self):
        super().__init__()

    def sc_create_sidechains(self):
        self.sidechains.append(UTXOSidechainInfo(
            self.options,
            number_of_sidechain_nodes=1,
            withdrawalEpochLength=10,
            block_timestamp_rewind=720 * 1200 * 20,
            circuittype_override=KEY_ROTATION_CIRCUIT,
            sc2sc_proving_key_file_name="proving",
            sc2sc_verification_key_file_name="verification"
        ))
        self.sidechains.append(UTXOSidechainInfo(
            self.options,
            number_of_sidechain_nodes=1,
            withdrawalEpochLength=10,
            block_timestamp_rewind=720 * 1200 * 20,
            circuittype_override=KEY_ROTATION_CIRCUIT,
            sc2sc_proving_key_file_name="proving",
            sc2sc_verification_key_file_name="verification"
        ))

    def sc_setup_sidechains(self):
        self.sc_create_sidechains()

        mc_node_1 = self.nodes[0]
        for sidechain in self.sidechains:
            sidechain.handle_debug_option()
            sidechain.sc_setup_chain(mc_node_1, {"nonceasing": "true"})
            sidechain.sc_setup_network()

    def run_test(self):
        mc_node = self.nodes[0]
        mc_return_address = mc_node.getnewaddress()

        sc_utxo1 = self.sidechains[0]
        sc_utxo2 = self.sidechains[1]

        sc_utxo1_node = sc_utxo1.sc_nodes[0]
        sc_utxo2_node = sc_utxo2.sc_nodes[0]

        sc_utxo1_id = sc_utxo1.sc_nodes_bootstrap_info.sidechain_id
        sc_utxo2_id = sc_utxo2.sc_nodes_bootstrap_info.sidechain_id

        ## CHECK NODES ARE SUBMITTERS
        assert_true(sc_utxo1_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")
        assert_true(sc_utxo2_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        ## CREATE ADDRESSES
        address_user_x = http_wallet_createPrivateKey25519(sc_utxo1_node)

        address_user_y = http_wallet_createPrivateKey25519(sc_utxo2_node)

        ## TRANSFER SOME FUNDS TO ADDRESSES
        forward_transfer_to_sidechain(sc_utxo1.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      address_user_x,
                                      1000,
                                      mc_node.getnewaddress())

        generate_next_block(sc_utxo1_node, "first node")

        forward_transfer_to_sidechain(sc_utxo2.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      address_user_y,
                                      1000,
                                      mc_node.getnewaddress())

        generate_next_block(sc_utxo2_node, "first node")

        ## REACH THE SC2SC FORK CONSENSUS EPOCH
        for _ in range(3):
            generate_next_blocks(sc_utxo1_node, "fist_node", 5)
            generate_next_block(sc_utxo1_node, "first node", force_switch_to_next_epoch=True)

            generate_next_blocks(sc_utxo2_node, "second node", 5)
            generate_next_block(sc_utxo2_node, "second node", force_switch_to_next_epoch=True)

        consensus_epoch_data = http_block_forging_info(sc_utxo1_node)
        assert_equal(consensus_epoch_data["bestEpochNumber"], 5)

        ## CREATE CROSS CHAIN MESSAGE
        vote = 1
        tx_data = send_vote_message_to_sidechain(sc_utxo1_node, address_user_x, vote, sc_utxo2_id, address_user_y, 100)

        sendTransaction(sc_utxo1_node, tx_data["transactionBytes"])
        generate_next_block(sc_utxo1_node, "first node")

        for i in range(6):
            mc_node.generate(9)
            generate_next_block(sc_utxo1_node, "first node")

            time.sleep(15)

            mc_node.generate(1)
            generate_next_block(sc_utxo1_node, "first node")
            time.sleep(15)

        time.sleep(20)

        redeem_message = \
            createRedeemMessage(sc_utxo1_node, 1, 1, sc_utxo1_id, address_user_x, sc_utxo2_id, address_user_y, vote)["result"]["redeemMessage"]

        generate_next_block(sc_utxo2_node, "second node")

        cert_data_hash = redeem_message['certificateDataHash']
        next_cert_data_hash = redeem_message['nextCertificateDataHash']
        sc_commitment_tree = redeem_message['scCommitmentTreeRoot']
        next_sc_commitment_tree = redeem_message['nextScCommitmentTreeRoot']
        proof = redeem_message['proof']

        generate_next_blocks(sc_utxo2_node, "second node", 5)
        generate_next_block(sc_utxo2_node, "second node", force_switch_to_next_epoch=True)

        redeem_tx_data = redeem_vote_message(sc_utxo2_node, 1, sc_utxo1_id, address_user_x, sc_utxo2_id, address_user_y, address_user_y,
                                           vote, cert_data_hash, next_cert_data_hash, sc_commitment_tree, next_sc_commitment_tree, proof, 100)

        sendTransaction(sc_utxo2_node, redeem_tx_data["result"]["transactionBytes"])
        generate_next_block(sc_utxo2_node, "second node")

        sc_best_block = sc_utxo2_node.block_best()["result"]
        cross_chain_redeem_msg_box = sc_best_block["block"]["sidechainTransactions"][0]["newBoxes"][1]
        cross_chain_message_box = cross_chain_redeem_msg_box["crossChainMessage"]

        assert_equal(1, cross_chain_message_box["messageType"])
        assert_equal(sc_utxo1_id, cross_chain_message_box["senderSidechain"])
        assert_equal(address_user_x, cross_chain_message_box["sender"])
        assert_equal(sc_utxo2_id, cross_chain_message_box["receiverSidechain"])
        assert_equal(address_user_y, cross_chain_message_box["receiver"])
        assert_equal("VERSION_1", cross_chain_message_box["protocolVersion"])

        assert_equal("CrossChainRedeemMessageBox", cross_chain_redeem_msg_box["typeName"])
        assert_equal(address_user_y, cross_chain_redeem_msg_box["proposition"]["publicKey"])
        assert_equal(cert_data_hash, cross_chain_redeem_msg_box["certificateDataHash"])
        assert_equal(next_cert_data_hash, cross_chain_redeem_msg_box["nextCertificateDataHash"])
        assert_equal(sc_commitment_tree, cross_chain_redeem_msg_box["scCommitmentTreeRoot"])
        assert_equal(next_sc_commitment_tree, cross_chain_redeem_msg_box["nextScCommitmentTreeRoot"])
        assert_equal(proof, cross_chain_redeem_msg_box["proof"])

if __name__ == "__main__":
    CrossChainMessageUtxo().main()