import json
#export all secrets to a file
def http_wallet_dumpSecrets(sidechainNode, file_path, api_key):
      j = {
        "path": file_path
      }
      request = json.dumps(j)
      response = sidechainNode.wallet_dumpSecrets(request, api_key)
      return response['result']['status']