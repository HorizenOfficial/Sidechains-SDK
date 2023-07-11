import time
from decimal import Decimal
from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_utils import format_evm, format_eoa
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.redeemVoteMessage import redeemVoteMessage
from SidechainTestFramework.multi_sc_test_framework import AccountSidechainInfo, UTXOSidechainInfo, \
    MultiSidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.scutil import assert_true, generate_next_block, generate_next_blocks, assert_equal
from httpCalls.block.forgingInfo import http_block_forging_info
from httpCalls.sc2sc.createRedeemMessage import createRedeemMessage
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.transaction.vote.sendVoteMessageToSidechain import send_vote_message_to_sidechain
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import forward_transfer_to_sidechain


class CrossChainMessageHybrid(MultiSidechainTestFramework):
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
        self.sidechains.append(AccountSidechainInfo(
            self.options,
            number_of_sidechain_nodes=1,
            max_nonce_gap=5,
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

        sc_utxo = self.sidechains[0]
        sc_evm = self.sidechains[1]

        sc_utxo_node = sc_utxo.sc_nodes[0]
        sc_evm_node = sc_evm.sc_nodes[0]

        sc_utxo_id = sc_utxo.sc_nodes_bootstrap_info.sidechain_id
        sc_evm_id = sc_evm.sc_nodes_bootstrap_info.sidechain_id

        ## CHECK NODES ARE SUBMITTERS
        assert_true(sc_utxo_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")
        assert_true(sc_evm_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        ## CREATE ADDRESSES
        public_key_user_x = http_wallet_createPrivateKey25519(sc_utxo_node)

        private_key_user_y = sc_evm_node.wallet_createPrivateKeySecp256k1()
        evm_address_user_y = format_evm(private_key_user_y["result"]["proposition"]["address"])
        hex_evm_addr_user_y = remove_0x_prefix(evm_address_user_y)

        ## TRANSFER SOME FUNDS TO ADDRESSES
        print(f'THE CONTENT {dir(sc_utxo)}')
        forward_transfer_to_sidechain(sc_utxo.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      public_key_user_x,
                                      1000,
                                      mc_node.getnewaddress())

        generate_next_block(sc_utxo_node, "first node")

        ft_amount_in_zen = Decimal("1000.00")
        forward_transfer_to_sidechain(sc_evm_id,
                                      mc_node,
                                      format_eoa(evm_address_user_y),
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_evm_node, "first node")

        ## REACH THE SC2SC FORK CONSENSUS EPOCH
        for _ in range(18):
            generate_next_blocks(sc_utxo_node, "fist_node", 5)
            generate_next_block(sc_utxo_node, "first node", force_switch_to_next_epoch=True)

        consensus_epoch_data = http_block_forging_info(sc_utxo_node)
        assert_equal(consensus_epoch_data["bestEpochNumber"], 20)

        ## CREATE CROSS CHAIN MESSAGE
        tx_data = send_vote_message_to_sidechain(sc_utxo_node, public_key_user_x, 'a6902df6488e8c4434125423a6735609e9818e18009035aa28c8b79fa9974130', sc_evm_id, hex_evm_addr_user_y, 100)

        sendTransaction(sc_utxo_node, tx_data["transactionBytes"])
        generate_next_block(sc_utxo_node, "first node")
        sc_utxo_node.block_best()

        for i in range(6):
            mc_node.generate(9)
            generate_next_block(sc_utxo_node, "")

            time.sleep(15)

            mc_node.generate(1)
            generate_next_block(sc_utxo_node, "")
            time.sleep(15)

        time.sleep(100)

        redeem_message = \
            createRedeemMessage(sc_utxo_node, 1, 1, sc_utxo_id, public_key_user_x, sc_evm_id, hex_evm_addr_user_y.lower(), 'a6902df6488e8c4434125423a6735609e9818e18009035aa28c8b79fa9974130')["result"]["redeemMessage"]

        for _ in range(2):
            generate_next_blocks(sc_evm_node, "fist_node", 5)
            generate_next_block(sc_evm_node, "first node", force_switch_to_next_epoch=True)

        generate_next_block(sc_evm_node, "first_node")

        cert_data_hash = redeem_message['certificateDataHash']
        next_cert_data_hash = redeem_message['nextCertificateDataHash']
        sc_commitment_tree = redeem_message['scCommitmentTreeRoot']
        next_sc_commitment_tree = redeem_message['nextScCommitmentTreeRoot']
        proof = redeem_message['proof']
        redeem_tx_data = redeemVoteMessage(sc_evm_node, 1, public_key_user_x, sc_evm_id, hex_evm_addr_user_y.lower(), 'a6902df6488e8c4434125423a6735609e9818e18009035aa28c8b79fa9974130',
                                           cert_data_hash, next_cert_data_hash, sc_commitment_tree,
                                           next_sc_commitment_tree, proof)

        createEIP1559Transaction(sc_evm_node,
                                 fromAddress=hex_evm_addr_user_y.lower(),
                                 toAddress='0000000000000000000066666666666666666666',
                                 value=1,
                                 nonce=0,
                                 gasLimit=20000000,
                                 maxPriorityFeePerGas=900000110,
                                 maxFeePerGas=900001100,
                                 data=redeem_tx_data
                                 )

        generate_next_block(sc_evm_node, "first node")

        sc_best_block = sc_evm_node.block_best()["result"]

        assert_true(sc_best_block["block"]["sidechainTransactions"] != [])
        assert_true(sc_best_block["block"]["sidechainTransactions"][0]["to"] != "0000000000000000000066666666666666666666")


if __name__ == "__main__":
    CrossChainMessageHybrid().main()