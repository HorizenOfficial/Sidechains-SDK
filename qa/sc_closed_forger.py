#!/usr/bin/env python3
import logging

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_true, assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, fail
from SidechainTestFramework.scutil import start_sc_nodes, generate_next_blocks, \
    bootstrap_sidechain_nodes, generate_secrets, generate_vrf_secrets, generate_next_block, UtxoModel
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.transaction.makeForgerStake import makeForgerStake
from httpCalls.wallet.createVrfSecret import http_wallet_createVrfSecret
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress, sendCointsToMultipleAddress
from httpCalls.transaction.openStake import createOpenStakeTransaction, createOpenStakeTransactionSimplified
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.block.best import http_block_best
from httpCalls.block.forgingInfo import http_block_forging_info
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SCForgerConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sidechainauthproxy import SCAPIException

"""
    Setup 1 SC Node with a closed list of forger. Try to stake money with invalid forger info and verify that we are not allowed to stake.
    After that open the stake to the world using the openStakeTransaction and verify that a generic proposition (not included in the forger list) 
    is allowed to forge.
"""


class SidechainClosedForgerTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1

    # the genesis pubKey/vrfKeys are added to the list of allowed forgers, therefore we have a
    # total of 5 allowed forgers
    number_of_forgers = 4

    allowed_forger_propositions = generate_secrets("seed2", number_of_forgers, UtxoModel)
    allowed_forger_vrf_public_keys = generate_vrf_secrets("seed2", number_of_forgers, UtxoModel)

    def __init__(self):
        self.sc_nodes_bootstrap_info = None

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start 1 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        allowedForgers = []
        for i in range(0, self.number_of_forgers):
            allowedForgers += [
                [self.allowed_forger_propositions[i].publicKey, self.allowed_forger_vrf_public_keys[i].publicKey]]
        forger_configuration = SCForgerConfiguration(True, allowedForgers)
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            forger_options=forger_configuration,
            initial_private_keys=list(map(lambda forger: forger.secret, self.allowed_forger_propositions)),
            max_fee=10000000000000
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir
                              #, extra_args=['-agentlib']
                              )

    def find_box(self, boxes, proposition):
        for box in boxes:
            if (box["typeName"] == "ZenBox" and box["proposition"]["publicKey"] == proposition):
                return box
        return {}

    def run_test(self):
        self.sync_all()
        sc_node1 = self.sc_nodes[0]
        mc_node1 = self.nodes[0]
        forger_amount = 1000
        sc_fee = 0

        # Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)
        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      self.allowed_forger_propositions[0].publicKey,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # Try to stake to an invalid blockSignProposition
        logging.info("Try to stake to an invalid blockSignProposition...")
        new_public_key = http_wallet_createPrivateKey25519(self.sc_nodes[0])
        new_vrf_public_key = http_wallet_createVrfSecret(sc_node1)
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key,
                                 self.allowed_forger_vrf_public_keys[0].publicKey, forger_amount, sc_fee)
        logging.info(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        logging.info("Ok!")

        # Try to stake to an invalid vrfPublicKey
        logging.info("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey,
                                 self.allowed_forger_propositions[0].publicKey, new_vrf_public_key, forger_amount,
                                 sc_fee)
        logging.info(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        logging.info("Ok!")

        # Try to stake with an invalid blockSignProposition and an invalid vrfPublicKey
        logging.info("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key,
                                 new_vrf_public_key, forger_amount, sc_fee)
        logging.info(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        logging.info("Ok!")

        # Try to stake with a valid blockSignProposition and a valid vrfPublickey
        logging.info("Try to stake with a valid blockSignProposition and a valid vrfPublickey")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey,
                                 self.allowed_forger_propositions[0].publicKey,
                                 self.allowed_forger_vrf_public_keys[0].publicKey, forger_amount, sc_fee)
        logging.info(result)
        assert_true('result' in result)
        assert_true('transactionId' in result['result'])

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # Create some ZenBoxes for all forger propositions and a new one
        logging.info("Create some ZenBoxes")
        listProp = list(map(lambda forger: forger.publicKey, self.allowed_forger_propositions))
        listProp.append(new_public_key)
        listAmounts = ([1000]*len(self.allowed_forger_propositions))
        listAmounts.append(1500)
        sendCointsToMultipleAddress(sc_node1, listProp, listAmounts, fee=0)

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        allBoxes = http_wallet_allBoxes(sc_node1)
        new_public_key_box = self.find_box(allBoxes, new_public_key)
        assert_true(new_public_key_box != {})

        forging_info = http_block_forging_info(sc_node1)
        assert_equal(forging_info["bestBlockEpochNumber"], 2)

        # Try to send an openStake transaction without had reach the SC Fork 1
        tx_bytes = createOpenStakeTransaction(sc_node1, new_public_key_box["id"], new_public_key, 0, sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true(res["error"]["detail"], "OpenStakeTransaction is still not allowed in this consensus epoch!")
        logging.info("Ok!")

        # Reach the SC Fork 1
        generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)
        forging_info = http_block_forging_info(sc_node1)
        assert_equal(forging_info["bestBlockEpochNumber"], 3)

        # Try to send an openStake transaction with negative forgerIndex
        logging.info("Try to send an openStake transaction with negative forgerIndex")
        try:
            createOpenStakeTransaction(sc_node1, new_public_key_box["id"], new_public_key, -1, sc_fee)
            fail("Try to send an openStake transaction with negative forgerIndex")
        except SCAPIException as e:
            logging.info("Expected SCAPIException: " + e.error)

        logging.info("Ok!")

        # Try to send an openStake transaction with empty output proposition
        logging.info("Try to send an openStake transaction with empty output proposition")
        try:
            createOpenStakeTransaction(sc_node1, new_public_key_box["id"], "", 0, sc_fee)
            fail("Try to send an openStake transaction with empty output proposition")
        except SCAPIException as e:
            logging.info("Expected SCAPIException: " + e.error)

        logging.info("Ok!")

        # Try to send an openStake transaction with empty boxid
        logging.info("Try to send an openStake transaction with empty boxid")
        try:
            createOpenStakeTransaction(sc_node1, "", new_public_key, 0, sc_fee)
            fail("Try to send an openStake transaction with empty output proposition")
        except SCAPIException as e:
            logging.info("Expected SCAPIException: " + e.error)

        logging.info("Ok!")

        # Try to send an openStake transaction with forgerIndex out of bounds
        logging.info("Try to send an openStake transaction with forgerIndex out of bounds")
        tx_bytes = createOpenStakeTransaction(sc_node1, new_public_key_box["id"],new_public_key,self.number_of_forgers+1,sc_fee)

        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("ForgerIndex in OpenStakeTransaction is out of bound" in res["error"]["detail"])
        logging.info("Ok!")

        # Try to send openStake transaction with forgerIndex doesn't match the input proposition
        logging.info("Try to send openStake transaction with forgerIndex doesn't match the input proposition")
        forger0_box = self.find_box(allBoxes, self.allowed_forger_propositions[0].publicKey)
        assert_true(forger0_box != {})
        tx_bytes = createOpenStakeTransaction(sc_node1, forger0_box["id"], new_public_key, 2, sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("OpenStakeTransaction input doesn't match the forgerIndex" in res["error"]["detail"])
        logging.info("Ok!")

        # Forger 0 opens the stake
        logging.info("Forger 0 opens the stake")
        tx_bytes = createOpenStakeTransaction(sc_node1, forger0_box["id"], new_public_key, 0, forger0_box["value"])
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("error" not in res)
        self.sc_sync_all()
        logging.info("Ok!")

        # Try to send an openStakeTransaction with the same forgerIndex of the previous one (in mempool)
        logging.info("Try to send an openStakeTransaction with the same forgerIndex of the previous one (in mempool)")
        allBoxes = http_wallet_allBoxes(sc_node1)
        forger0_box2 = self.find_box(allBoxes, self.allowed_forger_propositions[0].publicKey)
        assert_true(forger0_box2 != {})
        tx_bytes = createOpenStakeTransaction(sc_node1, forger0_box2["id"], new_public_key, 0, sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("Transaction is incompatible" in res["error"]["detail"])
        logging.info("Ok!")

        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # Verify that if we use fee = inputBox.value we don't generate new boxes.
        logging.info("Verify that if we use fee = inputBox.value we don't generate new boxes.")
        bestBlock = http_block_best(sc_node1)
        assert_equal(bestBlock["sidechainTransactions"][0]["newBoxes"], [])
        logging.info("Ok!")

        # Try to send an openStakeTransaction with the same forgerIndex of the previous one
        logging.info("Try to send an openStakeTransaction with the same forgerIndex of the previous one")
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("Forger already opened the stake" in res["error"]["detail"])
        logging.info("Ok!")

        # Forger 1 opens the stake
        logging.info("Forger 1 opens the stake")
        sendCoinsToAddress(sc_node1, self.allowed_forger_propositions[1].publicKey, 1500, 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        allBoxes = http_wallet_allBoxes(sc_node1)
        forger1_box = self.find_box(allBoxes, self.allowed_forger_propositions[1].publicKey)
        assert_true(forger1_box != {})
        # Try with automaticSend = True
        res = createOpenStakeTransaction(sc_node1, forger1_box["id"], new_public_key, 1, sc_fee, True, True)
        assert_true("error" not in res)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()
        logging.info("Ok!")

        # Try to stake with an invalid blockSignProposition and an invalid vrfPublicKey.
        # It should be fail because the majority of the allowed forgers didn't opened the stake yet.
        # (At this time only 2/5 of the allowed forgers opened the stake).
        logging.info("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key,
                                 new_vrf_public_key, forger_amount, sc_fee)
        logging.info(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        logging.info("Ok!")

        # Forger 2 opens the stake
        logging.info("Forger 2 opens the stake")
        sendCoinsToAddress(sc_node1, self.allowed_forger_propositions[2].publicKey, 1500, 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        allBoxes = http_wallet_allBoxes(sc_node1)
        forger2_box = self.find_box(allBoxes, self.allowed_forger_propositions[2].publicKey)
        assert_true(forger2_box != {})
        # Try the createOpenStakeTransactionSimplified endpoint
        tx_bytes = createOpenStakeTransactionSimplified(sc_node1, self.allowed_forger_propositions[2].publicKey, 2,
                                                        forger2_box["value"])
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("error" not in res)
        logging.info("Ok!")
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)[0]
        self.sc_sync_all()

        # Verify that we have created an openStakeTransaction with fee = inputBox.value. We should have newBoxes empty.
        logging.info(
            "Verify that we have created an openStakeTransaction with fee = inputBox.value. We should have newBoxes empty.")
        block = http_block_best(sc_node1)
        assert_equal(block["sidechainTransactions"][0]["newBoxes"], [])
        logging.info("Ok!")

        # Now the majority of the allowed forgers opened the stake (3/5) and we should be able to stake to a new forger
        logging.info(
            "Now the majority of the allowed forgers opened the stake (3/5) and we should be able to stake to a new forger")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key,
                                 new_vrf_public_key, forger_amount, sc_fee)
        logging.info(result)
        assert_true('result' in result)
        assert_true('transactionId' in result['result'])
        logging.info("Ok!")

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # Try to create an openStake transaction with the forge operation already opened. The transaction should be rejected.
        logging.info(
            "Try to create an openStake transaction with the forge operation already opened. The transaction should be rejected.")
        sendCoinsToAddress(sc_node1, self.allowed_forger_propositions[3].publicKey, 1500, 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        tx_bytes = createOpenStakeTransactionSimplified(sc_node1, self.allowed_forger_propositions[3].publicKey, 3,
                                                        sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true('error' in res)
        assert_true('OpenStakeTransactions are not allowed because the forger operation has already been opened' in
                    res['error']['detail'])
        logging.info("Ok!")


if __name__ == "__main__":
    SidechainClosedForgerTest().main()
