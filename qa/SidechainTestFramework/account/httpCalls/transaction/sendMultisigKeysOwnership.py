import json


def sendMultisigKeysOwnership(sidechainNode, *, sc_address, mc_addr, mc_signatures, redeemScript,
                      nonce=None, gas_limit=2300000, max_priority_fee_per_gas=900000000,
                      max_fee_per_gas=900000000, api_key=None):
    j = {
        "ownershipInfo": {
            "scAddress" : sc_address,
            "mcMultisigAddress": mc_addr,
            "mcSignatures": mc_signatures,
            "redeemScript": redeemScript
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
        response = sidechainNode.transaction_sendMultisigKeysOwnership(request, api_key)
    else:
        response = sidechainNode.transaction_sendMultisigKeysOwnership(request)


    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))

