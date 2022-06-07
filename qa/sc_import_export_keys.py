#!/usr/bin/env python3
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_false, assert_true, initialize_chain_clean, start_nodes, websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import start_sc_nodes, generate_next_blocks, bootstrap_sidechain_nodes, connect_sc_nodes
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from httpCalls.wallet.exportSecret import http_wallet_exportSecret
from httpCalls.wallet.allPublicKeys import http_wallet_allPublicKeys
from httpCalls.wallet.importSecret import http_wallet_importSecret
from httpCalls.wallet.dumpSecrets import http_wallet_dumpSecrets
from httpCalls.wallet.importSecrets import http_wallet_importSecrets
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from SidechainTestFramework.sidechainauthproxy import SCAPIException

"""
    - Setup 2 SC Node.
    - Create a new address on node2
    - Export its private key and import it inside the node1
    - Verify that node1 owns this address
    - Send coins to this address and verify that the balance on node1 is unchanged and node2 owns the new amount
    
    - Create some new addresses on node1 and dump all its secret on file
    - Import the node1 secrets inside the node2 and verify that we imported only the secrets that were missing
"""
class SidechainImportExportKeysTest(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    withdrawalEpochLength=10
    API_KEY = "Horizen"
    API_KEY_HASH = "aa8ed2a907753a4a7c66f2aa1d48a0a74d4fde9a6ef34bae96a86dcd7800af98"

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split = False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        # Start 1 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            True,
            automatic_fee_computation=False,
            api_key_hash=self.API_KEY_HASH
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            True,
            automatic_fee_computation=False,
            api_key_hash=self.API_KEY_HASH
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, self.withdrawalEpochLength),
                                         sc_node_1_configuration,
                                         sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 2 SC nodes
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)
    
    def findAddress(self, propositions, address):
        for proposition in propositions:
            if (proposition['publicKey'] == address):
                return True
        return False
    
    def readFile(self, file_path):
        f = open(file_path, "r")
        key_list = []
        for line in f:
            if ("#" not in line):
                row = line.split(" ")
                key_list += [(row[0], row[1][:-1])]
        return key_list

    def run_test(self):
        self.sync_all()
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes
        mc_node1 = self.nodes[0]

        assert_true(sc_node1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
            "Node 1 submitter expected to be enabled.")

        sc_address_1 = http_wallet_createPrivateKey25519(sc_node1)

        # Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      sc_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        ##### TEST import/export secret ######
        print("##### TEST import/export keys ######")

        # Generate 1 address in sc_node2
        print("# Generate 1 address in sc_node2")

        sc_address_2 = http_wallet_createPrivateKey25519(sc_node2)
 
        # Verify that we have this address inside sc_node2 but not in sc_node1
        print("# Verify that we have this address inside sc_node2 but not in sc_node1")

        pkeys_node1 = http_wallet_allPublicKeys(sc_node1)
        pkeys_node2 = http_wallet_allPublicKeys(sc_node2)

        assert_false(self.findAddress(pkeys_node1, sc_address_2))
        assert_true(self.findAddress(pkeys_node2, sc_address_2))

        # Test authentication on exportSecret endpoint
        print("# Test authentication on exportSecret endpoint")
        exception = False
        try:
            http_wallet_exportSecret(sc_node2, sc_address_2, "fake_api_key")
        except SCAPIException as e:
            exception = True
        assert_true(exception)
        

        # Call the endpoint exportSecret and store the secret of the new address
        print("# Call the endpoint exportSecret and store the secret of the new address")

        sc_secret_2 = http_wallet_exportSecret(sc_node2, sc_address_2, self.API_KEY)    

        # Test authentication on importSecret endpoint
        print("# Test authentication on importSecret endpoint")
        exception = False
        try:
            http_wallet_importSecret(sc_node1, sc_secret_2, "fake_api_key")
        except SCAPIException as e:
            exception = True
        assert_true(exception)

        # Import the secret in the sc_node1 and verify that it owns also the new address
        print("# Import the secret in the sc_node1 and verify that it owns also the new address")

        http_wallet_importSecret(sc_node1, sc_secret_2, self.API_KEY)
        pkeys_node1 = http_wallet_allPublicKeys(sc_node1)
        assert_true(self.findAddress(pkeys_node1, sc_address_2))

        # Send some coins to this address and verify that the balance on sc_node1 is not changed while the sc_node2 has some balance now
        print("# Send some coins to this address and verify that the balance on sc_node1 is not changed while the sc_node2 has some balance now")

        balance_node1 = http_wallet_balance(sc_node1)
        balance_node2 = http_wallet_balance(sc_node2)
        assert_equal(balance_node2, 0)

        sendCoinsToAddress(sc_node1, sc_address_2, 1000, 0)

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        balance_node1_updated = http_wallet_balance(sc_node1)
        balance_node2_updated = http_wallet_balance(sc_node2)
        assert_equal(balance_node1_updated, balance_node1)
        assert_equal(balance_node2_updated, 1000)

        ##### TEST import/dump secrets ######
        print("##### TEST import/dump secrets ######")

        DUMP_PATH = self.options.tmpdir+"/dumpSecrets"

        # Create a couple of new address on node 1
        print("# Create a couple of new address on node 1")

        sc_address_3 = http_wallet_createPrivateKey25519(sc_node1)
        sc_address_4 = http_wallet_createPrivateKey25519(sc_node1)

        # Test authentication on dumpSecrets endpoint
        print("# Test authentication on dumpSecrets endpoint")

        exception = False
        try:
            http_wallet_dumpSecrets(sc_node1, DUMP_PATH, "fake_api_key")
        except SCAPIException as e:
            exception = True
        assert_true(exception)   

        # Test that we dumped all the secrets
        print("# Test that we dumped all the secrets")

        http_wallet_dumpSecrets(sc_node1, DUMP_PATH, self.API_KEY)
        key_list = self.readFile(DUMP_PATH)
        pkeys_node1 = http_wallet_allPublicKeys(sc_node1)
        pkeys_node2 = http_wallet_allPublicKeys(sc_node2)
        assert_equal(len(pkeys_node1), len(key_list))
        assert_equal(len(pkeys_node2), len(key_list) - 4)

        propositions_node1 = list(map(lambda key: key['publicKey'], pkeys_node1))
        propositions_node2 = list(map(lambda key: key['publicKey'], pkeys_node2))

        common_addresses = 0
        for key in key_list:
            assert_true(key[1] in propositions_node1)
            if (key[1] in propositions_node2):
                common_addresses += 1
        assert_equal(common_addresses, len(key_list) - 4)

        # Test authentication on importSecrets endpoint
        print("# Test authentication on importSecrets endpoint")

        exception = False
        try:
            http_wallet_importSecrets(sc_node2, DUMP_PATH, "fake_api_key")
        except SCAPIException as e:
            exception = True
        assert_true(exception)           

        # Test that we imported in the sc_node2 only the 4 missing keys
        print("# Test that we imported in the sc_node2 only the 4 missing keys")

        result = http_wallet_importSecrets(sc_node2, DUMP_PATH, self.API_KEY)
        assert_equal(result["successfullyAdded"], 4)
        assert_equal(result["failedToAdd"], common_addresses)
        assert_equal(len(result["summary"]), common_addresses)

        for error in result["summary"]:
            assert_true("requirement failed: Key already exists" in error["description"])

        pkeys_node2 = http_wallet_allPublicKeys(sc_node2)
        assert_equal(len(pkeys_node2), len(pkeys_node1))
        propositions_node2 = list(map(lambda key: key['publicKey'], pkeys_node2))

        for proposition in propositions_node2:
            assert_true(proposition in propositions_node1)

        assert_true(sc_address_3 in propositions_node2)
        assert_true(sc_address_4 in propositions_node2)


if __name__ == "__main__":
    SidechainImportExportKeysTest().main()
