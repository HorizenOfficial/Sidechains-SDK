#!/usr/bin/env python3

from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import eoa_transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.scutil import generate_next_block, generate_account_proposition, \
    assert_true, assert_equal

"""
Check the EVM SC allowUnprotectedTxs property behaviour.

Configuration: bootstrap 1 SC node and start it with allowUnprotectedTxs set to false.
    - Create 1 SC node

Test:
    - Add legacy transaction
    - Verify that transaction is rejected
    - Add legacy transaction using eth API
    - Verify that transaction is rejected
    - Tx coming from another node (or forced into a block) should be allowed

"""


class SCEvmForbidUnprotectedTxs(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=10, allow_unprotected_txs=False)

    def run_test(self):
        self.sc_ac_setup()

        sc_node = self.sc_nodes[0]
        evm_hex_address = remove_0x_prefix(self.evm_address)
        recipient_keys = generate_account_proposition("seed3", 1)[0]
        recipient_proposition = recipient_keys.proposition

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        # tx1: http api
        try:
            createLegacyTransaction(sc_node,
                                    fromAddress=evm_hex_address,
                                    toAddress=recipient_proposition,
                                    value=500,
                                    nonce=0)
        except RuntimeError as e:
            assert_true("Legacy unprotected transaction are not allowed" in e.args[0])

        # tx2: rpc api
        try:
            eoa_transaction(sc_node, from_addr=evm_hex_address, to_addr=recipient_proposition, value=convertZenToWei(1))
        except RuntimeError as e:
            assert_true("Legacy unprotected transaction are not allowed" in e.args[0])

        # tx3: forced tx
        tx_bytes = createLegacyTransaction(sc_node,
                                           fromAddress=evm_hex_address,
                                           toAddress=recipient_proposition,
                                           value=500,
                                           nonce=0,
                                           output_raw_bytes=True)

        forced_tx = signTransaction(sc_node, fromAddress=evm_hex_address, payload=tx_bytes)
        block_id = generate_next_block(sc_node, "first node", forced_tx=[forced_tx])
        block_data = sc_node.block_findById(blockId=block_id)
        assert_equal(block_data['result']['block']['sidechainTransactions'][0]['legacy'], True)


if __name__ == "__main__":
    SCEvmForbidUnprotectedTxs().main()
