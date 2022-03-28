#!/usr/bin/env python3
import json

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, generate_next_block, generate_next_blocks

"""
Check that in the SC block, that contains MC block ref leading to the withdrawal epoch end, we allow to have MC2SCAggTx,
but not SC2SC transactions.

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Test:
    For the SC node:
        - Mine MC blocks till one block before the withdrawal epoch end.
        - Forge SC blocks to sync with MC.
        - Create new forward transfer to sidechain.
        - Mine MC block that will have a FT.
        - Generate CoreTx on SC node.
        - Try to generate SC block that will end with MC block ref with AggTx, but without Sc2Sc tx.
        - Check that Sc2Sc tx is still in the mempool.
"""
class SCWithdrawalEpochLastBlock(SidechainTestFramework):
    sc_nodes_bootstrap_info=None
    withdrawal_epoch_length=5

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.withdrawal_epoch_length), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        withdrawal_epoch_blocks_left = self.withdrawal_epoch_length - 1

        # Send some coins to SC node wallet.
        mc_return_address = mc_node.getnewaddress()
        (sc_info, mc_block_count) = forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                                                  mc_node,
                                                                  self.sc_nodes_bootstrap_info.genesis_account.publicKey,
                                                                  self.sc_nodes_bootstrap_info.genesis_account_balance,
                                                                  mc_return_address,
                                                                  generate_block=True)

        generate_next_blocks(sc_node, "first node", 1)
        withdrawal_epoch_blocks_left -= 1

        # Check the MC block reference's inclusion
        sc_best_block = sc_node.block_best()["result"]
        mc_block = self.nodes[0].getblock(str(mc_block_count))

        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        # Generate more MC blocks to reach 1 block before the end of the withdrawal epoch
        mc_node.generate(withdrawal_epoch_blocks_left - 1)

        # Generate SC block
        generate_next_blocks(sc_node, "first node", 1)

        # Send 1 more FT in the withdrawal epoch last MC block.
        (sc_info, mc_block_count) = forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                                                  mc_node,
                                                                  self.sc_nodes_bootstrap_info.genesis_account.publicKey,
                                                                  self.sc_nodes_bootstrap_info.genesis_account_balance,
                                                                  mc_return_address,
                                                                  generate_block=True)

        # Create SC to SC tx on SC node
        fee = 10
        sendCoins = {
            "outputs": [
                {
                    "publicKey": self.sc_nodes_bootstrap_info.genesis_account.publicKey,
                    "value": self.sc_nodes_bootstrap_info.genesis_account_balance - fee
                }
            ],
            "fee": fee,
        }
        sc_node.transaction_sendCoinsToAddress(json.dumps(sendCoins))

        # Check mempool
        assert_equal(1, len(sc_node.transaction_allTransactions()["result"]["transactions"]),
                     "FT spending Tx expected to be in the SC node mempool.")

        # Generate 1 more SC block and check that we have 1 MC block ref with AggTx and no SC2SCTx
        generate_next_block(sc_node, "first node")
        sc_best_block = sc_node.block_best()["result"]

        # Check that there are no SC2SC txs
        assert_equal(0, len(sc_best_block["block"]["sidechainTransactions"]), "No sidechain transactions expected.")

        # Check the MC block reference's inclusion
        mc_block = self.nodes[0].getblock(str(mc_block_count))

        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        # Check mempool if SC 2 SC tx is still present
        assert_equal(1, len(sc_node.transaction_allTransactions()["result"]["transactions"]),
                     "FT spending Tx expected to be in the SC node mempool.")


if __name__ == "__main__":
    SCWithdrawalEpochLastBlock().main()
