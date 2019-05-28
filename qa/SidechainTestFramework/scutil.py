import os
import sys

from binascii import hexlify, unhexlify
from base64 import b64encode
from decimal import Decimal, ROUND_DOWN
import json
import random
from util import check_json_precision, bytes_to_hex_str, hex_str_to_bytes, str_to_b64str, _rpchost_to_args, log_filename, \
                 assert_true, assert_false, assert_equal, assert_greater_than, assert_raises
import shutil
import subprocess
import time
import random
import string

from scorexauthproxy import ScorexAuthServiceProxy

def sc_p2p_port(n):
    return 8301 + n
def sc_rpc_port(n):
    return 8201 + n

def sync_sc_blocks(rpc_connections, wait=1, p=False, limit_loop=0):
    """
    Wait until everybody has the same block count or a limit has been exceeded
    """
    #could reuse the one in util if getblockcount RPC call name remains the same
    pass

def sync_sc_mempools(rpc_connections, wait=1):
    """
    Wait until everybody has the same transactions in their memory
    pools
    """
    #could reuse the one in util if getrawmempool RPC call name remains the same
    pass

sidechainclient_processes = {}

def initialize_sc_datadir(dirname, n, genesisData):
    #Put also genesis data in nodes configuration files. Configuration data must be automatically generated and different from the ones generated for the other nodes.
    apiAddress = "127.0.0.1"
    with open('./HybridApp/resources/template.conf','r') as templateFile:
        tmpConfig = templateFile.read()
    configsData = []
    r = random.randint(0, n+1)
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node"+str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)
    config = tmpConfig % {
        'NODE_NUMBER' : n,
        'WALLET_SEED' : ''.join([random.choice(string.ascii_letters + string.digits) for tmp in range(32)]),
        'API_ADDRESS' : "127.0.0.1",
        'API_PORT' : str(apiPort),
        'BIND_PORT' : str(bindPort),
        'KNOWN_PEERS' : "" if n == 0 else "\"" + apiAddress + ":" + str(bindPort-1) + "\"" ,
        'OFFLINE_GENERATION' : "false",
        'GENESIS_DATA': "" if genesisData is None else str(genesisData)
        }
    configsData.append({
        "name" : "node" + str(n),
        "genesisAddresses" : r*n,
        "url" : "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node"+str(n)+".conf"), 'w+') as configFile:
        configFile.write(config)
    return configsData

def initialize_sc_chain(test_dir):
    """
    Create (or copy from cache) a 200-block-long chain and
    4 wallets.
    zend and zen-cli must be in search path.
    """
    pass

def initialize_sc_chain_clean(test_dir, num_nodes, genesisData):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        initialize_sc_datadir(test_dir, i, genesisData)


def start_sc_node(i, dirname):
    """
    Start a SC node and returns API connection to it
    """
    datadir = os.path.join(dirname, "sc_node"+str(i))
    bashcmd = 'java -cp ./HybridApp/resources/twinsChain.jar examples.hybrid.HybridApp ' +  (datadir + ('/node%s.conf' % i))
    sidechainclient_processes[i] = subprocess.Popen(bashcmd.split())
    time.sleep(20) #Temporarily
    url = "http://:@%s:%d" % ('127.0.0.1', sc_rpc_port(i))
    proxy = ScorexAuthServiceProxy(url)
    proxy.url = url # store URL on proxy for info
    return proxy

def start_sc_nodes(num_nodes, dirname):
    """
    Start multiple SC clients, return connections to them
    """
    return [ start_sc_node(i, dirname) for i in range(num_nodes) ]

def check_sc_node(i):
    sidechainclient_processes[i].poll()
    return sidechainclient_processes[i].returncode

def stop_sc_node(node, i):
    #Must be changed with .stop() API Call
    sidechainclient_processes[i].kill()
    del sidechainclient_processes[i]

def stop_sc_nodes(nodes):
    #Must be changed with .stop() API call
    global sidechainclient_processes
    for sc in sidechainclient_processes.values():
        sc.kill()
    del sidechainclient_processes

def set_sc_node_times(nodes, t):
    #could be reused if setmocktime RPC call remains the same
    pass

def wait_sidechainclients():
    # Wait for all bitcoinds to cleanly exit
    for sidechainclient in sidechainclient_processes.values():
        sidechainclient.wait()
    sidechainclient_processes.clear()

def connect_sc_nodes(from_connection, node_num):
    #could be reused if addnode and getpeerinfo rpc calls remain the same
    pass

def connect_sc_nodes_bi(nodes, a, b):
    connect_sc_nodes(nodes[a], b)
    connect_sc_nodes(nodes[b], a)
    
def connect_to_mc_node(sc_node, mc_node, *kwargs):
    pass
