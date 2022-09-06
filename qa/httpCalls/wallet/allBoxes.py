import json

#executes a  wallet/allBoxes call
def http_wallet_allBoxes(sidechainNode, typeName = None, api_key = None):
      if (api_key != None):
            response = sidechainNode.wallet_allBoxes(json.dumps({"boxTypeClass": typeName}) if typeName != None else {}, api_key)
      else:
            response = sidechainNode.wallet_allBoxes(json.dumps({"boxTypeClass": typeName}) if typeName != None else {})
      return response['result']['boxes']



