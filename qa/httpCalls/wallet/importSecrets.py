import json
#import all secrets from a file
def http_wallet_importSecrets(sidechainNode, file_path, api_key):
      j = {
        "path": file_path
      }
      request = json.dumps(j)
      response = sidechainNode.wallet_importSecrets(request, api_key)
      return response['result']