import logging
import time
from decimal import Decimal
from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.redeemVoteMessage import redeemVoteMessage
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.sendVoteMessage import \
    sendVoteMessage
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.scutil import generate_next_block
from httpCalls.sc2sc.createAccountRedeemMessage import createAccountRedeemMessage
from test_framework.util import forward_transfer_to_sidechain, assert_true


# THIS TEST WILL BE REMOVED WHEN THE STF WILL SUPPORT MULTIPLE SIDECHAINS AT ONCE
class CrossChainEvmToBeDeleted(AccountChainSetup):
    def __init__(self):
        super().__init__(
            withdrawalEpochLength=10,
            block_timestamp_rewind=720 * 1200,
            circuittype_override=KEY_ROTATION_CIRCUIT,
            sc2sc_proving_key_file_name="proving",
            sc2sc_verification_key_file_name="verification"
        )

    def do_send_raw_tx(self, raw_tx, evm_signer_address):
        sc_node = self.sc_nodes[0]
        signed_raw_tx = signTransaction(sc_node, fromAddress=evm_signer_address, payload=raw_tx)

        tx_hash = sendTransaction(sc_node, payload=signed_raw_tx)
        return tx_hash

    def sc_setup_chain(self):
        # mandatory for this test
        self.options.nonceasing = True
        super().sc_setup_chain()

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        mc_return_address = mc_node.getnewaddress()

        assert_true(sc_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        # logging.info(ret)
        evm_address = format_evm(ret["result"]["proposition"]["address"])
        hex_evm_addr = remove_0x_prefix(evm_address)

        ft_amount_in_zen = Decimal("1000.00")
        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(evm_address),
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        sc_node.block_best()

        sc_id = self.sc_nodes_bootstrap_info.sidechain_id
        tx_data = sendVoteMessage(sc_node, 1, hex_evm_addr, sc_id, hex_evm_addr, '8')

        createEIP1559Transaction(sc_node,
                                 fromAddress=hex_evm_addr.lower(),
                                 toAddress='0000000000000000000055555555555555555555',
                                 value=1,
                                 nonce=0,
                                 gasLimit=23000000,
                                 maxPriorityFeePerGas=900000110,
                                 maxFeePerGas=900001100,
                                 data=tx_data
                                 )
        generate_next_block(sc_node, "first node")

        sc_best_block = sc_node.block_best()["result"]
        logging.info(sc_best_block)

        for i in range(10):
            mc_node.generate(9)
            sc_best_block = generate_next_block(sc_node, "")
            logging.info(sc_best_block)

            time.sleep(10)

            mc_node.generate(1)
            sc_best_block = generate_next_block(sc_node, "")
            logging.info(sc_best_block)
            time.sleep(10)

        time.sleep(30)
        
        redeem_message = \
             createAccountRedeemMessage(sc_node, 1, hex_evm_addr.lower(), sc_id, hex_evm_addr.lower(), '00000008',sc_id)["result"]["redeemMessage"]

        cert_data_hash = redeem_message['certificateDataHash']
        next_cert_data_hash = redeem_message['nextCertificateDataHash']
        sc_commitment_tree = redeem_message['scCommitmentTreeRoot']
        next_sc_commitment_tree = redeem_message['nextScCommitmentTreeRoot']
        proof = redeem_message['proof']
        redeem_tx_data = redeemVoteMessage(sc_node, 1, hex_evm_addr.lower(), sc_id, hex_evm_addr.lower(), '00000008',
                                            cert_data_hash, next_cert_data_hash, sc_commitment_tree,
                                            next_sc_commitment_tree, proof)

        createEIP1559Transaction(sc_node,
                                 fromAddress=hex_evm_addr.lower(),
                                 toAddress='0000000000000000000066666666666666666666',
                                 value=1,
                                 nonce=1,
                                 gasLimit=23000000,
                                 maxPriorityFeePerGas=900000110,
                                 maxFeePerGas=900001100,
                                 data=redeem_tx_data
                                 )

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        sc_best_block = sc_node.block_best()["result"]
        print(f'this is the block: {sc_best_block}')

        assert_true(sc_best_block["block"]["sidechainTransactions"] != [])
        assert_true(sc_best_block["block"]["sidechainTransactions"][0]["to"] != "0000000000000000000066666666666666666666")

if __name__ == "__main__":
    CrossChainEvmToBeDeleted().main()
