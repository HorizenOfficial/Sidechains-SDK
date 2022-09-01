def http_block_forging_info(sidechainNode):
      response = sidechainNode.block_forgingInfo()
      return response['result']
