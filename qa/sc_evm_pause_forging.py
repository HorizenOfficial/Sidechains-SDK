#!/usr/bin/env python3
import json
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.utils import convertZenToZennies, VERSION_1_3_FORK_EPOCH
from SidechainTestFramework.scutil import generate_next_blocks, start_sc_nodes, EVM_APP_BINARY, generate_next_block, \
    SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME
from test_framework.util import assert_equal, assert_true, fail, forward_transfer_to_sidechain, assert_not_equal

"""
Configuration: 
    - 2 SC nodes connected with each other
      - Node 1 has an optional parameter specifying 101 as max number of revertable sc blocks 
      - Node 2 has default value = 100
    - 1 MC node

Test:
    - Check that before fork 1.3 activation both nodes can forge and sync more than 100 sc blocks without any mc block 
      references included in the sc blocks
      
    - Reach the fork 1.3 and test that:
        Node 2 can forge up to 99 blocks without mc block references but stops forging at the 100th
        Node 1 can forge it, but Node 2 refuse to apply to its state such block and the network is split
        When one more MC block is generated, Node 2 resume forging, and after 2 SC blocks the illegal block on Node 1 is
        reverted and the network is synced back
   
"""



class SCEvmPauseForging(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 100)

    def sc_setup_nodes(self):
        # Start 2 SC nodes
        if self.debug_extra_args is not None:
            arg1 = self.debug_extra_args[0]
            arg2 = self.debug_extra_args[1]
        else:
            arg1 = ['']
            arg2 = ['']

        return start_sc_nodes(
            self.number_of_sidechain_nodes,
            self.options.tmpdir, extra_args=[['-max_hist_rew_len', str(101)] + arg1, arg2],
            binary=[EVM_APP_BINARY] * 2, auth_api_key=self.API_KEY)

    def run_test(self):

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        mc_node = self.nodes[0]

        # Do FT of some Zen to SC Node 2
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = 100

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)
        self.sync_all()

        # Generate MC block and SC block
        mc_node.generate(1)
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # Create forger stake with some Zen for SC node 2
        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        forger_stake_amount = ft_amount_in_zen - 0.01 # for gas usage

        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_2, evm_address_sc_node_2, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey,
                                                    convertZenToZennies(forger_stake_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        # Generate SC block
        generate_next_block(sc_node_1, "first node")

        # we now have 2 stakes, one from creation and one just added
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(2, len(stakeList))

        # Generate SC blocks on SC node 1 for switching 2 consensus epochs
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        # mc_node.generate(1)
        self.sc_sync_all()

        # verify that before the fork point we can forge more than 100 sc blocks without including a mc block ref
        NUM_OF_BLOCKS = 102

        for i in range(NUM_OF_BLOCKS):
            generate_next_block(sc_node_1, "second node")
            self.sc_sync_all()

        # reach the v1.3 fork
        current_best_epoch = sc_node_1.block_forgingInfo()["result"]["bestBlockEpochNumber"]
        for i in range(0, VERSION_1_3_FORK_EPOCH - current_best_epoch):
            try:
                generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
                self.sc_sync_all()
            except AssertionError as msg:
                # if the block to be forged is on the epoch that trigger the forks, we are already in the condition of
                # having more than 100 sc blocks without mc block refs
                logging.error("Assertion failed: " + str(msg))
                assert_true("No mc refs in a long row of blocks error" in str(msg))
                assert_true(sc_node_1.block_forgingInfo()["result"]["bestBlockEpochNumber"], VERSION_1_3_FORK_EPOCH)

        mc_block = mc_node.generate(1)[-1]
        self.sc_sync_all()

        # the first of these blocks will include the mc block ref, other 99 blocks will not have any
        NUM_OF_BLOCKS = 100
        block_seq = []
        for i in range(NUM_OF_BLOCKS):
            sc_block = generate_next_block(sc_node_2, "second node")
            block_seq.append(sc_block)
            self.sc_sync_all()
            if i == 0:
                # this block has a reference with the latest MC block
                assert_equal(1, len(sc_node_1.block_best()["result"]['block']['mainchainHeaders']))
                sc_mc_best_block_ref_info = sc_node_1.mainchain_bestBlockReferenceInfo()["result"]
                sc_block_referencing = sc_mc_best_block_ref_info['blockReferenceInfo']['mainchainHeaderSidechainBlockId']
                mc_block_referenced = sc_mc_best_block_ref_info['blockReferenceInfo']['hash']
                assert_equal(sc_block, sc_block_referencing)
                assert_equal(mc_block, mc_block_referenced)
            else:
                # all other blocks don't have refs
                assert_equal(0, len(sc_node_1.block_best()["result"]['block']['mainchainHeaders']))

        # node 2 can not forge until we have one more mc block, since default value for maxHistoryRewriteLen=100
        try:
           generate_next_block(sc_node_2, "second node")
           raise RuntimeError("Should not be able to forge here!!!")
        except AssertionError as msg:
            logging.error("Assertion failed: " + str(msg))
            assert_true("No mc refs in a long row of blocks error" in str(msg))

        # node 1 can forge because we set maxHistoryRewriteLen = 101
        generate_next_block(sc_node_1, "first node")

        # tips are now different, node 2 refused to connect that block
        node1_best_block = sc_node_1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node_2.rpc_eth_getBlockByNumber("latest", "true")
        assert_not_equal(node1_best_block, node2_best_block)
        assert_equal(node1_best_block['result']['parentHash'], node2_best_block['result']['hash'])
        # get illegal node 1 tip
        node1_bl_height = node1_best_block['result']['number']
        node1_bl_hash_pre_sync = sc_node_1.rpc_eth_getBlockByNumber(node1_bl_height, False)['result']['hash']

        # generate one more MC block and let node 2 generate other 2 blocks
        mc_node.generate(1)

        block_ids = generate_next_blocks(sc_node_2, "second node", 2)
        self.sc_sync_all()

        # verify the node 1 has really been synced, reverting its illegal block tip
        node1_bl_hash_after_sync = sc_node_1.rpc_eth_getBlockByNumber(node1_bl_height, False)['result']['hash']
        assert_not_equal(node1_bl_hash_pre_sync, node1_bl_hash_after_sync)
        node1_best_block = sc_node_1.rpc_eth_getBlockByNumber("latest", "true")
        node2_best_block = sc_node_2.rpc_eth_getBlockByNumber("latest", "true")
        assert_equal(node1_best_block, node2_best_block)
        assert_equal(node1_best_block['result']['hash'], '0x'+(block_ids[1]))
        assert_equal(node1_best_block['result']['parentHash'], '0x'+(block_ids[0]))


if __name__ == "__main__":
    SCEvmPauseForging().main()

