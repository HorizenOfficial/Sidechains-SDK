import sys
from web3 import Web3

# RPC Connection
w3 = Web3(Web3.HTTPProvider('https://gobi-rpc.horizenlabs.io/ethv1'))

# Check if an address is passed as an argument
filter_address = None

if len(sys.argv) == 2:
    filter_address = sys.argv[1].lower()


# Contract details
ABI = [
    {
      "type": "function",
      "name": "getAllForgersStakes",
      "stateMutability": "view",
      "constant": True,
      "payable": False,
      "inputs": [],
      "outputs": [
        {
          "type": "tuple[]",
          "components": [
            {
              "type": "bytes32",
              "name": "stakeId"
            },
            {
              "type": "uint256",
              "name": "stakeAmount"
            },
            {
              "type": "address",
              "name": "ownerAddress"
            },
            {
              "type": "bytes32",
              "name": "blockSignPublicKey"
            },
            {
              "type": "bytes32",
              "name": "first32BytesForgerVrfPublicKey"
            },
            {
              "type": "bytes1",
              "name": "lastByteForgerVrfPublicKey"
            }
          ]
        }
      ]
    }
]

contract_address = '0x0000000000000000000022222222222222222222'
contract = w3.eth.contract(address=contract_address, abi=ABI)

# Call the function
results = contract.functions.getAllForgersStakes().call()

# Output the results
for stake in results:
    if filter_address is None or stake[2].lower() == filter_address:
        print("Stake ID:", stake[0].hex())
        print("Stake Amount:", stake[1])
        print("Owner Address:", stake[2])
        print("Block Sign Public Key:", stake[3].hex())
        print("First 32 Bytes Forger VRF Public Key:", stake[4].hex())
        print("Last Byte Forger VRF Public Key:", stake[5].hex())
        print("-------------------------------")

