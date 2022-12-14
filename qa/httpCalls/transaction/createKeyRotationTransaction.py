import json

# execute a transaction/createKeyRotationTransaction call
def http_create_key_rotation_transaction(sidechainNode, key_type, key_index, new_key, signing_key_signature, master_key_signature, new_key_signature, format = True, automatic_send = True, api_key=None):
    j = {
        "keyType": key_type,
        "keyIndex": key_index,
        "newKey": new_key,
        "signingKeySignature": signing_key_signature,
        "masterKeySignature": master_key_signature,
        "newKeySignature": new_key_signature,
        "format": format,
        "automaticSend": automatic_send
    }
    request = json.dumps(j)
    if (api_key != None):
        response = sidechainNode.transaction_createKeyRotationTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_createKeyRotationTransaction(request)
    return response