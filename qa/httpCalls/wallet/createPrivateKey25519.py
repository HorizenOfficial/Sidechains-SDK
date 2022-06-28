import json
#create and send a custom transaction block/best
def http_wallet_createPrivateKey25519(sidechainNode, api_key = None):
      if (api_key != None):
            response = sidechainNode.wallet_createPrivateKey25519({},api_key)
      else:
            response = sidechainNode.wallet_createPrivateKey25519()
      return response['result']['proposition']['publicKey']



