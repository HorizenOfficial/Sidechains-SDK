#!/usr/bin/env python3
from decimal import Decimal
import time
import logging

from SidechainTestFramework.multi_sc_test_framework import MultiSidechainTestFramework, AccountSidechainInfo, \
    UTXOSidechainInfo
from SidechainTestFramework.scutil import (
    generate_next_block,
)
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.findTransactionByID import http_transaction_findById
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.transaction.vote.sendVoteMessageToSidechain import sendVoteMessageToSidechain
from httpCalls.sc2sc.createRedeemMessage import createRedeemMessage
from httpCalls.transaction.vote.redeem import redeem
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.redeemVoteMessage import redeemVoteMessage
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.sendVoteMessage import sendVoteMessage
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from httpCalls.sc2sc.createAccountRedeemMessage import createAccountRedeemMessage
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.showAllVotes import showAllVotes
from httpCalls.submitter.getWithdrawalEpochInfo import getWithdrawalEpochInfo
from test_framework.util import forward_transfer_to_sidechain, assert_equal, assert_true
from eth_utils import remove_0x_prefix

"""
This is an example test to show how to use MultiSidechainTestFramework
"""


class CrossChainMultipleSc(MultiSidechainTestFramework):
    def __init__(self):
        super().__init__()

    def sc_create_sidechains(self):
        self.sidechains.append(AccountSidechainInfo(self.options, block_timestamp_rewind=720*1200, withdrawalEpochLength=10))
        self.sidechains.append(AccountSidechainInfo(self.options, block_timestamp_rewind=720*1200, withdrawalEpochLength=10))

    def run_test(self):
        mc_node = self.nodes[0]
        mc_return_address = mc_node.getnewaddress()
        sc_evm_1 = self.sidechains[0]
        sc_evm_2 = self.sidechains[1]
        node1 = sc_evm_1.sc_nodes[0]
        node2 = sc_evm_2.sc_nodes[0]

        assert_true(node1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        assert_true(node2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 2 submitter expected to be enabled.")


        # SETUP NODE 1
        ret = node1.wallet_createPrivateKeySecp256k1()
        # logging.info(ret)
        evm_address1 = format_evm(ret["result"]["proposition"]["address"])
        hex_evm_addr1 = remove_0x_prefix(evm_address1)

        ft_amount_in_zen = Decimal("1000.00")
        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(sc_evm_1.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(evm_address1),
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(node1, "first node")

        # SETUP NODE 2
        ret = node2.wallet_createPrivateKeySecp256k1()
        # logging.info(ret)
        evm_address2 = format_evm(ret["result"]["proposition"]["address"])
        hex_evm_addr2 = remove_0x_prefix(evm_address2)

        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(sc_evm_2.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(evm_address2),
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(node2, "first node")

        sc_best_block = node1.block_best()["result"]
        sc_best_block = node2.block_best()["result"]
        # logging.info(sc_best_block)

        sc_id1 = sc_evm_1.sc_nodes_bootstrap_info.sidechain_id
        sc_id2 = sc_evm_2.sc_nodes_bootstrap_info.sidechain_id
        input('PREPARING THE SEND VOTE MESSAGE TX. CLICK TO CONTINUE...')
        tx_data = sendVoteMessage(node1, 1, hex_evm_addr1, sc_id2, hex_evm_addr2, '8')
        print(f'THIS IS THE SEND VOTE MESSAGE BODY: {tx_data}')
        input('CLICK TO CONTINUE...')

        # res = sendTransaction(sc_node, payload=tx_data)
        # print(f'THIS IS THE RESPONSE: {res}')

        balance = http_wallet_balance(node1, hex_evm_addr1)
        print(f'THIS IS THE WALLET BALANCE {balance}')

        raw_tx = createEIP1559Transaction(node1,
                                          fromAddress=hex_evm_addr1.lower(),
                                          toAddress='0000000000000000000055555555555555555555',
                                          value=1,
                                          nonce=0,
                                          gasLimit=23000000,
                                          maxPriorityFeePerGas=900000110,
                                          maxFeePerGas=900001100,
                                          data=tx_data
                                          )
        #input('CLICK TO CONTINUE...')

        generate_next_block(node1, "first node")
        generate_next_block(node2, "first node")

        sc_best_block = node1.block_best()["result"]
        print(f'The cross chain message included in the block: {sc_best_block}')
        print('Now advancing with withdrawal epochs')
        input('CLICK TO CONTINUE...')

        sc_best_block = node2.block_best()["result"]
        print(f'second node best block: {sc_best_block}')

        for i in range(5):
            mc_node.generate(9)
            sc_best_block = generate_next_block(node1, "")
            print(sc_best_block)
            mc_node.generate(1)
            sc_best_block = generate_next_block(node1, "")
            print(sc_best_block)

        counter = 0
        epoch = int(getWithdrawalEpochInfo(node1)['epoch'])

        while epoch < 4:
            counter += 1
            epoch = int(getWithdrawalEpochInfo(node1)['epoch'])
            mc_node.generate(1)
            if counter % 2 == 0:
                generate_next_block(node1, "")
            else:
                generate_next_block(node2, "")
            time.sleep(5)

        redeem_message = \
                    createAccountRedeemMessage(node1, 1, hex_evm_addr1.lower(), sc_id2, hex_evm_addr2.lower(), '00000008',sc_id1)["result"]["redeemMessage"]
        print(f'THIS IS THE REDEEM MESSAGE {redeem_message}')
        input('CLICK TO CONTINUE...')

        cert_data_hash = redeem_message['certificateDataHash']
        next_cert_data_hash = redeem_message['nextCertificateDataHash']
        sc_commitment_tree = redeem_message['scCommitmentTreeRoot']
        next_sc_commitment_tree = redeem_message['nextScCommitmentTreeRoot']
        proof = redeem_message['proof']
        redeem_tx_data = redeemVoteMessage(node2, 1, hex_evm_addr1.lower(), sc_id2, hex_evm_addr2.lower(), '00000008',
                                           cert_data_hash, next_cert_data_hash, sc_commitment_tree,
                                           next_sc_commitment_tree, proof)

        raw_tx = createEIP1559Transaction(node2,
                                          fromAddress=hex_evm_addr2.lower(),
                                          toAddress='0000000000000000000066666666666666666666',
                                          value=1,
                                          nonce=0,
                                          gasLimit=23000000,
                                          maxPriorityFeePerGas=900000110,
                                          maxFeePerGas=900001100,
                                          data=redeem_tx_data
                                          )

        print(f'THE RESULT OF REDEEM MESSAGE CALL: {raw_tx}')
        generate_next_block(node2, "first node")

        sc_best_block = node2.block_best()["result"]
        print(f'THIS IS THE BLOCK WITH THE TX: {sc_best_block}')
        input('CLICK TO CONTINUE...')

        tx_body = showAllVotes(node2, evm_address2.lower())

        raw_tx = createEIP1559Transaction(node2,
                                          fromAddress=hex_evm_addr2.lower(),
                                          toAddress='0000000000000000000066666666666666666666',
                                          value=1,
                                          nonce=1,
                                          gasLimit=23000000,
                                          maxPriorityFeePerGas=900000110,
                                          maxFeePerGas=900001100,
                                          data=tx_body
                                          )
        print(f'THE RESULT OF SHOW ALL VOTES CALL: {raw_tx}')

        generate_next_block(node2, "first node")

        block = node2.block_best()

        print(f'the last block: {block}')

if __name__ == "__main__":
    CrossChainMultipleSc().main()
