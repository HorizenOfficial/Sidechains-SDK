import json


# execute a transaction/withdrawCoins call on account sidechain
def withdrawcoins(sidechain_node, address, amount, nonce=None):
    j = {
        "nonce": nonce,
        "withdrawalRequest":
            {
                "mainchainAddress": str(address),
                "value": amount
            },
        "gasInfo": {
            "gasLimit": 230000,
            "maxFeePerGas": 1,
            "maxPriorityFeePerGas": 1

        }
    }
    request = json.dumps(j)
    response = sidechain_node.transaction_withdrawCoins(request)
    return response


