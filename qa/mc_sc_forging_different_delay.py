#!/usr/bin/env python3

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, disconnect_nodes_bi
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_blocks, \
    connect_sc_nodes, disconnect_sc_nodes, generate_next_block, check_box_balance, check_wallet_coins_balance
from SidechainTestFramework.sc_forging_util import *

"""
Check node interaction with different MC Block Reference delay:

Configuration:
    Start 2 MC nodes and 2 SC node (with default websocket configuration).
    First SC delay is 1 block.
    Second SC delay is 0 block

Test:
    - Forge SC block, verify that there is no MC Headers and Data.
    - Delegate coins to forge to the second SC node via 2 ForgerBoxes from the first SC node.
    - Forge SC block by the first SC node for the next consensus epoch.
    - Forge SC block by the second SC node for the next consensus epoch. (ForgerBoxes must become active for this epoch).
    - Disconnect SC nodes.
    - Generate 2 MC blocks. First SC node should include 1 MC block reference. 
    - Synchronize SC nodes.
    - Disconnect SC nodes.
    - Generate 2 MC blocks. Second SC node should include 3 MC headers and 3 MC block reference.
    - Synchronize SC nodes.
"""


class MCSCForgingDifferentDelay(SidechainTestFramework):
    number_of_mc_nodes = 2
    number_of_sidechain_nodes = 2

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()

    def setup_nodes(self):
        # Start 2 MC nodes
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC node 1 connection to MC node 1
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))), False)

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration, sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir, extra_args=[['-mc_block_delay_ref', '1'], []])

    def run_test(self):
        mc_node1 = self.nodes[0]
        mc_node2 = self.nodes[1]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Synchronize mc_node1 and mc_node2
        self.sync_all()

        # genesis_sc_block_id = sc_node1.block_best()["result"]

        # Do FT of 500 Zen to SC Node 2
        sc_node2_address = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_node2_account = Account("", sc_node2_address)
        ft_amount = 500  # Zen
        mc_return_address = mc_node1.getnewaddress()
        ft_args = [{
            "toaddress": sc_node2_address,
            "amount": ft_amount,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }]
        mc_node1.sc_send(ft_args)
        assert_equal(1, mc_node1.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block and check that FT appears in SC node wallet
        mcblock_hash1 = mc_node1.generate(2)[0]
        scblock_id0 = generate_next_block(sc_node1, "first node")
        check_mcreference_presence(mcblock_hash1, scblock_id0, sc_node1)
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        # check all keys/boxes/balances are coherent with the default initialization
        check_wallet_coins_balance(sc_node2, ft_amount)
        check_box_balance(sc_node2, sc_node2_account, "ZenBox", 1, ft_amount)

        # Create forger stake with 499 Zen for SC node 2
        sc_node2_rewards_address = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_node2_vrf_address = sc_node2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        forgerStake_amount = 499 # Zen
        fee = 1000 # Satoshi
        forgerStakes = {
            "outputs": [
                {
                    "publicKey": sc_node2_address, # SC node 2 is an owner
                    "blockSignPublicKey": sc_node2_rewards_address,  # SC node 2 is a block signer
                    "vrfPubKey": sc_node2_vrf_address,
                    "value": forgerStake_amount * 100000000  # in Satoshi
                }
            ],
            "fee": fee
        }
        makeForgerStakeJsonRes = sc_node2.transaction_makeForgerStake(json.dumps(forgerStakes))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forget stake created: " + json.dumps(makeForgerStakeJsonRes))

        self.sc_sync_all()
        generate_next_block(sc_node1, "first node")
        # generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)
        generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()
        generate_next_block(sc_node2, "second node", force_switch_to_next_epoch=True)
        # Check SC node 2 ForgerBoxes
        check_box_balance(sc_node2, sc_node2_account, "ForgerBox", 1, forgerStake_amount)

        disconnect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        # Generate 1 SC block without any MC block info
        scblock_id0 = generate_next_blocks(sc_node1, "first node", 1)[0]
        # Verify that SC block has no MC headers, ref data, ommers
        check_mcheaders_amount(0, scblock_id0, sc_node1)
        check_mcreferencedata_amount(0, scblock_id0, sc_node1)
        check_ommers_amount(0, scblock_id0, sc_node1)

        # Generate 1 MC block on the first MC node
        mcblock_hash1 = mc_node1.generate(1)[0]
        # Synchronize mc_node1 and mc_node2, then disconnect them.
        self.sync_all()
        disconnect_nodes_bi(self.nodes, 0, 1)

        # Generate 1 more MC block on the first MC node
        mcblock_hash2 = mc_node1.generate(1)[0]

        # Generate 1 SC block, that should put 2 MC blocks inside
        # SC block contains MC `mcblock_hash1` that is common for MC Nodes 1,2 and `mcblock_hash2` that is known only by MC Node 1.
        scblock_id1 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_scparent(scblock_id0, scblock_id1, sc_node1)
        # Verify that SC block contains MC block as a MainchainReference
        check_mcheaders_amount(1, scblock_id1, sc_node1)
        check_mcreferencedata_amount(1, scblock_id1, sc_node1)
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node1)
        check_ommers_amount(0, scblock_id1, sc_node1)

        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()
        check_mcheaders_amount(1, scblock_id1, sc_node2)
        check_mcreferencedata_amount(1, scblock_id1, sc_node2)
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node2)
        check_ommers_amount(0, scblock_id1, sc_node2)
        disconnect_sc_nodes(self.sc_nodes[0], 1)

        # Generate another 2 MC blocks on the second MC node
        mcblock_hash3 = mc_node1.generate(1)[0]
        mcblock_hash4 = mc_node1.generate(1)[0]

        scblock_id2 = generate_next_blocks(sc_node2, "second node", 1)[0]
        check_scparent(scblock_id1, scblock_id2, sc_node2)
        # Verify that SC block contains MC block as a MainchainReference
        check_mcheaders_amount(3, scblock_id2, sc_node2)
        check_mcreferencedata_amount(3, scblock_id2, sc_node2)
        check_mcreference_presence(mcblock_hash2, scblock_id2, sc_node2)
        check_mcreference_presence(mcblock_hash3, scblock_id2, sc_node2)
        check_mcreference_presence(mcblock_hash4, scblock_id2, sc_node2)
        check_ommers_amount(0, scblock_id2, sc_node2)

        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        # Verify that SC block contains MC block as a MainchainReference
        check_mcheaders_amount(3, scblock_id2, sc_node1)
        check_mcreferencedata_amount(3, scblock_id2, sc_node1)
        check_mcreference_presence(mcblock_hash2, scblock_id2, sc_node1)
        check_mcreference_presence(mcblock_hash3, scblock_id2, sc_node1)
        check_mcreference_presence(mcblock_hash4, scblock_id2, sc_node1)
        check_ommers_amount(0, scblock_id2, sc_node1)

if __name__ == "__main__":
    MCSCForgingDifferentDelay().main()