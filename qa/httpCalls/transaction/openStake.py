import json
#execute a transaction/openStake call
def openStake(sidechainNode, boxid, address, forger_index, fee, format = False):
      j = { 
            "transactionInput": 
            { 
                "boxId": boxid 
            },
            "regularOutputProposition": address,
            "forgerListIndex": forger_index,
            "fee": fee,
            "format": format 
      }
      request = json.dumps(j)
      response = sidechainNode.transaction_openStake(request)
      return response["result"]