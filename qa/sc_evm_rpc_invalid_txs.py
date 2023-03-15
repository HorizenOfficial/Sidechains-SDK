#!/usr/bin/env python3
import logging
import re
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import eoa_transaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.scutil import (
    assert_true,
)

"""
Check that sending an invalid transaction to the RPC method eth_sendRawTransaction returns an error
"""


class SCEvmRPCInvalidTx(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        sc_node_1 = self.sc_nodes[0]

        ft_amount_in_zen = Decimal('3000.0')
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = self.evm_address
        evm_address_sc2 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # test that sending an invalid transaction to eth_sendRawTransaction fails with an error
        # the tx is semantically invalid because the supplied gas limit is below the required intrinsic gas
        exception_occurred = False
        try:
            res = eoa_transaction(
                sc_node_1, gas=20000,
                from_addr=evm_address_sc1, to_addr=evm_address_sc2, value=convertZenToWei(1)
            )
            logging.error("invalid transaction was accepted via RPC api: {}".format(str(res)))
        except RuntimeError as err:
            logging.debug("invalid transaction was rejected with: {}".format(str(err)))
            if re.search("gas limit .* is below intrinsic gas", str(err)):
                exception_occurred = True
        assert_true(exception_occurred, "invalid transaction should be rejected by RPC api")


if __name__ == "__main__":
    SCEvmRPCInvalidTx().main()
