import json

#executes a  transaction/findById call
def http_transaction_findById(sidechainNode, txid, format = True):
      j = {\
            "transactionId": txid, \
            "format": format \
      }
      request = json.dumps(j)
      response = sidechainNode.transaction_findById(request)
      return response["result"]



