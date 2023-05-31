#!/usr/bin/env python3
from decimal import Decimal
import time

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
from test_framework.util import forward_transfer_to_sidechain, assert_equal, assert_true

"""
This is an example test to show how to use MultiSidechainTestFramework
"""


class CrossChainMultipleSc(MultiSidechainTestFramework):
    def __init__(self):
        super().__init__()

    def sc_create_sidechains(self):
        self.sidechains.append(UTXOSidechainInfo(self.options, block_timestamp_rewind=720*1200, withdrawalEpochLength=10))
        self.sidechains.append(UTXOSidechainInfo(self.options, block_timestamp_rewind=720*1200, withdrawalEpochLength=10))

    def run_test(self):
        mc_node = self.nodes[0]
        mc_return_address = mc_node.getnewaddress()
        sc_utxo_1 = self.sidechains[0]
        sc_utxo_2 = self.sidechains[1]
        sc_utxo_node_1 = sc_utxo_1.sc_nodes[0]
        sc_utxo_node_2 = sc_utxo_2.sc_nodes[0]

        assert_true(sc_utxo_node_1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        assert_true(sc_utxo_node_2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 2 submitter expected to be enabled.")

        # INIT UTXO
        sc_utxo_address_1 = http_wallet_createPrivateKey25519(sc_utxo_node_1)
        forward_transfer_to_sidechain(sc_utxo_1.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_utxo_address_1,
                                      sc_utxo_1.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_return_address)

        generate_next_block(sc_utxo_node_1, "")
        print(f'sc 1 best block: {sc_utxo_node_1.block_best()}')

        # INIT UTXO
        sc_utxo_address_2 = http_wallet_createPrivateKey25519(sc_utxo_node_2)
        forward_transfer_to_sidechain(sc_utxo_2.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_utxo_address_2,
                                      sc_utxo_2.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_return_address)

        generate_next_block(sc_utxo_node_2, "")
        print(f'sc 2 best block: {sc_utxo_node_2.block_best()}')

        # STEP 1 - SEND MESSAGE TO UTXO SIDECHAIN
        utxo_sc_id_1 = sc_utxo_1.sc_nodes_bootstrap_info.sidechain_id
        utxo_sc_id_2 = sc_utxo_2.sc_nodes_bootstrap_info.sidechain_id
        print(f'sc1: {utxo_sc_id_1} sc2: {utxo_sc_id_2}')
        print(f'sc1 address: {sc_utxo_address_1} sc2 address: {sc_utxo_address_2}')
        result = sendVoteMessageToSidechain(sc_utxo_node_1, sc_utxo_address_1, '8', utxo_sc_id_2, sc_utxo_address_2, 100)
        sendTransaction(sc_utxo_node_1, result["transactionBytes"])

        generate_next_block(sc_utxo_node_1, "", 1)
        block = sc_utxo_node_1.block_best()
        print(f'THIS BLOCK SHOULD CONTAINS THE CC MESSAGE {block}')
        generate_next_block(sc_utxo_node_2, "", 1)
        print(f'sc 1 best block: {sc_utxo_node_1.block_best()}')
        print(f'sc 2 best block: {sc_utxo_node_2.block_best()}')


        for i in range(5):
            mc_node.generate(9)
            generate_next_block(sc_utxo_node_1, "")
            generate_next_block(sc_utxo_node_1, "")

            print(f'sc 1 best block: {sc_utxo_node_1.block_best()}')
            print(f'sc 2 best block: {sc_utxo_node_2.block_best()}')
            time.sleep(5)

            mc_node.generate(1)

            time.sleep(10)

        time.sleep(20)

        mc_node.generate(3)
        generate_next_block(sc_utxo_node_1, "")
        generate_next_block(sc_utxo_node_1, "")
        generate_next_block(sc_utxo_node_2, "")
        generate_next_block(sc_utxo_node_2, "")

        print('CREATE REDEEM MESSAGE')

        redeem_message = createRedeemMessage(sc_utxo_node_1, 'VERSION_1', 1, utxo_sc_id_1, sc_utxo_address_1, utxo_sc_id_2, sc_utxo_address_2, '00000008')["result"]["redeemMessage"]
        message = redeem_message["message"]
        print(f'THIS IS THE MESSAGE {message} with sc1: {utxo_sc_id_1}, sc2: {utxo_sc_id_2} address 1: {sc_utxo_address_1}, address 2: {sc_utxo_address_2}')

        print('CREATE REDEEM TRANSACTION')
        redeem_tx = redeem(
            sc_utxo_node_2,
            sc_utxo_address_1,
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

        #print(f'THE SC_ID 1: {utxo_sc_id_1}, THE SC_ID 2: {utxo_sc_id_2}')
        #print('SEND TX WITH REDEEM MESSAGE')
        #print(f'ALL THE TXS BEFORE: {allTransactions(sc_utxo_node_1)}')
        #print(f'ALL THE TXS BEFORE: {allTransactions(sc_utxo_node_2)}')
        tx_sent = sendTransaction(sc_utxo_node_2, redeem_tx["result"]["transactionBytes"])
        #print(f'THIS IS THE SENT TX: {tx_sent}')
        #print(f'ALL THE TXS AFTER: {allTransactions(sc_utxo_node_1)}')
        #print(f'ALL THE TXS AFTER: {allTransactions(sc_utxo_node_2)}')

        for i in range(5):
            mc_node.generate(9)
            generate_next_block(sc_utxo_node_2, "")
            generate_next_block(sc_utxo_node_2, "")

            print(f'sc 1 best block: {sc_utxo_node_1.block_best()}')
            print(f'sc 2 best block: {sc_utxo_node_2.block_best()}')
            time.sleep(5)

            mc_node.generate(1)

            time.sleep(6)

            generate_next_block(sc_utxo_node_1, "", 1)
            generate_next_block(sc_utxo_node_2, "", 1)
            print(f'sc 1 best block: {sc_utxo_node_1.block_best()}')
            print(f'sc 2 best block: {sc_utxo_node_2.block_best()}')
            print(f'ALL THE TXS: {allTransactions(sc_utxo_node_1)}')
            print(f'ALL THE TXS: {allTransactions(sc_utxo_node_2)}')

            all_boxes = http_wallet_allBoxes(sc_utxo_node_1)
            print(f'ALL BOXES FROM NODE 1: {all_boxes}')
            all_boxes = http_wallet_allBoxes(sc_utxo_node_2)
            print(f'ALL BOXES FROM NODE 2: {all_boxes}')

        # assert_true(any(box["typeName"] == "CrossChainMessageBox" for box in all_boxes), "Expected a CrossChainMessageBox but none were found")
        # assert_true(any(box["typeName"] == "CrossChainRedeemMessageBox" for box in all_boxes), "Expected a CrossChainRedeemMessageBox but none were found")

if __name__ == "__main__":
    CrossChainMultipleSc().main()
