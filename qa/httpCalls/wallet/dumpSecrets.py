import json
#export all secrets to a file
def http_wallet_dumpSecrets(sidechainNode, file_path, api_key):
      j = {
        "path": file_path
      }
      auth = {
         "api_key": api_key
      }
      request = json.dumps(j)
      authHeader = json.dumps(auth)
      response = sidechainNode.wallet_dumpSecrets(request, authHeader)
      return response['result']['status']