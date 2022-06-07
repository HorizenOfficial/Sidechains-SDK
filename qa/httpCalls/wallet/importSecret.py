import json
#import the secret corresponding to a public key
def http_wallet_importSecret(sidechainNode, secret, api_key):
      j = {
        "privKey": secret
      }
      auth = {
         "api_key": api_key
      }
      request = json.dumps(j)
      authHeader = json.dumps(auth)
      response = sidechainNode.wallet_importSecret(request, authHeader)
      return response['result']['proposition']