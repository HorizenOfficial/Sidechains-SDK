import json

# execute a node/signSchnorrPublicKey call
def http_sign_schnorr_publicKey(sidechainNode, message_to_sign, key):
    j = {
        "messageToSign": message_to_sign,
        "key": key
     }
    request = json.dumps(j)
    response = sidechainNode.node_signSchnorrPublicKey(request)
    return response["result"]