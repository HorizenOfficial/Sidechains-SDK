#!/usr/bin/env python3
import json
import time
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import generate_next_block, \
    connect_sc_nodes, assert_equal, assert_false,\
    assert_true, bootstrap_sidechain_nodes, AccountModel, get_next_epoch_slot, generate_forging_request, start_sc_nodes, \
    check_wallet_coins_balance
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.wallet.importSecret import http_wallet_importSecret
from httpCalls.wallet.importSecrets import http_wallet_importSecrets
from sc_evm_seedernode import check_error_not_enabled_on_seeder_node
from test_framework.util import forward_transfer_to_sidechain, fail, websocket_port_by_mc_node_index, assert_false

"""
Checks the behavior of a seeder node, i.e. a node that doesn't support local or remote transactions.

Configuration:
    - 3 SC nodes connected with each other. One node is a seeder node.
    - 1 MC node

Test:
    - Create a transaction on a normal node. Check that the tx is not in the seeder mempool
    - Try to start forging on seeder node. Verify that an error is returned. 
    - Try to stop forging on seeder node. Verify that an error is returned
    - Try to create a block on seeder node. Verify that an error is returned
    - Check that wallet endpoints don't exist on seeder node.
    - Check that Submitter endpoints don't exist on seeder node. 
    - Check disabled transaction endpoint APIs
    - Check disabled CSW endpoint APIs
    - On node 1 create some blocks containing txs and then revert them. Verify that in node 1 and node 3 the 
    transactions are in their mempool, while the node seeder mempool remains empty
    
"""


