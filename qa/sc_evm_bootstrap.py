#!/usr/bin/env python3
import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, \
    generate_next_blocks
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


class SCEvmBootstrap(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def run_test(self):

        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

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


if __name__ == "__main__":
    SCEvmBootstrap().main()
