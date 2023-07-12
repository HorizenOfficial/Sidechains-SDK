#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain
)

"""
Configuration: 
    - 1 SC node
    - 1 MC node
    - SC1 node owns a stakeAmount made out of cross chain creation output

Test:
    - Send FTs to SC1 (used for forging delegation)
    - SC1 delegates to itself until the number of delegations is met
    - Check all delegated stakes can be retrieved


"""


def getSignerStakeAmount(myInfoList, inSignerAddress):
    sum = 0
    for entry in myInfoList:
        signerAddress = entry['forgerStakeData']['forgerPublicKeys']['blockSignPublicKey']['publicKey']
        if signerAddress == inSignerAddress:
            sum += entry['forgerStakeData']['stakedAmount']
    # print("Sum = {}, address={}".format(sum, inSignerAddress))
    return sum


class SCEvmForgingStakes(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=1, forward_amount=99, block_timestamp_rewind=720 * 120 * 10)

    def run_test(self):
        # Configuration
        number_of_stakes = 17
        max_transactions_per_block = 100
        max_nonces_per_wallet = 16
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]

        # Create the wallets
        number_of_wallets = (number_of_stakes + max_nonces_per_wallet - 1) // max_nonces_per_wallet
        wallets = [sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"] for _ in range(number_of_wallets)]

        # Initialize the nonce count for each wallet
        nonces_per_wallet = [0] * number_of_wallets

        # Keep track of the total number of stakes created
        stakes_created = 0

        # get stake info from genesis block
        sc_genesis_block = sc_node_1.block_best()
        genStakeInfo = sc_genesis_block["result"]["block"]["header"]["forgingStakeInfo"]
        genStakeAmount = genStakeInfo['stakeAmount']
        sc1_blockSignPubKey = genStakeInfo["blockSignPublicKey"]["publicKey"]
        sc1_vrfPubKey = genStakeInfo["vrfPublicKey"]["publicKey"]

        ft_amount_in_zen = Decimal('1.0')

        for i in range(number_of_wallets):
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                        mc_node,
                                        wallets[i],
                                        ft_amount_in_zen,
                                        mc_return_address=mc_node.getnewaddress(),
                                        generate_block=True)
        
        # Generate SC block
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Delegate all the stakes
        forgerStake_amount = 0.001  # Zen

        wallet_index = 0
        while stakes_created < number_of_stakes:
            # Check if we've reached the maximum number of nonces for this wallet
            if nonces_per_wallet[wallet_index] < max_nonces_per_wallet:
                # Create a stake
                result = ac_makeForgerStake(sc_node_1, wallets[wallet_index], sc1_blockSignPubKey, sc1_vrfPubKey, convertZenToZennies(forgerStake_amount), nonces_per_wallet[wallet_index])

                if "result" not in result:
                    fail(f"{wallets[wallet_index]} make forger stake {stakes_created} failed for nonce {nonces_per_wallet[wallet_index]}: " + json.dumps(result))
                else:
                    logging.info(f"{wallets[wallet_index]} Forger stake {stakes_created} created: " + json.dumps(result))

                # Update our counts
                stakes_created += 1
                nonces_per_wallet[wallet_index] += 1

                # Check if we've reached the maximum number of transactions for this block
                if stakes_created % max_transactions_per_block == 0:
                    self.sc_sync_all()

                    # get mempool contents, we must have max_transactions_per_block forger stake txes
                    mempoolList = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))['result']['transactionIds']
                    assert_equal(max_transactions_per_block, len(mempoolList))

                    # Generate SC block
                    generate_next_block(sc_node_1, "first node")
                    self.sc_sync_all()
            else:
                # If the current wallet reached its maximum nonce, increment the wallet_index to use the next wallet
                wallet_index += 1


        # we have a total of number_of_stakes+1 stake ids, the genesis creation and the txes just forged
        stakeList = sc_node_1.transaction_allForgingStakes()['result']['stakes']
        # pprint.pprint(stakeList)
        assert_equal(number_of_stakes + 1, len(stakeList))

if __name__ == "__main__":
    SCEvmForgingStakes().main()
