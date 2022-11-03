import json

# execute a node/getKeyRotationProof call
def http_get_key_rotation_proof(sidechainNode, withdrawalEpoch, indexOfSigner, keyType):
    j = {
        "withdrawalEpoch": withdrawalEpoch,
        "indexOfSigner": indexOfSigner,
        "keyType": keyType
     }
    request = json.dumps(j)
    response = sidechainNode.transaction_getKeyRotationProof(request)
    return response["result"]