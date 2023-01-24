import json


# execute a submitter/getKeyRotationMessageToSign call
def http_get_key_rotation_message_to_sign(sidechain_node, pub_key_to_sign, key_type, withdrawal_epoch):
    j = {
        "schnorrPublicKey": pub_key_to_sign,
        "keyType": key_type,
        "withdrawalEpoch": withdrawal_epoch
    }
    request = json.dumps(j)
    response = sidechain_node.submitter_getKeyRotationMessageToSign(request)
    return response["result"]
