#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_true, initialize_chain_clean, start_nodes, connect_nodes_bi, websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import generate_secrets, start_sc_nodes, generate_next_blocks, bootstrap_sidechain_nodes, generate_secrets, generate_vrf_secrets
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from httpCalls.transaction.makeForgerStake import makeForgerStake
from httpCalls.wallet.createVrfSecret import http_wallet_createVrfSecret
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SCForgerConfiguration
"""
    Setup 1 SC Node with a closed list of forger. Try to stake money with invalid forger info and verify that we are not allowed to stake.
"""
class SidechainClosedForgerTest(SidechainTestFramework):
    number_of_mc_nodes = 3
    number_of_sidechain_nodes = 1
    allowed_forger_proposition = generate_secrets("seed", 1)[0].publicKey
    allowed_forger_vrf_public_key = generate_vrf_secrets("seed", 1)[0].publicKey

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
        forger_configuration  = SCForgerConfiguration(True, [
            [self.allowed_forger_proposition, self.allowed_forger_vrf_public_key]
        ])

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            forger_options = forger_configuration
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000),
                                         sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

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
                                      self.allowed_forger_proposition,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node1.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()

        # Try to stake to an invalid blockSignProposition
        print("Try to stake to an invalid blockSignProposition...")
        new_public_key = http_wallet_createPrivateKey25519(self.sc_nodes[0])
        new_vrf_public_key = http_wallet_createVrfSecret(sc_node1)
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_proposition, new_public_key, self.allowed_forger_vrf_public_key, forger_amount, sc_fee)
        print(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        print("Ok!")

        # Try to stake to an invalid vrfPublicKey
        print("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_proposition, self.allowed_forger_proposition, new_vrf_public_key, forger_amount, sc_fee)
        print(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        print("Ok!")

        # Try to stake with an invalid blockSignProposition and an invalid vrfPublicKey
        print("Try to stake to an invalid vrfPublicKey...")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_proposition, new_public_key, new_vrf_public_key, forger_amount, sc_fee)
        print(result)
        assert_true('error' in result)
        assert_true('This publicKey is not allowed to forge' in result['error']['detail'])
        print("Ok!")

        # Try to stake with a valid blockSignProposition and a valid vrfPublickey
        print("Try to stake with a valid blockSignProposition and a valid vrfPublickey")
        result = makeForgerStake(self.sc_nodes[0], self.allowed_forger_proposition, self.allowed_forger_proposition, self.allowed_forger_vrf_public_key, forger_amount, sc_fee)
        print(result)
        assert_true('result' in result)
        assert_true('transactionId' in result['result'])

        self.sc_sync_all()
        generate_next_blocks(sc_node1, "first node", 1)
        self.sc_sync_all()


if __name__ == "__main__":
    SidechainClosedForgerTest().main()
