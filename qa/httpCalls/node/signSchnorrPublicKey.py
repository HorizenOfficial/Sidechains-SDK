import json

# execute a node/signSchnorrPublicKey call
def http_sign_schnorr_publicKey(sidechainNode, message_to_sign, key):
    j = {
        "message_to_sign": message_to_sign,
        "key": key
     }
    request = json.dumps(j)
    response = sidechainNode.transaction_signSchnorrPublicKey(request)
    return response["result"]