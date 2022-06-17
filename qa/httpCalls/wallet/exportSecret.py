import json
#export the secret corresponding to a public key
def http_wallet_exportSecret(sidechainNode, public_key, api_key = None):
      j = {
        "publickey": public_key
      }
      request = json.dumps(j)
      if (api_key != None):
        response = sidechainNode.wallet_exportSecret(request, api_key)
      else:
        response = sidechainNode.wallet_exportSecret(request)
      return response['result']['privKey']