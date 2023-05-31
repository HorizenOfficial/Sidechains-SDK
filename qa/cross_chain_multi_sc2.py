#!/usr/bin/env python3
from decimal import Decimal
import time

from SidechainTestFramework.multi_sc_test_framework import MultiSidechainTestFramework, AccountSidechainInfo, \
    UTXOSidechainInfo
from SidechainTestFramework.scutil import (
    generate_next_block,
)
from eth_utils import remove_0x_prefix
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
from SidechainTestFramework.account.httpCalls.transaction.simpleapp.redeemVoteMessage import redeemVoteMessage
from httpCalls.sc2sc.createAccountRedeemMessage import createAccountRedeemMessage
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa
from test_framework.util import forward_transfer_to_sidechain, assert_equal, assert_true

"""
This is an example test to show how to use MultiSidechainTestFramework
"""


class CrossChainMultipleSc(MultiSidechainTestFramework):
    def __init__(self):
        super().__init__()

    def sc_create_sidechains(self):
        self.sidechains.append(UTXOSidechainInfo(self.options, block_timestamp_rewind=720*1200, withdrawalEpochLength=10))
        self.sidechains.append(AccountSidechainInfo(self.options, block_timestamp_rewind=720*1200, withdrawalEpochLength=10))

    def run_test(self):
        mc_node = self.nodes[0]
        mc_return_address = mc_node.getnewaddress()
        utxo_sc = self.sidechains[0]
        evm_sc = self.sidechains[1]
        utxo_node = utxo_sc.sc_nodes[0]
        evm_node = evm_sc.sc_nodes[0]

        assert_true(utxo_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        assert_true(evm_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 2 submitter expected to be enabled.")

        # INIT UTXO
        sc_utxo_address_1 = http_wallet_createPrivateKey25519(utxo_node)
        forward_transfer_to_sidechain(utxo_sc.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_utxo_address_1,
                                      utxo_sc.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_return_address)

        generate_next_block(utxo_node, "")
        print(f'sc 1 best block: {utxo_node.block_best()}')

        # INIT UTXO
        ret = evm_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address = format_evm(ret)
        utxo_evm_like = format_evm(sc_utxo_address_1)
        hex_evm_addr = remove_0x_prefix(evm_address)

        ft_amount_in_zen = Decimal('100.0')

        forward_transfer_to_sidechain(evm_sc.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(evm_address),
                                      ft_amount_in_zen,
                                      mc_return_address=mc_return_address)
        generate_next_block(evm_node, "first node")

        # STEP 1 - SEND MESSAGE TO UTXO SIDECHAIN
        utxo_sc_id = utxo_sc.sc_nodes_bootstrap_info.sidechain_id
        evm_sc_id = evm_sc.sc_nodes_bootstrap_info.sidechain_id
        print(f'sc1: {utxo_sc_id} sc2: {evm_sc_id}')
        print(f'sc1 address: {sc_utxo_address_1} sc2 address: {evm_address}')
        result = sendVoteMessageToSidechain(utxo_node, sc_utxo_address_1, '8', evm_sc_id, hex_evm_addr, 100)
        sendTransaction(utxo_node, result["transactionBytes"])

        generate_next_block(utxo_node, "", 1)
        block = utxo_node.block_best()
        print(f'THIS BLOCK SHOULD CONTAINS THE CC MESSAGE {block}')
        generate_next_block(evm_node, "", 1)
        print(f'sc 1 best block: {utxo_node.block_best()}')
        print(f'sc 2 best block: {evm_node.block_best()}')

        print('PRIMA DELLO SFACIMM')
        for i in range(10):
            mc_node.generate(9)
            generate_next_block(utxo_node, "")
            generate_next_block(utxo_node, "")

            print(f'SIAMO VIVI ALL\'ITERAZIONE {i}')

            mc_node.generate(1)

            time.sleep(10)

        time.sleep(20)
        print('ABBIAMO PASSATO IL PORCO DDDDIO')

        print('CREATE REDEEM MESSAGE')

        redeem_message = createRedeemMessage(utxo_node, 'VERSION_1', 1, utxo_sc_id, sc_utxo_address_1, evm_sc_id, hex_evm_addr, '00000008')["result"]["redeemMessage"]
        message = redeem_message["message"]
        print(f'THIS IS THE MESSAGE {message} with sc1: {utxo_sc_id}, sc2: {evm_sc_id} address 1: {sc_utxo_address_1}, address 2: {hex_evm_addr}')

        print('CREATE REDEEM TRANSACTION')
        cert_data_hash = redeem_message['certificateDataHash']
        next_cert_data_hash = redeem_message['nextCertificateDataHash']
        sc_commitment_tree = redeem_message['scCommitmentTreeRoot']
        next_sc_commitment_tree = redeem_message['nextScCommitmentTreeRoot']
        proof = redeem_message['proof']
        redeem_tx_data = redeemVoteMessage(evm_node, 1, sc_utxo_address_1, evm_sc_id, hex_evm_addr.lower(), '00000008',
                                           cert_data_hash, next_cert_data_hash, sc_commitment_tree,
                                           next_sc_commitment_tree, proof)

        raw_tx = createEIP1559Transaction(evm_node,
                                          fromAddress=hex_evm_addr.lower(),
                                          toAddress='0000000000000000000066666666666666666666',
                                          value=1,
                                          nonce=1,
                                          gasLimit=23000000,
                                          maxPriorityFeePerGas=900000110,
                                          maxFeePerGas=900001100,
                                          data=redeem_tx_data
                                          )
        print(f'THIS IS THE FATIDICA RESPONSE {raw_tx}')

        generate_next_block(evm_node, "first node")

        for i in range(5):
            mc_node.generate(9)
            generate_next_block(evm_node, "")
            generate_next_block(evm_node, "")

            print(f'sc 1 best block: {utxo_node.block_best()}')
            print(f'sc 2 best block: {evm_node.block_best()}')
            time.sleep(5)

            mc_node.generate(1)

            time.sleep(6)

            generate_next_block(utxo_node, "", 1)
            generate_next_block(evm_node, "", 1)
            print(f'sc 1 best block: {utxo_node.block_best()}')
            print(f'sc 2 best block: {evm_node.block_best()}')
            print(f'ALL THE TXS: {allTransactions(utxo_node)}')
            print(f'ALL THE TXS: {allTransactions(evm_node)}')

            all_boxes = http_wallet_allBoxes(utxo_node)
            print(f'ALL BOXES FROM NODE 1: {all_boxes}')
            all_boxes = http_wallet_allBoxes(evm_node)
            print(f'ALL BOXES FROM NODE 2: {all_boxes}')

        # assert_true(any(box["typeName"] == "CrossChainMessageBox" for box in all_boxes), "Expected a CrossChainMessageBox but none were found")
        # assert_true(any(box["typeName"] == "CrossChainRedeemMessageBox" for box in all_boxes), "Expected a CrossChainRedeemMessageBox but none were found")

if __name__ == "__main__":
    CrossChainMultipleSc().main()
