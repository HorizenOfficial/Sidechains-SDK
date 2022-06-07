import json
#import all secrets from a file
def http_wallet_importSecrets(sidechainNode, file_path, api_key):
      j = {
        "path": file_path
      }
      auth = {
         "api_key": api_key
      }
      request = json.dumps(j)
      authHeader = json.dumps(auth)
      response = sidechainNode.wallet_importSecrets(request, authHeader)
      return response['result']