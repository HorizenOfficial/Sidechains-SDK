import json
#import the secret corresponding to a public key
def http_wallet_importSecret(sidechainNode, secret, api_key):
      j = {
        "privKey": secret
      }
      request = json.dumps(j)
      response = sidechainNode.wallet_importSecret(request, api_key)
      return response['result']['proposition']