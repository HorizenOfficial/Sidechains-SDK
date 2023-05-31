import os
import logging
from decimal import Decimal
import time

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa, deploy_smart_contract, \
    contract_function_static_call
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.scutil import generate_next_block
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.sendVoteMessage import \
    sendVoteMessage
from SidechainTestFramework.account.httpCalls.transaction.createRawEIP1559Transaction import \
    createRawEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from httpCalls.sc2sc.createRedeemMessage import createRedeemMessage
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from httpCalls.sc2sc.createAccountRedeemMessage import createAccountRedeemMessage
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.redeemVoteMessage import redeemVoteMessage
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.showAllVotes import showAllVotes
from httpCalls.submitter.getWithdrawalEpochInfo import getWithdrawalEpochInfo
from test_framework.util import forward_transfer_to_sidechain, assert_true
from sc_evm_test_erc721 import compare_total_supply
from eth_utils import remove_0x_prefix
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction


class CrossChainEvmToBeDeleted(AccountChainSetup):
    def __init__(self):
        super().__init__(
            withdrawalEpochLength=10,
            block_timestamp_rewind=720 * 1200,
            circuittype_override=KEY_ROTATION_CIRCUIT
        )

    def do_send_raw_tx(self, raw_tx, evm_signer_address):
        sc_node = self.sc_nodes[0]
        signed_raw_tx = signTransaction(sc_node, fromAddress=evm_signer_address, payload=raw_tx)

        tx_hash = sendTransaction(sc_node, payload=signed_raw_tx)
        return tx_hash

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

        sc_best_block = sc_node.block_best()["result"]
        # logging.info(sc_best_block)

        sc_id = self.sc_nodes_bootstrap_info.sidechain_id
        tx_data = sendVoteMessage(sc_node, 1, hex_evm_addr, sc_id, hex_evm_addr, '8')
        print(f'THIS IS THE TXBODY: {tx_data}')

        epoch = getWithdrawalEpochInfo(sc_node)
        print(f'THE EPOCH: {epoch}')

        # # res = sendTransaction(sc_node, payload=tx_data)
        # # print(f'THIS IS THE RESPONSE: {res}')
        #
        # nonce_addr_1 = 0
        #
        # balance = http_wallet_balance(sc_node, hex_evm_addr)
        # print(f'THIS IS THE WALLET BALANCE {balance}')
        #
        # raw_tx = createEIP1559Transaction(sc_node,
        #                                   fromAddress=hex_evm_addr.lower(),
        #                                   toAddress='0000000000000000000055555555555555555555',
        #                                   value=1,
        #                                   nonce=0,
        #                                   gasLimit=23000000,
        #                                   maxPriorityFeePerGas=900000110,
        #                                   maxFeePerGas=900001100,
        #                                   data=tx_data
        #                                   )
        # print(f'THIS IS THE FATIDICA RESPONSE {raw_tx}')
        #
        # generate_next_block(sc_node, "first node")
        # self.sc_sync_all()
        #
        # sc_best_block = sc_node.block_best()["result"]
        # logging.info(sc_best_block)
        #
        # for i in range(10):
        #     mc_node.generate(9)
        #     sc_best_block = generate_next_block(sc_node, "")
        #     self.sc_sync_all()
        #     logging.info(sc_best_block)
        #
        #     time.sleep(5)
        #
        #     mc_node.generate(1)
        #     sc_best_block = generate_next_block(sc_node, "")
        #     self.sc_sync_all()
        #     logging.info(sc_best_block)
        #
        #     time.sleep(10)
        #
        # time.sleep(20)
        #
        # redeem_message = \
        #     createAccountRedeemMessage(sc_node, 1, hex_evm_addr.lower(), sc_id, hex_evm_addr.lower(), '00000008',sc_id)["result"]["redeemMessage"]
        # print(f'THIS IS THE REDEEM MESSAGE {redeem_message}')
        # cert_data_hash = redeem_message['certificateDataHash']
        # next_cert_data_hash = redeem_message['nextCertificateDataHash']
        # sc_commitment_tree = redeem_message['scCommitmentTreeRoot']
        # next_sc_commitment_tree = redeem_message['nextScCommitmentTreeRoot']
        # proof = redeem_message['proof']
        # redeem_tx_data = redeemVoteMessage(sc_node, 1, hex_evm_addr.lower(), sc_id, hex_evm_addr.lower(), '00000008',
        #                                    cert_data_hash, next_cert_data_hash, sc_commitment_tree,
        #                                    next_sc_commitment_tree, proof)
        #
        # raw_tx = createEIP1559Transaction(sc_node,
        #                                   fromAddress=hex_evm_addr.lower(),
        #                                   toAddress='0000000000000000000066666666666666666666',
        #                                   value=1,
        #                                   nonce=1,
        #                                   gasLimit=23000000,
        #                                   maxPriorityFeePerGas=900000110,
        #                                   maxFeePerGas=900001100,
        #                                   data=redeem_tx_data
        #                                   )
        # print(f'THIS IS THE FATIDICA RESPONSE {raw_tx}')
        #
        # generate_next_block(sc_node, "first node")
        # self.sc_sync_all()
        #
        # sc_best_block = sc_node.block_best()["result"]
        # logging.info(sc_best_block)
        #
        # generate_next_block(sc_node, "first node")
        # self.sc_sync_all()
        #
        # sc_best_block = sc_node.block_best()["result"]
        # logging.info(sc_best_block)
        #
        # tx_body = showAllVotes(sc_node, evm_address.lower())
        #
        # raw_tx = createEIP1559Transaction(sc_node,
        #                                   fromAddress=hex_evm_addr.lower(),
        #                                   toAddress='0000000000000000000066666666666666666666',
        #                                   value=1,
        #                                   nonce=2,
        #                                   gasLimit=23000000,
        #                                   maxPriorityFeePerGas=900000110,
        #                                   maxFeePerGas=900001100,
        #                                   data=tx_body
        #                                   )
        # print(f'THIS IS THE FATIDICA RESPONSE {raw_tx}')
        #
        # generate_next_block(sc_node, "first node")
        # self.sc_sync_all()
        #
        # block = sc_node.block_best()
        #
        # print(f'the last block: {block}')

if __name__ == "__main__":
    CrossChainEvmToBeDeleted().main()
