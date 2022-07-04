import json
#export all secrets to a file
def http_wallet_dumpSecrets(sidechainNode, file_path, api_key = None):
      j = {
        "path": file_path
      }
      request = json.dumps(j)
      if (api_key != None):
        response = sidechainNode.wallet_dumpSecrets(request, api_key)
      else:
         response = sidechainNode.wallet_dumpSecrets(request)
      return response['result']['status']