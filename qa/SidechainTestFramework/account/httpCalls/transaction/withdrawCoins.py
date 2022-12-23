import json


# execute a transaction/withdrawCoins call on account sidechain
def withdrawcoins(sidechain_node, destMCAddress, amount_in_zennies, nonce=None, gas_limit=230000, max_fee_per_gas=900000000,
                  max_priority_fee_per_gas=900000000, api_key=None):
    j = {
        "nonce": nonce,
        "withdrawalRequest":
            {
                "mainchainAddress": str(destMCAddress),
                "value": amount_in_zennies
            },
        "gasInfo": {
            "gasLimit": gas_limit,
            "maxFeePerGas": max_fee_per_gas,
            "maxPriorityFeePerGas": max_priority_fee_per_gas
        }
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechain_node.transaction_withdrawCoins(request, api_key)
    else:
        response = sidechain_node.transaction_withdrawCoins(request)

    if "result" in response:
        return response

    raise RuntimeError("Something went wrong, see {}".format(str(response)))