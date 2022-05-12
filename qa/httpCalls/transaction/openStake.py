import json
#execute a transaction/createopenStakeTransaction call
def createOpenStakeTransaction(sidechain_node, boxid, address, forger_index, fee, format = False, automatic_send = False):
      j = { 
            "transactionInput": 
            { 
                "boxId": boxid 
            },
            "regularOutputProposition": address,
            "forgerIndex": forger_index,
            "fee": fee,
            "format": format,
            "automaticSend": automatic_send
      }
      request = json.dumps(j)
      response = sidechain_node.transaction_createOpenStakeTransaction(request)
      return response["result"]

#execute a transaction/createOpenStakeTransactionSimplified call
def createOpenStakeTransactionSimplified(sidechain_node, forger_proposition, forger_index, fee, format = False, automatic_send = False):
      j = {
            "forgerProposition": forger_proposition,
            "forgerIndex": forger_index,
            "fee": fee,
            "format": format,
            "automaticSend": automatic_send
      }
      request = json.dumps(j)
      response = sidechain_node.transaction_createOpenStakeTransactionSimplified(request)
      return response["result"]