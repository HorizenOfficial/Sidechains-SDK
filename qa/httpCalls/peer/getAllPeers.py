def get_all_peers(sidechain_node):
    response = sidechain_node.node_allPeers()
    return response
