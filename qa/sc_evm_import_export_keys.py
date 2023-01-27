#!/usr/bin/env python3
import json
import logging
import pprint
import shutil

from eth_utils import add_0x_prefix
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCNetworkConfiguration, \
    SCCreationInfo, SC_CREATION_VERSION_1
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.authproxy import AuthServiceProxy
from test_framework.util import assert_equal, assert_false, assert_true, forward_transfer_to_sidechain, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import generate_next_blocks, bootstrap_sidechain_nodes, AccountModel, \
    EVM_APP_BINARY, start_sc_node, wait_for_sc_node_initialization
from httpCalls.wallet.exportSecret import http_wallet_exportSecret
from httpCalls.wallet.allPublicKeys import http_wallet_allPublicKeys
from httpCalls.wallet.importSecret import http_wallet_importSecret
from httpCalls.wallet.dumpSecrets import http_wallet_dumpSecrets
from httpCalls.wallet.importSecrets import http_wallet_importSecrets
from SidechainTestFramework.sidechainauthproxy import SCAPIException

"""
    (Created from analogous in UTXO model)
    - Setup 2 SC Node.
    - Create a new address on node2
    - Export its private key and import it inside the node1
    - Verify that node1 owns this address
    - Send coins from node1 to this address and verify that the balance on node1 is unchanged and node2 owns the new
      amount
    
    - Create some new addresses on node1 and dump all its secret on file
    - Import the node1 secrets inside the node2 and verify that we imported only the secrets that were missing
"""


