import json


def http_create_key_rotation_transaction_evm(sidechainNode, key_type, key_index, new_key,
                                             signing_key_signature, master_key_signature, new_key_signature,
                                             nonce=None, gas_limit=None, max_fee_per_gas=900000000,
                                             max_priority_fee_per_gas=900000000, api_key=None):
    j = {
        "keyType": key_type,
        "keyIndex": key_index,
        "newKey": new_key,
        "signingKeySignature": signing_key_signature,
        "masterKeySignature": master_key_signature,
        "newKeySignature": new_key_signature,
        "nonce": nonce
    }
    if gas_limit is not None:
        j["gasInfo"] = {
            "gasLimit": gas_limit,
            "maxFeePerGas": max_fee_per_gas,
            "maxPriorityFeePerGas": max_priority_fee_per_gas
        }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_createKeyRotationTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_createKeyRotationTransaction(request)
    return response
