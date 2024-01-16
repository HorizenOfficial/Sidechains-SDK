#!/usr/bin/env python3
import logging
import pprint
import time
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.sc_boostrap_info import DEFAULT_API_KEY
from SidechainTestFramework.scutil import is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, \
    generate_next_blocks, start_sc_nodes, EVM_APP_BINARY
from httpCalls.wallet.allPublicKeys import http_wallet_allPublicKeys
from httpCalls.wallet.createPrivateKeySecp256k1 import http_wallet_createPrivateKeySec256k1
from test_framework.util import assert_equal, assert_true, fail

"""
Check the EVM bootstrap feature.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the SC node:
        - verify the MC block is included
"""
MC_BLOCK_DELAY = 1
addresses = []
outputs_in_ft = 800


class SCEvmBootstrap(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def generate_big(self, sc_node):

        mc_node = self.nodes[0]
        # create huge FTs for filling up the MC ref data. This constant allows us to have tx with size ~90K, a little
        # below the 100K MC limit and moreover stay within the max FT number CommTree limit of 4095 FT per sidechain

        mc_return_address = mc_node.getnewaddress()


        # we have this number of big tx in one block, that implies a block size a little less than 400K
        ft_in_block = 4
        ft_amount_in_zen = Decimal('0.01')

        # A MC block built in this way will generate MC ref data of more than 500K
        for n in range(0, ft_in_block):

            ft_args = []
            for k in range(0, outputs_in_ft):
                ft_args.append({
                    "toaddress": str(addresses[k]),
                    "amount": ft_amount_in_zen,
                    "scid": str(self.sc_nodes_bootstrap_info.sidechain_id),
                    "mcReturnAddress": mc_return_address
                })
            transaction_id = mc_node.sc_send(ft_args)
            logging.info("FT transaction id: {0}".format(transaction_id))
            time.sleep(1.1)
            tx_json = mc_node.getrawtransaction(transaction_id, 1)
            logging.info("sent tx with sz {} [{}]".format(tx_json['size'], transaction_id))

            bh = mc_node.generate(1)
            block = mc_node.getblock(bh[0], True)
            logging.info("Generated MC block with sz {} [{}]".format(block['size'], bh[0]))

        self.sync_all()
        return bh


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
            self.options.tmpdir, extra_args=[['-mc_block_delay_ref', str(MC_BLOCK_DELAY)] + arg1, arg2],
            binary=[EVM_APP_BINARY] * 2, auth_api_key=self.API_KEY)

    def run_test(self):

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        mc_node = self.nodes[0]

        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        logging.info("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node_1.block_best()["result"]
        logging.info(sc_best_block)

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node_1.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        # test we can not use a wrong key
        try:
            http_wallet_createPrivateKeySec256k1(sc_node_1, "qqq")
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("Using a wrong key should fail")

        evm_address = http_wallet_createPrivateKeySec256k1(sc_node_1)
        logging.info("pubkey = {}".format(evm_address))

        # call a legacy wallet api
        ret = http_wallet_allPublicKeys(sc_node_1)
        logging.info(ret)

        # input("\n\t======> Enter any input to continue generating a new sc block...")
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()
        sc_best_block = sc_node_2.block_best()["result"]

        assert_equal(2, sc_best_block["height"], "The best block has not the specified height.")
        invalidate_list = []
        mc_block_list = []

        mc_block_list += mc_node.generate(1 + MC_BLOCK_DELAY)
        invalidate_list.append(mc_block_list[-1-MC_BLOCK_DELAY])

        self.sync_all()

        logging.info("generating {} addresses, it may take some time...".format(outputs_in_ft))
        for k in range(0, outputs_in_ft):
            addresses.append(sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"])
        #---------------------------------------------------------
        num_of_invalidations = 24
        num_of_sc_blocks_per_mc_ref = 30

        for i in range(0, num_of_invalidations):
            generate_next_blocks(sc_node_1, "first node", num_of_sc_blocks_per_mc_ref)
            self.sc_sync_all()

            sc_best_block = sc_node_2.block_best()["result"]

            mc_block_list += self.generate_big(sc_node_1)
            invalidate_list.append(mc_block_list[-1 - MC_BLOCK_DELAY])

            self.sync_all()
            time.sleep(5)

        size_list = []

        for j in range(0, num_of_invalidations-1):
            mc_node.invalidateblock(invalidate_list[num_of_invalidations-j-1])
            time.sleep(5)

            mc_node.generate(2*(j+1)+MC_BLOCK_DELAY)
            time.sleep(5)

            generate_next_blocks(sc_node_1, "first node", 1)
            self.sc_sync_all()
            sc_best_block = sc_node_2.block_best()["result"]
            print("###### block_size = {}".format(sc_best_block['block']['size']))
            size_list.append(sc_best_block['block']['size'])

            generate_next_blocks(sc_node_1, "first node", num_of_sc_blocks_per_mc_ref - 1)
            self.sc_sync_all()

        print("Sizes: {}".format(size_list))




if __name__ == "__main__":
    SCEvmBootstrap().main()
