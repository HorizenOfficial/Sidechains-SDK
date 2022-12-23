def http_wallet_createPrivateKeySec256k1(sidechainNode, api_key=None):
    if (api_key != None):
        response = sidechainNode.wallet_createPrivateKeySecp256k1({}, api_key)
    else:
        response = sidechainNode.wallet_createPrivateKeySecp256k1()
    return response['result']['proposition']['address']
