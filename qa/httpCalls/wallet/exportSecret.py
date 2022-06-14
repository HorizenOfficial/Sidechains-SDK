import json
#export the secret corresponding to a public key
def http_wallet_exportSecret(sidechainNode, public_key, api_key):
      j = {
        "publickey": public_key
      }
      request = json.dumps(j)
      response = sidechainNode.wallet_exportSecret(request, api_key)
      return response['result']['privKey']