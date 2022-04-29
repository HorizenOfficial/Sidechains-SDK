#!/usr/bin/env python3
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_true, initialize_chain_clean, start_nodes, connect_nodes_bi, websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import generate_secrets, start_sc_nodes, generate_next_blocks, bootstrap_sidechain_nodes, generate_secrets, generate_vrf_secrets
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.transaction.makeForgerStake import makeForgerStake
from httpCalls.wallet.createVrfSecret import http_wallet_createVrfSecret
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress, sendCointsToMultipleAddress
from httpCalls.transaction.openStake import createOpenStakeTransaction, createOpenStakeTransactionSimplified
from httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SCForgerConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sidechainauthproxy import SCAPIException

"""
    Setup 1 SC Node with a closed list of forger. Try to stake money with invalid forger info and verify that we are not allowed to stake.
    After that open the stake to the world using the openStakeTransaction and verify that a generic proposition (not included in the forger list) 
    is allowed to forge.
"""
class SidechainClosedForgerTest(SidechainTestFramework):
    number_of_mc_nodes = 3
    number_of_sidechain_nodes = 1
    number_of_forgers = 5
    allowed_forger_propositions = generate_secrets("seed", number_of_forgers)
    allowed_forger_vrf_public_keys = generate_vrf_secrets("seed", number_of_forgers)

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        connect_nodes_bi(self.nodes, 0, 1)
        connect_nodes_bi(self.nodes, 0, 2)
        self.sync_all()

    def setup_nodes(self):
        # Start 3 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        allowedForgers = []
        for i in range (0, self.number_of_forgers):
            allowedForgers += [[self.allowed_forger_propositions[i].publicKey, self.allowed_forger_vrf_public_keys[i].publicKey]]
        forger_configuration  = SCForgerConfiguration(True, allowedForgers)
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            forger_options = forger_configuration,
            initial_private_keys=list(map(lambda forger: forger.secret,self.allowed_forger_propositions))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def find_box(self, boxes, proposition):
        found_box = {}
        for box in boxes:
            if (box["typeName"]=="ZenBox" and box["proposition"]["publicKey"] == proposition):
                found_box = box
        return found_box

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
        print("Try to stake to an invalid blockSignProposition...")
        new_public_key = http_wallet_createPrivateKey25519(self.sc_nodes[0])
        new_vrf_public_key = http_wallet_createVrfSecret(sc_node1)
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key, self.allowed_forger_vrf_public_keys[0].publicKey, forger_amount, sc_fee)
        print(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        print("Ok!")

        # Try to stake to an invalid vrfPublicKey
        print("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, self.allowed_forger_propositions[0].publicKey, new_vrf_public_key, forger_amount, sc_fee)
        print(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        print("Ok!")

        # Try to stake with an invalid blockSignProposition and an invalid vrfPublicKey
        print("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key, new_vrf_public_key, forger_amount, sc_fee)
        print(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        print("Ok!")

        # Try to stake with a valid blockSignProposition and a valid vrfPublickey
        print("Try to stake with a valid blockSignProposition and a valid vrfPublickey")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, self.allowed_forger_propositions[0].publicKey, self.allowed_forger_vrf_public_keys[0].publicKey, forger_amount, sc_fee)
        print(result)
        assert_true('result' in result)
        assert_true('transactionId' in result['result'])

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # Create some ZenBoxes
        print("Create some ZenBoxes")
        sendCointsToMultipleAddress(sc_node1, list(map(lambda forger: forger.publicKey, self.allowed_forger_propositions)), [1000]*len(self.allowed_forger_propositions), 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        sendCoinsToAddress(sc_node1, new_public_key, 1500, 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        allBoxes = http_wallet_allBoxes(sc_node1)
        new_public_key_box = self.find_box(allBoxes, new_public_key)
        assert_true(new_public_key_box != {})

        #Try to send an openStake transaction with negative forgerIndex
        print("Try to send an openStake transaction with negative forgerIndex")
        error_occur = False
        try:
            createOpenStakeTransaction(sc_node1, new_public_key_box["id"],new_public_key,-1,sc_fee)
        except SCAPIException as e:
            print("Expected SCAPIException: " + e.error)
            error_occur = True
        assert_true(error_occur, "Try to send an openStake transaction with negative forgerListIndex")
        print("Ok!")

        #Try to send an openStake transaction with empty output proposition
        print("Try to send an openStake transaction with empty output proposition")
        error_occur = False
        try:
            createOpenStakeTransaction(sc_node1, new_public_key_box["id"],"",0,sc_fee)
        except SCAPIException as e:
            print("Expected SCAPIException: " + e.error)
            error_occur = True
        assert_true(error_occur, "Try to send an openStake transaction with empty output proposition")
        print("Ok!")

        #Try to send an openStake transaction with empty boxid
        print("Try to send an openStake transaction with empty boxid")
        error_occur = False
        try:
            createOpenStakeTransaction(sc_node1, "",new_public_key,0,sc_fee)
        except SCAPIException as e:
            print("Expected SCAPIException: " + e.error)
            error_occur = True         
            assert_true(error_occur, "Try to send an openStake transaction with empty output proposition")
        print("Ok!")

        #Try to send an openStake transaction with forgerIndex out of bounds
        print("Try to send an openStake transaction with forgerIndex out of bounds")
        tx_bytes = createOpenStakeTransaction(sc_node1, new_public_key_box["id"],new_public_key,self.number_of_forgers,sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("ForgerListIndex in OpenStakeTransaction is out of bound" in res["error"]["detail"])
        print("Ok!")

        #Try to send openStake transaction with forgerIndex doesn't match the input proposition
        print("Try to send openStake transaction with forgerIndex doesn't match the input proposition")
        forger0_box = self.find_box(allBoxes, self.allowed_forger_propositions[0].publicKey)
        assert_true(forger0_box != {})
        tx_bytes = createOpenStakeTransaction(sc_node1, forger0_box["id"],new_public_key,2,sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])
        assert_true("OpenStakeTransaction input doesn't match the forgerListIndex" in res["error"]["detail"])
        print("Ok!")

        #Forger 0 opens the stake
        print("Forger 0 opens the stake")
        tx_bytes = createOpenStakeTransaction(sc_node1, forger0_box["id"],new_public_key,0,sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])  
        assert_true("error" not in res)
        self.sc_sync_all()
        print("Ok!")

        #Try to send an openStakeTransaction with the same forgerIndex of the previous one (in mempool)
        print("Try to send an openStakeTransaction with the same forgerIndex of the previous one (in mempool)")
        allBoxes = http_wallet_allBoxes(sc_node1)
        forger0_box2 = self.find_box(allBoxes, self.allowed_forger_propositions[0].publicKey)
        assert_true(forger0_box2 != {})
        tx_bytes = createOpenStakeTransaction(sc_node1, forger0_box2["id"],new_public_key,0,sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])  
        assert_true("Transaction is incompatible" in res["error"]["detail"])
        print("Ok!")

        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        #Try to send an openStakeTransaction with the same forgerIndex of the previous one
        print("Try to send an openStakeTransaction with the same forgerIndex of the previous one")
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])  
        assert_true("Forger already opened the stake" in res["error"]["detail"])
        print("Ok!")

        #Forger 1 opens the stake
        print("Forger 1 opens the stake")
        sendCoinsToAddress(sc_node1, self.allowed_forger_propositions[1].publicKey, 1500, 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        allBoxes = http_wallet_allBoxes(sc_node1)
        forger1_box = self.find_box(allBoxes, self.allowed_forger_propositions[1].publicKey)
        assert_true(forger1_box != {})
        #Try with automaticSend = True
        res = createOpenStakeTransaction(sc_node1, forger1_box["id"],new_public_key,1,sc_fee, True, True)
        assert_true("error" not in res)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()
        print("Ok!")

        # Try to stake with an invalid blockSignProposition and an invalid vrfPublicKey.
        # It should be fail because the majority of the allowed forgers didn't opened the stake yet.
        # (At this time only 2/5 of the allowed forgers opened the stake).
        print("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key, new_vrf_public_key, forger_amount, sc_fee)
        print(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        print("Ok!")

        #Forger 2 opens the stake
        print("Forger 2 opens the stake")
        sendCoinsToAddress(sc_node1, self.allowed_forger_propositions[2].publicKey, 1500, 0)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()


        allBoxes = http_wallet_allBoxes(sc_node1)
        forger2_box = self.find_box(allBoxes, self.allowed_forger_propositions[2].publicKey)
        assert_true(forger2_box != {})
        #Try the createOpenStakeTransactionSimplified endpoint
        tx_bytes = createOpenStakeTransactionSimplified(sc_node1, self.allowed_forger_propositions[2].publicKey,2,sc_fee)
        res = sendTransaction(sc_node1, tx_bytes["transactionBytes"])  
        assert_true("error" not in res)
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()
        print("Ok!")

        # Now the majority of the allowed forgers opened the stake (3/5) and we should be able to stake to a new forger
        print("Now the majority of the allowed forgers opened the stake (3/5) and we should be able to stake to a new forger")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_propositions[0].publicKey, new_public_key, new_vrf_public_key, forger_amount, sc_fee)
        print(result) 
        assert_true('result' in result)
        assert_true('transactionId' in result['result'])
        print("Ok!")

if __name__ == "__main__":
    SidechainClosedForgerTest().main()
