import json


def sendKeysOwnership(sidechainNode, *, fromAddress, mc_pub_key, mc_signature,
                      nonce=None, gas_limit=230000, max_priority_fee_per_gas=900000000,
                      max_fee_per_gas=900000000, api_key=None):
    j = {
        "ownershipInfo": {
            "scAddress" : fromAddress,
             "mcPubKey": mc_pub_key,
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

