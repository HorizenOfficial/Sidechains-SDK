#executes a wallet/balance call
def http_wallet_balance(sidechainNode, api_key = None):
      if (api_key != None):
            response = sidechainNode.wallet_coinsBalance({}, api_key)
      else:
            response = sidechainNode.wallet_coinsBalance()
      return response['result']['balance']



