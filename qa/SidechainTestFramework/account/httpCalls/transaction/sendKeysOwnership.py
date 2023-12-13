import json


def sendKeysOwnership(sidechainNode, *, sc_address, mc_addr, mc_signature,
                      nonce=None, gas_limit=2300000, max_priority_fee_per_gas=900000000,
                      max_fee_per_gas=900000000, api_key=None):
    j = {
        "ownershipInfo": {
            "scAddress" : sc_address,
            "mcTransparentAddress": mc_addr,
            "mcSignature": mc_signature
        },
        "nonce": nonce,
        "gasInfo": {
            "gasLimit": gas_limit,
            "maxFeePerGas": max_fee_per_gas,
            "maxPriorityFeePerGas": max_priority_fee_per_gas
        }
    }

    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_sendKeysOwnership(request, api_key)
    else:
        response = sidechainNode.transaction_sendKeysOwnership(request)


    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

