import json
#import all secrets from a file
def http_wallet_importSecrets(sidechainNode, file_path, api_key = None):
      j = {
        "path": file_path
      }
      request = json.dumps(j)
      if (api_key != None):
        response = sidechainNode.wallet_importSecrets(request, api_key)
      else:
        response = sidechainNode.wallet_importSecrets(request)
      return response['result']