class SCSeederNode(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 3
    withdrawalEpochLength = 10

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                ),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                cert_submitter_enabled=False,
                cert_signing_enabled=False,
                handling_txs_enabled=False),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                ),

        ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 600, self.withdrawalEpochLength, csw_enabled=True),
                                         *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)


    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_seeder = self.sc_nodes[1]
        sc_node_3 = self.sc_nodes[2]
        connect_sc_nodes(sc_node_1, 1)
        connect_sc_nodes(sc_node_1, 2)

        # transfer some fund from MC to SC1 at a new address, then mine mc block
        sc_address_1 = http_wallet_createPrivateKey25519(sc_node_1)
        sc_address_3 = http_wallet_createPrivateKey25519(sc_node_3)

        ft_amount_in_zen = Decimal('500.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_address_1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        best_1 = sc_node_1.block_best()["result"]
        best_3 = sc_node_3.block_best()["result"]
        best_seeder = sc_node_seeder.block_best()["result"]
        assert_equal(best_1, best_seeder, "Seeder node best block is not equal to node 1 best")
        assert_equal(best_3, best_seeder, "Seeder node best block is not equal to node 3 best")

        # Create a transaction on node 1. Verify that the tx is in node 3 mempool but seeder node mempool is empty
        sendCoinsToAddress(sc_node_1, sc_address_3, 10, fee=0)

        time.sleep(5)
        assert_equal(1, len(allTransactions(sc_node_1, False)['transactionIds']))
        assert_equal(0, len(allTransactions(sc_node_seeder, False)['transactionIds']))
        assert_equal(1, len(allTransactions(sc_node_3, False)['transactionIds']))

        # Generate a block in order to clean the mempool
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Try to start forging. Verify that an error is returned

        try:
            sc_node_seeder.block_startForging()
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Try to stop forging. Verify that an error is returned

        try:
            sc_node_seeder.block_stopForging()
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Try to create a block. Verify that an error is returned

        forging_info = sc_node_seeder.block_forgingInfo()["result"]
        slots_in_epoch = forging_info["consensusSlotsInEpoch"]
        best_slot = forging_info["bestSlotNumber"]
        best_epoch = forging_info["bestEpochNumber"]

        next_epoch, next_slot = get_next_epoch_slot(best_epoch, best_slot, slots_in_epoch, False)

        try:
            sc_node_seeder.block_generate(generate_forging_request(next_epoch, next_slot, None))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check that wallet endpoints don't exist on seeder node
        try:
            sc_node_seeder.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling wallet method")

        try:
            sc_node_seeder.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling wallet method")

        try:
            sc_node_seeder.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling wallet method")

        try:
            http_wallet_importSecret(sc_node_seeder, "bbbb", "fake_api_key")
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling wallet method")

        try:
            DUMP_PATH = self.options.tmpdir + "/dumpSecrets"
            http_wallet_importSecrets(sc_node_seeder, DUMP_PATH, "fake_api_key")
        except SCAPIException:
            pass
        else:
            fail("SCAPIException expected")

        # Check that Submitter endpoints exist on seeder node
        sc_node_seeder.submitter_enableCertificateSubmitter()
        assert_true(sc_node_seeder.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
        "Failed to enabling certificate submitting on seeder node")

        sc_node_seeder.submitter_disableCertificateSubmitter()
        assert_false(sc_node_seeder.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
        "Failed to disabling certificate submitting on seeder node")

        sc_node_seeder.submitter_enableCertificateSigner()
        assert_true(sc_node_seeder.submitter_isCertificateSignerEnabled()["result"]["enabled"],
        "Failed to enabling certificate signing on seeder node")

        sc_node_seeder.submitter_disableCertificateSigner()
        assert_false(sc_node_seeder.submitter_isCertificateSignerEnabled()["result"]["enabled"],
        "Failed to disabling certificate signing on seeder node")

        # Check that Transaction API are not accessible


        # Checking createCoreTransactionSimplified
        mc_address2 = self.nodes[0].getnewaddress()
        withdrawal_requests = [{"mainchainAddress": mc_address2,
                                "value": 1000}
                               ]

        core_transaction_request = {
            "regularOutputs": [],
            "withdrawalRequests": withdrawal_requests,
            "forgerOutputs": [],
            "fee": 0
        }

        try:
            sc_node_seeder.transaction_createCoreTransactionSimplified(json.dumps(core_transaction_request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking createCoreTransaction
        forger_box = sc_node_1.wallet_allBoxes()["result"]["boxes"][0]

        # Try to withdraw coins from SC to MC: amount below the dust threshold
        core_transaction_request = {
            "transactionInputs": [{"boxId": forger_box["id"]}],
            "regularOutputs": [{"publicKey": sc_address_1, "value": forger_box["value"] - 100}],
            "withdrawalRequests": [{"mainchainAddress": mc_address2,
                                    "value": 1000}],
            "forgerOutputs": []
        }

        try:
            sc_node_seeder.transaction_createCoreTransaction(json.dumps(core_transaction_request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking sendCoinsToAddress
        request = {
            "outputs": [
                {
                    "publicKey": str(sc_address_3),
                    "value": 10
                }
            ],
            "fee": 0
        }

        try:
            sc_node_seeder.transaction_sendCoinsToAddress(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking sendTransaction
        core_transaction_request = {
            "transactionInputs": [{"boxId": forger_box["id"]}],
            "regularOutputs": [{"publicKey": sc_address_1, "value": forger_box["value"] - 100}],
            "withdrawalRequests": [{"mainchainAddress": mc_address2,
                                    "value": 90}],
            "forgerOutputs": []
        }

        coreTransactionJson = sc_node_1.transaction_createCoreTransaction(json.dumps(core_transaction_request))

        try:
            sc_node_seeder.transaction_sendTransaction(json.dumps(coreTransactionJson["result"]))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking withdrawCoins
        request = {
            "outputs": [
                {
                    "mainchainAddress": mc_address2,
                    "value": 100
                    }
                ],
            "fee": 0
            }

        try:
            sc_node_seeder.transaction_withdrawCoins(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking makeForgerStake

        rewards_address = sc_node_1.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        vrf_address = sc_node_1.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        forgerStakes = {
            "outputs": [
                {
                    "publicKey": sc_address_1,
                    "blockSignPublicKey": rewards_address,
                    "vrfPubKey": vrf_address,
                    "value": 10000000000  # in Satoshi
                }
            ],
            "fee": 0
        }

        try:
            sc_node_seeder.transaction_makeForgerStake(json.dumps(forgerStakes))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking spendForgingStake
        all_forger_boxes_req = {"boxTypeClass": "ForgerBox"}
        forger_box_id = sc_node_1.wallet_allBoxes(json.dumps(all_forger_boxes_req))["result"]["boxes"][0]["id"]
        spend_forger_stakes_req = {
            "transactionInputs": [
                {
                "boxId": forger_box_id
                }
            ],
            "regularOutputs": [
                {
                    "publicKey": sc_address_1,
                    "value": self.sc_nodes_bootstrap_info.genesis_account_balance * 100000000  # in Satoshi
                }
            ],
            "forgerOutputs": []
        }

        try:
            sc_node_seeder.transaction_spendForgingStake(json.dumps(spend_forger_stakes_req))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking createOpenStakeTransactionSimplified
        request = {
            "forgerProposition": sc_address_1,
            "forgerIndex": 0,
            "fee": 0,
            "format": False,
            "automaticSend": True
        }

        try:
            sc_node_seeder.transaction_createOpenStakeTransactionSimplified(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Checking createOpenStakeTransaction

        request = {
            "transactionInput":
                {
                    "boxId": forger_box_id
                },
            "regularOutputProposition": sc_address_1,
            "forgerIndex": 0,
            "fee": 0,
            "format": False,
            "automaticSend": False
        }

        try:
            sc_node_seeder.transaction_createOpenStakeTransaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        request = {
            "keyType": 0,
            "keyIndex": 0,
            "newKey": 0,
            "signingKeySignature": "0",
            "masterKeySignature": "0",
            "newKeySignature": "0",
            "format": True,
            "automaticSend": True
        }

        try:
            sc_node_seeder.transaction_createKeyRotationTransaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check that CSW API are not accessible

        is_csw_enabled = sc_node_seeder.csw_isCSWEnabled()["result"]["cswEnabled"]
        assert_true(is_csw_enabled, "Ceased Sidechain Withdrawal expected to be enabled.")

        try:
            sc_node_seeder.csw_cswBoxIds()
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        try:
            sc_node_seeder.csw_cswInfo()
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        try:
            sc_node_seeder.csw_nullifier()
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        try:
            sc_node_seeder.csw_generateCswProof()
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Creates some blocks containing txs and then revert them. Verify that in node 1 and node 3 the transactions
        # are in their mempool, while the node seeder mempool remains empty

        max_num_of_blocks = 3

        list_of_mc_block_hash_to_be_reverted = []
        for j in range(max_num_of_blocks):
            sendCoinsToAddress(sc_node_1, sc_address_3, 10, fee=0)
            generate_next_block(sc_node_1, "first node")
            list_of_mc_block_hash_to_be_reverted.append(mc_node.generate(1)[0])

        self.sc_sync_all()

        # Pre-requirements: all mempools are empty
        assert_equal(0, len(allTransactions(sc_node_1, False)['transactionIds']))
        assert_equal(0, len(allTransactions(sc_node_seeder, False)['transactionIds']))
        assert_equal(0, len(allTransactions(sc_node_3, False)['transactionIds']))

        # Create a fork on MC: invalidate the old MC blocks and create new ones
        mc_node.invalidateblock(list_of_mc_block_hash_to_be_reverted[0])
        time.sleep(5)
        mc_node.generate(max_num_of_blocks + 1)

        # Generate a new sc block, in order to see the MC fork
        generate_next_block(sc_node_1, "first node")

        assert_true(len(allTransactions(sc_node_1, False)['transactionIds']) > 0)
        assert_true(len(allTransactions(sc_node_3, False)['transactionIds']) > 0)
        assert_equal(0, len(allTransactions(sc_node_seeder, False)['transactionIds']))



if __name__ == "__main__":
    SCSeederNode().main()