class EvmSidechainImportExportKeysTest(AccountChainSetup):

    def __init__(self):
        super().__init__(
            number_of_mc_nodes=1,
            withdrawalEpochLength=10,
            number_of_sidechain_nodes=2)

    API_KEY_NODE1 = "aaaa"
    API_KEY_NODE2 = "Horizen2"

    # this is overridden for using two different AUTH keys

    def sc_setup_nodes(self):
        dirname = self.options.tmpdir
        if self.debug_extra_args is None:
            extra_args = [None for _ in range(self.number_of_sidechain_nodes)]
        else:
            extra_args = self.debug_extra_args

        nodes = [
            start_sc_node(0, dirname, extra_args[0], binary=EVM_APP_BINARY, auth_api_key=self.API_KEY_NODE1),
            start_sc_node(1, dirname, extra_args[1], binary=EVM_APP_BINARY, auth_api_key=self.API_KEY_NODE2)
        ]

        wait_for_sc_node_initialization(nodes)
        return nodes

    # this is overridden for using two different AUTH keys
    def sc_setup_chain(self):
        mc_node: AuthServiceProxy = self.nodes[0]
        sc_node_configuration = [
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY_NODE1,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled),
            SCNodeConfiguration(
                MCConnectionInfo(
                    address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                api_key=self.API_KEY_NODE2,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled)
        ]

        circuit_type = self.options.certcircuittype
        sc_creation_version = SC_CREATION_VERSION_1

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, self.forward_amount, self.withdrawalEpochLength,
                                                        sc_creation_version=sc_creation_version,
                                                        is_non_ceasing=self.options.nonceasing,
                                                        circuit_type=circuit_type),
                                         *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=self.block_timestamp_rewind,
                                                                 model=AccountModel)

    def find_address(self, propositions, address):
        for proposition in propositions:
            if 'address' in proposition:
                if proposition['address'] == address:
                    return True
        return False

    def read_file(self, file_path):
        f = open(file_path, "r")
        key_list = []
        for line in f:
            if "#" not in line:
                row = line.split(" ")
                key_list += [(row[0], row[1][:-1])]
        return key_list

    def run_test(self):
        self.sync_all()
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        # connect_sc_nodes(sc_node1, 1)  # Connect SC nodes
        mc_node1 = self.nodes[0]

        assert_true(sc_node1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")

        evm_address_1 = sc_node1.wallet_createPrivateKeySecp256k1({}, self.API_KEY_NODE1)["result"]["proposition"][
            "address"]

        # Generate 1 SC block
        generate_next_blocks(sc_node1, "first node", 1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node1,
                                      evm_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        ##### TEST import/export secret ######
        logging.info("##### TEST import/export keys ######")

        # Generate 1 address in sc_node2
        logging.info("# Generate 1 address in sc_node2")

        evm_address_2 = sc_node2.wallet_createPrivateKeySecp256k1({}, self.API_KEY_NODE2)["result"]["proposition"][
            "address"]

        # Verify that we have this address inside sc_node2 but not in sc_node1
        logging.info("# Verify that we have this address inside sc_node2 but not in sc_node1")

        pkeys_node1 = http_wallet_allPublicKeys(sc_node1, self.API_KEY_NODE1)
        pkeys_node2 = http_wallet_allPublicKeys(sc_node2, self.API_KEY_NODE2)

        assert_false(self.find_address(pkeys_node1, evm_address_2))
        assert_true(self.find_address(pkeys_node2, evm_address_2))

        # Test authentication on exportSecret endpoint
        logging.info("# Test authentication on exportSecret endpoint")
        exception = False
        try:
            http_wallet_exportSecret(sc_node2, evm_address_2, "fake_api_key")
        except SCAPIException:
            exception = True
        assert_true(exception)

        # Call the endpoint exportSecret and store the secret of the new address
        logging.info("# Call the endpoint exportSecret and store the secret of the new address")

        sc_secret_2 = http_wallet_exportSecret(sc_node2, evm_address_2, self.API_KEY_NODE2)

        # Test authentication on importSecret endpoint
        logging.info("# Test authentication on importSecret endpoint")
        exception = False
        try:
            http_wallet_importSecret(sc_node1, sc_secret_2, "fake_api_key")
        except SCAPIException:
            exception = True
        assert_true(exception)

        # Import the secret in the sc_node1 and verify that it owns also the new address
        logging.info("# Import the secret in the sc_node1 and verify that it owns also the new address")

        http_wallet_importSecret(sc_node1, sc_secret_2, self.API_KEY_NODE1)
        pkeys_node1 = http_wallet_allPublicKeys(sc_node1, self.API_KEY_NODE1)
        assert_true(self.find_address(pkeys_node1, evm_address_2))

        # Send some coins to this address and verify that the balance on sc_node1 is not changed while the sc_node2 has
        # some balance now
        logging.info(
            "# Send some coins to this address and verify that the balance on sc_node1 is not changed while the "
            "sc_node2 has some balance now")

        balance_node1 = sc_node1.wallet_getTotalBalance(json.dumps({}), self.API_KEY_NODE1)
        balance_node2 = sc_node2.wallet_getTotalBalance(json.dumps({}), self.API_KEY_NODE2)
        assert_equal(balance_node2['result']['balance'], 0)

        # sendCoinsToAddress(sc_node1, sc_address_2, 1000, 0, self.API_KEY_NODE1)
        tx_hash = createLegacyTransaction(sc_node1,
                                          fromAddress=evm_address_1,
                                          toAddress=evm_address_2,
                                          value=convertZenToWei(99),
                                          api_key=self.API_KEY_NODE1
                                          )

        self.sc_sync_all()

        # get mempool contents and check contents are as expected
        response = allTransactions(sc_node1, False)
        assert_true(tx_hash in response['transactionIds'])

        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # check receipt, meanwhile do some check on amounts
        receipt = sc_node1.rpc_eth_getTransactionReceipt(add_0x_prefix(tx_hash))

        status = int(receipt['result']['status'], 16)
        fee = int(receipt['result']['gasUsed'][2:], 16) * int(receipt['result']['effectiveGasPrice'][2:], 16)

        pprint.pprint(receipt)
        print("Gas used: {}".format(fee))
        assert_equal(status, 1)

        balance_node1_updated = sc_node1.wallet_getTotalBalance(json.dumps({}), self.API_KEY_NODE1)
        balance_node2_updated = sc_node2.wallet_getTotalBalance(json.dumps({}), self.API_KEY_NODE2)
        assert_equal(balance_node1_updated['result']['balance'] + fee, balance_node1['result']['balance'])
        assert_equal(balance_node2_updated['result']['balance'], convertZenToWei(99))

        ##### TEST import/dump secrets ######
        logging.info("##### TEST import/dump secrets ######")

        DUMP_PATH = self.options.tmpdir + "/dumpSecrets"
        DUMP_PATH_CORRUPTED = self.options.tmpdir + "/dumpSecretsCorrupted"

        # Create a couple of new address on node 1
        logging.info("# Create a couple of new address on node 1")

        evm_address_3 = sc_node1.wallet_createPrivateKeySecp256k1({}, self.API_KEY_NODE1)["result"]["proposition"][
            "address"]
        evm_address_4 = sc_node1.wallet_createPrivateKeySecp256k1({}, self.API_KEY_NODE1)["result"]["proposition"][
            "address"]

        # Test authentication on dumpSecrets endpoint
        logging.info("# Test authentication on dumpSecrets endpoint")

        exception = False
        try:
            http_wallet_dumpSecrets(sc_node1, DUMP_PATH, "fake_api_key")
        except SCAPIException:
            exception = True
        assert_true(exception)

        # Test that we dumped all the secrets
        logging.info("# Test that we dumped all the secrets")

        http_wallet_dumpSecrets(sc_node1, DUMP_PATH, self.API_KEY_NODE1)
        key_list = self.read_file(DUMP_PATH)
        pkeys_node1 = http_wallet_allPublicKeys(sc_node1, self.API_KEY_NODE1)
        pkeys_node2 = http_wallet_allPublicKeys(sc_node2, self.API_KEY_NODE2)
        assert_equal(len(pkeys_node1), len(key_list))
        # genesis account is made of 2 key in account model, one secp256k and one 25519
        # and we generated 3 new secp256k on node1, therefore 2+3=5
        assert_equal(len(pkeys_node2), len(key_list) - 5)

        propositions_node1 = [
            (
                    ('publicKey' in sub and sub['publicKey']) or
                    ('address' in sub and sub['address'])
            ) for sub in pkeys_node1
        ]
        propositions_node2 = [
            (
                    ('publicKey' in sub and sub['publicKey']) or
                    ('address' in sub and sub['address'])
            ) for sub in pkeys_node2
        ]

        common_addresses = 0
        for key in key_list:
            assert_true(key[1] in propositions_node1)
            if key[1] in propositions_node2:
                common_addresses += 1
        assert_equal(common_addresses, len(key_list) - 5)

        # Test authentication on importSecrets endpoint
        logging.info("# Test authentication on importSecrets endpoint")

        exception = False
        try:
            http_wallet_importSecrets(sc_node2, DUMP_PATH, "fake_api_key")
        except SCAPIException:
            exception = True
        assert_true(exception)

        # Test that we stop the execution of importSecrets if the file is corrupted.
        logging.info("# Test that we stop the execution of importSecrets if the file is corrupted.")
        shutil.copyfile(DUMP_PATH, DUMP_PATH_CORRUPTED)
        f = open(DUMP_PATH_CORRUPTED, "a")
        f.write("Corrupted_line C_\n")
        f.close()

        exception = False
        try:
            http_wallet_importSecrets(sc_node2, DUMP_PATH_CORRUPTED, self.API_KEY_NODE2)
        except SCAPIException:
            exception = True
        assert_true(exception)

        # Test that we imported in the sc_node2 only the 5 missing keys
        logging.info("# Test that we imported in the sc_node2 only the 5 missing keys")

        result = http_wallet_importSecrets(sc_node2, DUMP_PATH, self.API_KEY_NODE2)
        assert_equal(result["successfullyAdded"], 5)
        assert_equal(result["failedToAdd"], common_addresses)
        assert_equal(len(result["summary"]), common_addresses)

        for error in result["summary"]:
            assert_true("requirement failed: Key already exists" in error["description"])

        pkeys_node2 = http_wallet_allPublicKeys(sc_node2, self.API_KEY_NODE2)
        assert_equal(len(pkeys_node2), len(pkeys_node1))
        propositions_node2 = [
            (
                    ('publicKey' in sub and sub['publicKey']) or
                    ('address' in sub and sub['address'])
            ) for sub in pkeys_node2
        ]
        for proposition in propositions_node2:
            assert_true(proposition in propositions_node1)

        assert_true(evm_address_3 in propositions_node2)
        assert_true(evm_address_4 in propositions_node2)


if __name__ == "__main__":
    EvmSidechainImportExportKeysTest().main()
