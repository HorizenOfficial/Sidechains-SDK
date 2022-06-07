import json
#export the secret corresponding to a public key
def http_wallet_exportSecret(sidechainNode, public_key, api_key):
      j = {
        "publickey": public_key
      }
      auth = {
         "api_key": api_key
      }
      request = json.dumps(j)
      authHeader = json.dumps(auth)
      response = sidechainNode.wallet_exportSecret(request, authHeader)
      return response['result']['privKey']