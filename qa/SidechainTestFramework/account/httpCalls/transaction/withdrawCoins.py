import json


# execute a transaction/withdrawCoins call on account sidechain
def withdrawcoins(sidechain_node, address, amount, nonce=None, gas_limit=230000, max_fee_per_gas=1,
                  max_priority_fee_per_gas=1):
    j = {
        "nonce": nonce,
        "withdrawalRequest":
            {
                "mainchainAddress": str(address),
                "value": amount
            },
        "gasInfo": {
            "gasLimit": gas_limit,
            "maxFeePerGas": max_fee_per_gas,
            "maxPriorityFeePerGas": max_priority_fee_per_gas

        }
    }
    request = json.dumps(j)
    response = sidechain_node.transaction_withdrawCoins(request)
    return response
