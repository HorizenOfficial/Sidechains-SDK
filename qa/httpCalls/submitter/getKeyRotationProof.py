import json

# execute a submitter/getKeyRotationProof call
def http_get_key_rotation_proof(sidechainNode, withdrawal_epoch, index_of_key, key_type):
    j = {
        "withdrawalEpoch": withdrawal_epoch,
        "indexOfKey": index_of_key,
        "keyType": key_type
     }
    request = json.dumps(j)
    response = sidechainNode.submitter_getKeyRotationProof(request)
    return response