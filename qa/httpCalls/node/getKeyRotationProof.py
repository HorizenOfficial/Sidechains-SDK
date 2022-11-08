import json

# execute a node/getKeyRotationProof call
def http_get_key_rotation_proof(sidechainNode, withdrawal_epoch, index_of_signer, key_type):
    j = {
        "withdrawalEpoch": withdrawal_epoch,
        "indexOfSigner": index_of_signer,
        "keyType": key_type
     }
    request = json.dumps(j)
    response = sidechainNode.node_getKeyRotationProof(request)
    return response["result"]