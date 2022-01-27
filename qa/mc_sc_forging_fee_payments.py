#!/usr/bin/env python3

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account
from test_framework.util import assert_equal, assert_true, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    connect_sc_nodes, check_wallet_coins_balance, check_box_balance, generate_next_block
from SidechainTestFramework.sc_forging_util import *

import math

"""
Info about forger block fee payments
The JSON representation is only for documentation.

BlockFeeInfo: {
    "node": n
    "fee": n
}
"""
class BlockFeeInfo(object):
    def __init__(self, node, fee):
        self.node = node
        self.fee = fee


"""
Check Forger fee payments:
1. Forging using stakes of different SC nodes
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC nodes are connected to the MC node.
Test:
    - Do FT to the second SC node.
    - Sync SC and MC networks.
    - Delegate coins to forge to the second SC node using coins from the FT.
    - Forge SC block by the first SC node for the next consensus epoch.
    - Forge SC block by the second SC node for the next consensus epoch (Second node ForgingStake must become active).
    - Generate MC and SC blocks to reach the end of the withdrawal epoch. 
    - Check forger payments for the SC nodes.
"""


class MCSCForgingFeePayments(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    withdrawal_epoch_length = 5

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()

    def setup_nodes(self):
        # Start MC node
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC nodes connection to MC node
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 500, self.withdrawal_epoch_length),
                                         sc_node_1_configuration, sc_node_2_configuration)

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        # Start 2 SC nodes
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Connect and sync SC nodes
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()
        # Set the genesis SC block fee info
        sc_block_fee_info = [BlockFeeInfo(1, 0)]


        # Do FT of 500 Zen to SC Node 2
        sc_node2_address = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_node2_account = Account("", sc_node2_address)
        ft_amount = 500  # Zen
        mc_return_address = mc_node.getnewaddress()
        ft_args = [{
            "toaddress": sc_node2_address,
            "amount": ft_amount,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }]
        mc_node.sc_send(ft_args)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block and check that FT appears in SC node wallet
        mcblock_hash1 = mc_node.generate(1)[0]
        scblock_id1 = generate_next_block(sc_node1, "first node")
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node1)
        # Update block fees: node 1 generated block with 0 fees.
        sc_block_fee_info.append(BlockFeeInfo(1, 0))

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
            print("Forget stake created: " + json.dumps(makeForgerStakeJsonRes))

        self.sc_sync_all()

        # Generate SC block
        generate_next_block(sc_node1, "first node")
        sc_block_fee_info.append(BlockFeeInfo(1, fee))

        self.sc_sync_all()

        # Check SC node 2 ForgerBoxes
        check_box_balance(sc_node2, sc_node2_account, "ForgerBox", 1, forgerStake_amount)

        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)
        sc_block_fee_info.append(BlockFeeInfo(1, 0))

        self.sc_sync_all()

        fee = 200
        sendCoins = {
            "outputs": [
                {
                    "publicKey": sc_node2_address,
                    "value": 100000
                }
            ],
            "fee": fee,
        }
        jsonRes = sc_node2.transaction_sendCoinsToAddress(json.dumps(sendCoins))
        if "result" not in jsonRes:
            fail("send coins tx failed: " + json.dumps(jsonRes))
        else:
            print("send coins tx created: " + json.dumps(jsonRes))

        # Generate SC block on SC node 2 for the next consensus epoch
        generate_next_block(sc_node2, "second node", force_switch_to_next_epoch=True)
        sc_block_fee_info.append(BlockFeeInfo(2, fee))

        self.sc_sync_all()

        # Generate 3 MC block to reach the end of the withdrawal epoch
        mc_node.generate(3)

        # Collect SC node balances before fees redistribution
        sc_node1_balance_before_payments = int(sc_node1.wallet_coinsBalance()["result"]["balance"])
        sc_node2_balance_before_payments = int(sc_node2.wallet_coinsBalance()["result"]["balance"])

        # Generate one more block with no fee by SC node 2 to reach the end of the withdrawal epoch
        generate_next_block(sc_node2, "second node")
        sc_block_fee_info.append(BlockFeeInfo(2, 0))

        self.sc_sync_all()

        # Collect fee values
        total_fee = 0
        pool_fee = 0.0
        forger_fees = {}
        for sc_block_fee in sc_block_fee_info:
            total_fee += sc_block_fee.fee
            pool_fee += math.ceil(sc_block_fee.fee * 0.3)

        for idx, sc_block_fee in enumerate(sc_block_fee_info):
            if sc_block_fee.node in forger_fees:
                forger_fees[sc_block_fee.node] += math.floor(sc_block_fee.fee * 0.7)
            else:
                forger_fees[sc_block_fee.node] = math.floor(sc_block_fee.fee * 0.7)

            forger_fees[sc_block_fee.node] += pool_fee / len(sc_block_fee_info)

            if idx < pool_fee % len(sc_block_fee_info):
                forger_fees[sc_block_fee.node] += 1

        sc_node1_balance_after_payments = int(sc_node1.wallet_coinsBalance()["result"]["balance"])
        sc_node2_balance_after_payments = int(sc_node2.wallet_coinsBalance()["result"]["balance"])

        node_1_fees = forger_fees[1]
        node_2_fees = forger_fees[2]

        # Check forger fee payments
        assert_equal(sc_node1_balance_after_payments, sc_node1_balance_before_payments + node_1_fees,
                     "Wrong fee payment amount for SC node 1")
        assert_equal(sc_node2_balance_after_payments, sc_node2_balance_before_payments + node_2_fees,
                     "Wrong fee payment amount for SC node 2")




if __name__ == "__main__":
    MCSCForgingFeePayments().main()