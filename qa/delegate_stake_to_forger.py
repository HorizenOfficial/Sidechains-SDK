import argparse
from web3 import Web3
from eth_account import Account


# python delegate_stake_to_forger.py PRIVATE_KEY ZEN_AMOUNT BLOCK_SIGN_PUBLIC_KEY FORGER_VRF_PUBLIC_KEY

def main(private_key, zen_amount, block_sign_public_key, forger_vrf_public_key):
    # Connect
    w3 = Web3(Web3.HTTPProvider('https://gobi-rpc.horizenlabs.io/ethv1'))

    contract_address = "0x0000000000000000000022222222222222222222"

    # ABI
    ABI = [
        {
            "type": "function",
            "name": "delegate",
            "stateMutability": "payable",
            "payable": True,
            "constant": False,
            "inputs": [
                {"type": "bytes32", "name": "blockSignPublicKey"},
                {"type": "bytes32", "name": "first32BytesForgerVrfPublicKey"},
                {"type": "bytes1", "name": "lastByteForgerVrfPublicKey"},
                {"type": "address", "name": "ownerAddress"}
            ],
            "outputs": [{"type": "bytes32"}]
        },
        {
            "type": "event",
            "anonymous": False,
            "name": "DelegateForgerStake",
            "inputs": [
                {"name": "from", "type": "address", "indexed": True},
                {"name": "owner", "type": "address", "indexed": True},
                {"name": "stakeId", "type": "bytes32", "indexed": False},
                {"name": "value", "type": "uint256", "indexed": False}
            ]
        }
    ]

    contract = w3.eth.contract(address=contract_address, abi=ABI)

    # Initialize account
    account = Account.from_key(private_key)

    # Convert hex inputs to bytes
    block_sign_public_key = bytes.fromhex(block_sign_public_key)
    forger_vrf_public_key = bytes.fromhex(forger_vrf_public_key)

    # Split the forger_vrf_public_key into bytes32 and bytes1
    first_32_bytes_forger_vrf_public_key = forger_vrf_public_key[:32]
    last_byte_forger_vrf_public_key = forger_vrf_public_key[32:]

    # Amount (in wei)
    amount_in_wei = w3.to_wei(zen_amount, 'ether')

    # Prepare transaction
    transaction = {
        'to': contract_address,
        'value': amount_in_wei,
        'gas': 2000000,
        'gasPrice': w3.to_wei('50', 'gwei'),
        'nonce': w3.eth.get_transaction_count(account.address),
        'chainId': w3.eth.chain_id,
        'data': contract.functions.delegate(
            block_sign_public_key,
            first_32_bytes_forger_vrf_public_key,
            last_byte_forger_vrf_public_key,
            account.address
        ).build_transaction({'gas': 2000000, 'gasPrice': w3.to_wei('50', 'gwei')})['data']
    }

    # Sign transaction
    signed_transaction = account.sign_transaction(transaction)

    # Send transaction
    tx_hash = w3.eth.send_raw_transaction(signed_transaction.rawTransaction)

    print(f'Transaction sent with hash: {tx_hash.hex()}')

    # Wait for transaction to be mined
    receipt = w3.eth.wait_for_transaction_receipt(tx_hash)

    print(f'Transaction receipt: {receipt}')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Send a transaction to the contract')
    parser.add_argument('private_key', type=str, help='Private key of the sender')
    parser.add_argument('zen_amount', type=str, help='Amount of Zen to send')
    parser.add_argument('block_sign_public_key', type=str, help='Block sign public key as a hex string')
    parser.add_argument('forger_vrf_public_key', type=str, help='Forger VRF public key as a hex string')

    args = parser.parse_args()

    main(args.private_key, args.zen_amount, args.block_sign_public_key, args.forger_vrf_public_key)
