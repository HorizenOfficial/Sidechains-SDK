#executes a  block/best call
def http_block_best(sidechainNode):
      response = sidechainNode.block_best()
      return response['result']['block']

def http_block_best_height(sidechainNode):
      response = sidechainNode.block_best()
      return response['result']['height']



