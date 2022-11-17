import json
#import the secret corresponding to a public key
def http_wallet_importSecret(sidechainNode, secret, api_key = None, reindex = False):
      if (reindex == True):
          j = {
            "privKey": secret,
            "reindex": True
          }
      else:
          j = {
              "privKey": secret
          }
      request = json.dumps(j)
      if (api_key != None):
        response = sidechainNode.wallet_importSecret(request, api_key)
      else:
        response = sidechainNode.wallet_importSecret(request)
      return response['result']['proposition']

