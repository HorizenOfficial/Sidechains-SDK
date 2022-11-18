#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa
from SidechainTestFramework.scutil import is_mainchain_block_included_in_sc_block, check_mainchain_block_reference_info, \
    generate_next_blocks, generate_next_block
from test_framework.util import assert_equal, assert_true, forward_transfer_to_sidechain

"""
Check that forward transfer to non-EOA account does not change balance.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Execute forward transfer to an EOA
    - Verify account balance
    - Verify forward transfer
    - Deploy Smart Contract
    - Execute forward transfer to the address of the smart contract
    - Verify balance of the smart contract account has not changed
"""


class SCEvmForwardTransfer(AccountChainSetup):
    def __init__(self):
        super().__init__()

    def run_test(self):
        sc_node = self.sc_nodes[0]
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        logging.info("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node.block_best()["result"]

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        mc_return_address = self.nodes[0].getnewaddress()

        ret = sc_node.wallet_createPrivateKeySecp256k1()
        logging.info(ret)
        evm_address = format_evm(ret["result"]["proposition"]["address"])
        logging.info("pubkey = {}".format(evm_address))

        # call a legacy wallet api
        ret = sc_node.wallet_allPublicKeys()
        logging.info(ret)

        ft_amount_in_zen = Decimal("33.22")

        # transfer some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(evm_address),
                                      ft_amount_in_zen,
                                      mc_return_address)

        blockId = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # verify forward transfer was received
        balance = sc_node.rpc_eth_getBalance(evm_address, "latest")
        logging.info(balance)
        assert_equal("0x1cd0525fe2e7a0000", balance["result"], "FT to EOA failed")

        # verify forward transfer is contained in block and contains given value and to address via rpc
        forward_transfer = sc_node.rpc_zen_getForwardTransfers("latest")['result']['forwardTransfers'][0]
        assert_equal("0x1cd0525fe2e7a0000", forward_transfer['value'])
        assert_equal(evm_address.lower(), forward_transfer['to'])

        # verify forward transfer is contained in block and contains given value and to address via api
        j = {
            "blockId": blockId
        }
        request = json.dumps(j)
        forward_transfer = sc_node.block_getForwardTransfers(request)['result']['forwardTransfers'][0]
        assert_equal('33220000000000000000', forward_transfer['value'])
        assert_equal(format_eoa(evm_address), forward_transfer['to'])

        # Deploy Smart Contract
        smart_contract_type = 'StorageTestContract'
        logging.info(f"Creating smart contract utilities for {smart_contract_type}")
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)
        test_message = 'Initial message'
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, test_message,
                                                                fromAddress=evm_address,
                                                                gasLimit=10000000,
                                                                gasPrice=900000000)

        self.sc_sync_all()
        logging.info("Mempool node before")
        response = sc_node.transaction_allTransactions(json.dumps({"format": True}))
        logging.info(response)

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        logging.info("Mempool node after")
        response = sc_node.transaction_allTransactions(json.dumps({"format": True}))
        logging.info(response)

        # verify smart contract has a balance of zero
        balance = sc_node.rpc_eth_getBalance(smart_contract_address, "latest")
        logging.info(balance)
        assert_equal("0x0", balance["result"], "smart contract has non-zero balance")

        # execute forward transfer to the smart contract account
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(smart_contract_address),
                                      ft_amount_in_zen,
                                      mc_return_address)

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # verify that the balance has not changed, FT to smart contract account should be rejected
        # TODO check why failed (balance actually increased, problem with FT?)
        balance = sc_node.rpc_eth_getBalance(smart_contract_address, "latest")
        logging.info(balance)
        assert_equal("0x0", balance["result"], "smart contract has non-zero balance")


if __name__ == "__main__":
    SCEvmForwardTransfer().main()
