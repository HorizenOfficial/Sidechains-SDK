import json

#executes a wallet/balance call on an account sidechain
def http_wallet_balance(sidechainNode, evm_address, api_key = None):
      j = {"address": str(evm_address)}
      balance_request = json.dumps(j)

      # balance is in wei
      if (api_key != None):
            response = sidechainNode.wallet_getBalance(balance_request, api_key)
      else:
            response = sidechainNode.wallet_getBalance(balance_request)

      return response["result"]["balance"]



