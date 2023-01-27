import json


# execute a submitter/getKeyRotationMessageToSign call
def http_get_key_rotation_message_to_sign_for_signing_key(sidechain_node, pub_key_to_sign, withdrawal_epoch):
    j = {
        "schnorrPublicKey": pub_key_to_sign,
        "withdrawalEpoch": withdrawal_epoch
    }
    request = json.dumps(j)
    response = sidechain_node.submitter_getKeyRotationMessageToSignForSigningKey(request)
    return response["result"]


def http_get_key_rotation_message_to_sign_for_master_key(sidechain_node, pub_key_to_sign, withdrawal_epoch):
    j = {
        "schnorrPublicKey": pub_key_to_sign,
        "withdrawalEpoch": withdrawal_epoch
    }
    request = json.dumps(j)
    response = sidechain_node.submitter_getKeyRotationMessageToSignForMasterKey(request)
    return response["result"]
