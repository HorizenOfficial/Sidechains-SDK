#!/usr/bin/env python3
import json
import logging
from decimal import Decimal
import hashlib
from eth_hash.auto import keccak
from eth_abi import decode

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
    - Additional infinite loop commented at the bottom to test breaking point

"""

def get_function_selector(function_signature):
    return keccak(function_signature.encode('utf-8'))[:4].hex()

class SCEvmForgingStakes(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=1, forward_amount=99, block_timestamp_rewind=720 * 120 * 10)

    def raw_encode_call(self, function_name: str, *args):
        """Can be used to encode *args for function_name
            Params:
                function_name: The name of the function including parentheses and input types
                *args: the arguments to encode

            Returns:
                A bytestring of the encoded function
        """
        if function_name not in self.Functions:
            raise RuntimeError("Function {} does not exist on contract {}".format(function_name, self.Name))
        else:
            return self.Functions[function_name].encode(*args)

    def run_test(self):
        # Configuration 
        number_of_stakes = 2970
        max_nonces_per_wallet = 16
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]

        # Create a single wallet
        wallet = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # Initialize the nonce count for the wallet
        wallet_nonce = 0

        # Keep track of the total number of stakes created
        stakes_created = 0

        # Get stake info from genesis block
        sc_genesis_block = sc_node_1.block_best()
        genStakeInfo = sc_genesis_block["result"]["block"]["header"]["forgingStakeInfo"]
        genStakeAmount = genStakeInfo['stakeAmount']
        sc1_blockSignPubKey = genStakeInfo["blockSignPublicKey"]["publicKey"]
        sc1_vrfPubKey = genStakeInfo["vrfPublicKey"]["publicKey"]

        ft_amount_in_zen = Decimal('100.0')

        # Send forward transfer to the created wallet
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      wallet,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        
        # Generate SC block
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Delegate all the stakes
        forgerStake_amount = 0.00001  # Zen

        # Generate request data
        request = {
            "to": "0x0000000000000000000022222222222222222222",
            "data": "0xf6ad3c23"
        }

        while stakes_created < number_of_stakes:
            # Create a stake
            result = ac_makeForgerStake(sc_node_1, wallet, sc1_blockSignPubKey, sc1_vrfPubKey, convertZenToZennies(forgerStake_amount), wallet_nonce)

            if "result" not in result:
                fail(f"{wallet} make forger stake {stakes_created} failed for nonce {wallet_nonce}: " + json.dumps(result))

            # Check if we've reached the max nonce for this wallet
            # Generate block
            if (wallet_nonce + 1) % max_nonces_per_wallet == 0 and wallet_nonce != 0:
                # Generate SC block
                generate_next_block(sc_node_1, "first node")
                self.sc_sync_all()

            if (wallet_nonce + 1) % (max_nonces_per_wallet*10) == 0 and wallet_nonce != 0:
                try:
                    response = sc_node_1.rpc_eth_call(request, "latest")
                    if 'result' in response:
                        response_bytes = bytes.fromhex(response['result'][2:])
                        decoded_response = decode(['(bytes32,uint256,address,bytes32,bytes32,bytes1)[]'], response_bytes)
                        logging.info(f"Successfully retrieved {len(decoded_response[0])} forger stakes")
                    elif 'error' in response:
                        raise Exception(f"Error in rpc_eth_call: {response['error']['message']}")
                    else:
                        raise Exception("rpc_eth_call no result in response")
                except Exception as e:
                    raise Exception("There was a problem fetching allForgingStakes: " + str(e))

            # Update our counts
            stakes_created += 1
            wallet_nonce += 1

        # Generate SC block
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()   

        # We have a total of number_of_stakes+1 stake ids, the genesis creation and the txes just forged
        try:
            response = sc_node_1.rpc_eth_call(request, "latest")
            if 'result' in response:
                response_bytes = bytes.fromhex(response['result'][2:])
                decoded_response = decode(['(bytes32,uint256,address,bytes32,bytes32,bytes1)[]'], response_bytes)
                logging.info(f"Successfully retrieved {len(decoded_response[0])} forger stakes")
        except Exception as e:
            raise Exception("There was a problem fetching allForgingStakes: " + str(e))

        # pprint.pprint(stakeList)
        assert_equal(number_of_stakes + 1, len(decoded_response[0]))


        # # Continue sending transactions and getting forging stake list until it breaks
        # while True:
        #     for i in range(max_nonces_per_wallet*10):
        #         # Create a stake
        #         result = ac_makeForgerStake(sc_node_1, wallet, sc1_blockSignPubKey, sc1_vrfPubKey, convertZenToZennies(forgerStake_amount), wallet_nonce)

        #         if "result" not in result:
        #             fail(f"{wallet} make forger stake {stakes_created} failed for nonce {wallet_nonce}: " + json.dumps(result))

        #         # Check if we've reached the max nonce for this wallet
        #         # Generate block
        #         if (wallet_nonce + 1) % max_nonces_per_wallet == 0 and wallet_nonce != 0:
        #             # Generate SC block
        #             generate_next_block(sc_node_1, "first node")
        #             self.sc_sync_all()

        #         # Update our counts
        #         stakes_created += 1
        #         wallet_nonce += 1

        #         try:
        #             response = sc_node_1.rpc_eth_call(request, "latest")
        #             if 'result' in response:
        #                 response_bytes = bytes.fromhex(response['result'][2:])
        #                 decoded_response = decode(['(bytes32,uint256,address,bytes32,bytes32,bytes1)[]'], response_bytes)
        #                 logging.info(f"Successfully retrieved {len(decoded_response[0])} forger stakes")
        #             elif 'error' in response:
        #                 raise Exception(f"Error in rpc_eth_call: {response['error']['message']}")
        #             else:
        #                 raise Exception("rpc_eth_call no result in response")
        #         except Exception as e:
        #             raise Exception("There was a problem fetching allForgingStakes: " + str(e))

if __name__ == "__main__":
    SCEvmForgingStakes().main()