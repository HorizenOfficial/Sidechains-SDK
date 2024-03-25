import argparse
from web3 import Web3
from eth_account import Account

# RPC Connection
w3 = Web3(Web3.HTTPProvider('https://gobi-rpc.horizenlabs.io/ethv1'))

# Argument parsing
parser = argparse.ArgumentParser(description="Call the withdraw function with a private key and stakeId.")
parser.add_argument('private_key', type=str, help='Your private key for signing the transaction')
parser.add_argument('stake_id', type=str, help='Your stakeId')
args = parser.parse_args()

# Contract details
ABI = [  
       {
    "type": "function",
    "name": "withdraw",
    "stateMutability": "nonpayable",
    "payable": False,
    "constant": False,
    "inputs": [
      {
        "type": "bytes32",
        "name": "stakeId"
      },
      {
        "type": "bytes1",
        "name": "v"
      },
      {
        "type": "bytes32",
        "name": "r"
      },
      {
        "type": "bytes32",
        "name": "s"
      }
    ],
    "outputs": [
      {
        "type": "bytes32"
      }
    ]
  },
  {
    "type": "event",
    "name": "WithdrawForgerStake",
    "inputs": [
      {
        "name": "owner",
        "type": "address",
        "indexed": True
      },
      {
        "name": "stakeId",
        "type": "bytes32",
        "indexed": False
      }
    ]
  } 
]

contract_address = '0x0000000000000000000022222222222222222222'
contract = w3.eth.contract(address=contract_address, abi=ABI)

# Initialize account
account = Account.from_key(args.private_key)

# Build the message to sign
nonce = w3.eth.get_transaction_count(account.address)
nonce = 165
nonce_hex = hex(nonce)[2:]  # Convert nonce to hex and remove the '0x' prefix
if len(nonce_hex) % 2 > 0:
    nonce_hex = '0' + nonce_hex  # Ensure it has an even number of characters
    
stake_id_bytes32 = bytes.fromhex(args.stake_id[2:]) if args.stake_id.startswith('0x') else bytes.fromhex(args.stake_id)


print("address = " + account.address)

print("nonce " + nonce_hex)
message_str = account.address[2:] + nonce_hex + args.stake_id
message_str = "3d372940331a8cda7181c2c7848ef8fa0e69992e" + nonce_hex + "f18d25363855151a6491d001e9bd35bb0f3257a3657614819d593c5ce6dbdef7"
# message_str = account.address + nonce_hex + args.stake_id
print(message_str)
message = bytes.fromhex(message_str)

# Hash the message with keccak256
hashed_message = w3.solidity_keccak(['bytes'], [message])

# Sign the keccak256 hash
signature = account.signHash(hashed_message)
r = signature.r
s = signature.s
v = signature.v

# Convert r and s from integers to bytes32
r_bytes = r.to_bytes(32, byteorder='big')
s_bytes = s.to_bytes(32, byteorder='big')

# Convert the v value to bytes1 format
v_bytes = bytes([v])

# Prepare transaction for the withdraw function
transaction = {
    'to': contract_address,
    'value': 0,
    'gas': 2000000,
    'gasPrice': w3.to_wei('50', 'gwei'),
    'nonce': nonce,
    'chainId': w3.eth.chain_id,
    'data': contract.functions.withdraw(stake_id_bytes32, v_bytes, r_bytes, s_bytes).build_transaction({
        'gas': 2000000,
        'gasPrice': w3.to_wei('50', 'gwei'),
        'nonce': nonce
    })['data']
}

# Sign the transaction locally
signed_transaction = account.sign_transaction(transaction)

# Send the transaction
txn_hash = w3.eth.send_raw_transaction(signed_transaction.rawTransaction)
print(f"Transaction hash: {txn_hash.hex()}")

# Wait for the transaction receipt
receipt = w3.eth.wait_for_transaction_receipt(txn_hash)

# Check the transaction status
if receipt.status:
    print("Transaction was successful!")
else:
    print("Transaction failed!")
    try:
        # Use eth_call to simulate the transaction and get the revert reason
        response = w3.eth.call({**transaction, 'from': account.address}, block_identifier='latest')
    except ValueError as e:
        revert_reason = str(e.args[0].get('message'))
        print(f"Revert reason: {revert_reason}")
