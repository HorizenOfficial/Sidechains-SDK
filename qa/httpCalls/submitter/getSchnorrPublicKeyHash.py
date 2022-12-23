import json

# execute a submitter/getSchnorrPublicKeyHash call
def http_get_schnorr_public_key_hash(sidechainNode, pub_key_to_sign):
    j = {
        "schnorrPublicKey": pub_key_to_sign,
     }
    request = json.dumps(j)
    response = sidechainNode.submitter_getSchnorrPublicKeyHash(request)
    return response["result"]