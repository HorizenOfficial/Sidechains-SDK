def get_connected_peers(sidechain_node):
    response = sidechain_node.node_connectedPeers()
    return response
