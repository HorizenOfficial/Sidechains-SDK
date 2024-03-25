import argparse
from web3 import Web3
from eth_account import Account


# python upgrade.py PRIVATE_KEY

def main():
    # Connect
    w3 = Web3(Web3.HTTPProvider('https://gobi-rpc.horizenlabs.io/ethv1'))
    private_key = "ad41864efba84bf83fce496d4ff1b314f8f65c1f222801ef4df7240b07fdd087"
    # w3 = Web3(Web3.HTTPProvider('http://127.0.0.1:8836/ethv1'))

    # contract_address = "0x0000000000000000000022222222222222222222"
    #
    # # ABI
    # ABI = [
    #     {
    #         "anonymous": False,
    #         "inputs": [
    #             {
    #                 "indexed": False,
    #                 "internalType": "uint32",
    #                 "name": "oldVersion",
    #                 "type": "uint32"
    #             },
    #             {
    #                 "indexed": False,
    #                 "internalType": "uint32",
    #                 "name": "newVersion",
    #                 "type": "uint32"
    #             }
    #         ],
    #         "name": "StakeUpgrade",
    #         "type": "event"
    #     },
    #     {
    #         "inputs": [],
    #         "name": "upgrade",
    #         "outputs": [
    #             {
    #                 "internalType": "uint32",
    #                 "name": "",
    #                 "type": "uint32"
    #             }
    #         ],
    #         "stateMutability": "view",
    #         "type": "function"
    #     }
    # ]
    #
    # contract = w3.eth.contract(address=contract_address, abi=ABI)
    #
    # Initialize account
    account = Account.from_key(private_key)


    # Amount (in wei)
    # amount_in_wei = w3.to_wei(zen_amount, 'ether')

    # Prepare transaction
    MAX_INITCODE_SIZE = 49152
    big_data = 'FF' * (MAX_INITCODE_SIZE + 1)

    transaction = {
        'gas': 20533001,
        'gasPrice': w3.to_wei('50', 'gwei'),
        'nonce': w3.eth.get_transaction_count(account.address),
        'chainId': w3.eth.chain_id,
        'data': big_data
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
    # parser = argparse.ArgumentParser(description='Send a transaction to the contract')
    # parser.add_argument('private_key', type=str, help='Private key of the sender')
    #
    # args = parser.parse_args()

    # main(args.private_key)
    main()
