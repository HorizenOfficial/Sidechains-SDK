#!/usr/bin/env python3
import json
import time
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import format_evm
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo
from SidechainTestFramework.scutil import generate_next_block, \
    connect_sc_nodes, assert_equal, \
    assert_true, bootstrap_sidechain_nodes, AccountModel, get_next_epoch_slot, generate_forging_request
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.wallet.importSecret import http_wallet_importSecret
from httpCalls.wallet.importSecrets import http_wallet_importSecrets
from test_framework.util import forward_transfer_to_sidechain, fail, websocket_port_by_mc_node_index

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
    - Check disabled Transaction APIs on seeder node. 
    - Check that read-write ETH RPC are not allowed on seeder node. 
    - On node 1 create some blocks containing txs and then revert them. Verify that in node 1 and node 3 the 
    transactions are in their mempool, while the node seeder mempool remains empty
    
"""


def check_error_not_enabled_on_seeder_node(err):
    assert_true("Invalid operation on node not supporting transactions." in err.error)


class SCEvmSeederNode(AccountChainSetup):

    def __init__(self):
        super().__init__(number_of_sidechain_nodes=3, connect_nodes=False)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled,
                cert_submitter_enabled=False,
                cert_signing_enabled=False,
                handling_txs_enabled=False),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled),

        ]

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, self.forward_amount, self.withdrawalEpochLength),
                                         *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=self.block_timestamp_rewind,
                                                                 model=AccountModel)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_seeder = self.sc_nodes[1]
        sc_node_3 = self.sc_nodes[2]
        connect_sc_nodes(sc_node_1, 1)
        connect_sc_nodes(sc_node_1, 2)

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('500.0')

        mc_return_address = mc_node.getnewaddress()
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_return_address,
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
        nonce_addr_1 = 0
        createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1,
                                 nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                 maxFeePerGas=900000000, value=1)
        nonce_addr_1 += 1

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

        # Check that Submitter endpoints don't exist on seeder node
        try:
            sc_node_seeder.submitter_enableCertificateSubmitter()
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling Submitter method")

        try:
            sc_node_seeder.submitter_disableCertificateSubmitter()
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling Submitter method")

        try:
            sc_node_seeder.submitter_enableCertificateSigner()
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling Submitter method")

        try:
            sc_node_seeder.submitter_disableCertificateSigner()
        except SCAPIException:
            pass
        else:
            fail("expected exception when calling Submitter method")

        ##############################################################################
        # Check disabled Transaction APIs
        ##############################################################################

        # Check createLegacyEIP155Transaction
        request = {
            "from": evm_address_sc1,
            "to": evm_address_sc1,
            "nonce": 0,
            "gasLimit": 100000,
            "gasPrice": 1,
            "value": 1,
            "data": None,
            "signature_v": None,
            "signature_r": None,
            "signature_s": None
        }

        try:
            sc_node_seeder.transaction_createLegacyEIP155Transaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check createEIP1559Transaction
        request = {
            "from": evm_address_sc1,
            "to": evm_address_sc1,
            "nonce": 0,
            "gasLimit": 100000,
            "maxPriorityFeePerGas": 1000,
            "maxFeePerGas": 1100,
            "value": 1,
            "data": None,
            "signature_v": None,
            "signature_r": None,
            "signature_s": None
        }

        try:
            sc_node_seeder.transaction_createEIP1559Transaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check createLegacyTransaction
        request = {
            "from": evm_address_sc1,
            "to": evm_address_sc1,
            "nonce": 0,
            "gasLimit": 100000,
            "gasPrice": 1,
            "value": 1,
            "data": None,
            "signature_v": None,
            "signature_r": None,
            "signature_s": None,
            "outputRawBytes": True
        }

        try:
            sc_node_seeder.transaction_createLegacyTransaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check sendTransaction
        raw_tx = "96dc24d6874a9b01e4a7b7e5b74db504db3731f764293769caef100f551efadf7d378a015faca6ae62ae30a9bf5e3c6aa94f58597edc381d0ec167fa0c84635e12a2d13ab965866ebf7c7aae458afedef1c17e08eb641135f592774e18401e0104f8e7f8e0d98e3230332e3133322e39342e31333784787beded84556c094cf8528c39342e3133372e342e31333982765fb840621168019b7491921722649cd1aa9608f23f8857d782e7495fb6765b821002c4aac6ba5da28a5c91b432e5fcc078931f802ffb5a3ababa42adee7a0c927ff49ef8528c3136322e3234332e34362e39829dd4b840e437a4836b77ad9d9ffe73ee782ef2614e6d8370fcf62191a6e488276e23717147073a7ce0b444d485fff5a0c34c4577251a7a990cf80d8542e21b95aa8c5e6cdd8e3230332e3133322e39342e31333788ffffffffa5aadb3a84556c095384556c0919"

        request = {
            "transactionBytes": raw_tx
        }

        try:
            sc_node_seeder.transaction_sendTransaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check signTransaction
        request = {
            "from": evm_address_sc1,
            "transactionBytes": raw_tx
        }

        try:
            sc_node_seeder.transaction_signTransaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check makeForgerStake

        blockSignPubKey = sc_node_1.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        vrf_public_key = sc_node_1.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        request = {"forgerStakeInfo": {
            "ownerAddress": evm_address_sc1,
            "blockSignPublicKey": blockSignPubKey,
            "vrfPubKey": vrf_public_key,
            "value": 10
        },
            "nonce": 0
        }

        try:
            sc_node_seeder.transaction_makeForgerStake(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check withdrawCoins
        request = {
            "nonce": 0,
            "withdrawalRequest":
                {
                    "mainchainAddress": str(mc_return_address),
                    "value": 1
                },
            "gasInfo": {
                "gasLimit": 10000,
                "maxFeePerGas": 100,
                "maxPriorityFeePerGas": 100
            }
        }

        try:
            sc_node_seeder.transaction_withdrawCoins(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check spendForgingStake
        stake_id_genesis = sc_node_seeder.transaction_allForgingStakes()["result"]['stakes'][0]['stakeId']

        try:
            sc_node_seeder.transaction_spendForgingStake(
                json.dumps({"stakeId": str(stake_id_genesis)}))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check createSmartContract
        request = {
            "contractCode": "0x8888",
        }

        try:
            sc_node_seeder.transaction_createSmartContract(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        # Check openForgerList
        j = {
            "forgerIndex": 0,
        }

        try:
            sc_node_seeder.transaction_openForgerList(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")


        # Check createKeyRotationTransaction
        request = {
            "keyType": 0,
            "keyIndex": 0,
            "newKey": 0,
            "signingKeySignature": "0",
            "masterKeySignature": "0",
            "newKeySignature": "0",
            "nonce": 0,
            "gasInfo": {
                "gasLimit": 10000,
                "maxFeePerGas": 100,
                "maxPriorityFeePerGas": 100
            }
        }

        try:
            sc_node_seeder.transaction_createKeyRotationTransaction(json.dumps(request))
        except SCAPIException as e:
            check_error_not_enabled_on_seeder_node(e)
        else:
            fail("expected exception when calling method")

        ##############################################################################
        # Check that read-write ETH RPC are not allowed
        ##############################################################################

        response = sc_node_seeder.rpc_eth_sendRawTransaction(raw_tx)
        self.check_rpc_not_allowed(response)

        payload = {
            "type": 0,
            "nonce": 10,
            "gas": 23000,
            "value": 1,
            "from": evm_address_sc1,
            "gasPrice": 1000
        }

        response = sc_node_seeder.rpc_eth_signTransaction(payload)
        self.check_rpc_not_allowed(response)

        payload = ["0x335a48952dfc1434c33878d692c42bede32071f9", "0xdeadbeef"]

        response = sc_node_seeder.rpc_eth_sign(payload)
        self.check_rpc_not_allowed(response)

        payload = {
            "nonce": "0x1",
            "data": "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
            "gasPrice": "0x9184e72a000",
            "gas": "0x76c0",
            "to": "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
            "from": "0x335a48952dfc1434c33878d692c42bede32071f9",
            "value": "0x9184e72a"
        }

        response = sc_node_seeder.rpc_eth_sendTransaction(payload)
        self.check_rpc_not_allowed(response)

        response = sc_node_seeder.rpc_eth_call(payload, "latest")
        self.check_rpc_not_allowed(response)

        response = sc_node_seeder.rpc_eth_estimateGas(payload)
        self.check_rpc_not_allowed(response)

        response = sc_node_seeder.rpc_txpool_status()
        self.check_rpc_not_allowed(response)

        response = sc_node_seeder.rpc_txpool_content()
        self.check_rpc_not_allowed(response)

        response = sc_node_seeder.rpc_txpool_contentFrom('0x'+evm_address_sc1)
        self.check_rpc_not_allowed(response)

        response = sc_node_seeder.rpc_txpool_inspect()
        self.check_rpc_not_allowed(response)

        # Creates some blocks containing txs and then revert them. Verify that in node 1 and node 3 the transactions
        # are in their mempool, while the node seeder mempool remains empty

        max_num_of_blocks = 3

        list_of_mc_block_hash_to_be_reverted = []
        for j in range(max_num_of_blocks):
            for i in range(j * self.max_account_slots, (j + 1) * (self.max_account_slots - 1)):
                createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1,
                                         toAddress=evm_address_sc1, nonce=nonce_addr_1,
                                         gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1)
                nonce_addr_1 += 1
            list_of_mc_block_hash_to_be_reverted.append(mc_node.generate(1)[0])
            generate_next_block(sc_node_1, "first node")

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

    @staticmethod
    def check_rpc_not_allowed(response):
        assert_true("error" in response)
        assert_equal("Action not allowed", response['error']['message'])
        assert_equal(2, response['error']['code'])


if __name__ == "__main__":
    SCEvmSeederNode().main()
