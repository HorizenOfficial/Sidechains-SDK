# Copyright (c) 2014 The Bitcoin Core developers
# Distributed under the MIT software license, see the accompanying
# file COPYING or https://www.opensource.org/licenses/mit-license.php .


#
# Helpful routines for regression testing
#

# Add python-bitcoinrpc to module search path:
import codecs
import logging
import os
import sys

from binascii import hexlify, unhexlify
from base64 import b64encode
from decimal import Decimal, ROUND_DOWN
import json
import random
import shutil
import subprocess
import time
import re

from test_framework.authproxy import AuthServiceProxy

certificate_field_config_csw_enabled = [255, 255]

certificate_field_config_csw_disabled = []
certificate_with_key_rotation_field_config = [255]

COIN = 100000000 # 1 zen in zatoshis

def p2p_port(n):
    return 11000 + n + os.getpid()%999
def rpc_port(n):
    return 12000 + n + os.getpid()%999
def websocket_port_by_mc_node_index(n):
    return 13000 + n + os.getpid()%999

def check_json_precision():
    """Make sure json library being used does not lose precision converting BTC values"""
    n = Decimal("20000000.00000003")
    satoshis = int(json.loads(json.dumps(float(n)))*1.0e8)
    if satoshis != 2000000000000003:
        raise RuntimeError("JSON encode/decode loses precision")

def bytes_to_hex_str(byte_str):
    return hexlify(byte_str).decode('ascii')

def hex_str_to_bytes(hex_str):
    return unhexlify(hex_str.encode('ascii'))

def str_to_b64str(string):
    return b64encode(string.encode('utf-8')).decode('ascii')

def sync_blocks(rpc_connections, wait=1):
    """
    Wait until everybody has the same block count
    """
    while True:
        counts = [ x.getblockcount() for x in rpc_connections ]
        if counts == [ counts[0] ]*len(counts):
            break
        time.sleep(wait)

def sync_mempools(rpc_connections, wait=1):
    """
    Wait until everybody has the same transactions in their memory
    pools
    """
    while True:
        pool = set(rpc_connections[0].getrawmempool())
        num_match = 1
        for i in range(1, len(rpc_connections)):
            if set(rpc_connections[i].getrawmempool()) == pool:
                num_match = num_match+1
        if num_match == len(rpc_connections):
            break
        time.sleep(wait)

bitcoind_processes = {}

def initialize_datadir(dirname, n, websocket_port=None):
    datadir = os.path.join(dirname, "node"+str(n))

    if not os.path.isdir(datadir):
        os.makedirs(datadir)
    with open(os.path.join(datadir, "zen.conf"), 'w') as f:
        f.write("regtest=1\n")
        f.write("showmetrics=0\n")
        f.write("rpcuser=rt\n")
        f.write("rpcpassword=rt\n")
        f.write("port="+str(p2p_port(n))+"\n")
        f.write("rpcport="+str(rpc_port(n))+"\n")
        f.write("listenonion=0\n")
        f.write("debug=ws\n")
        if(websocket_port is not None):
            f.write("wsport={0}\n".format(websocket_port))
    return datadir

def initialize_chain(test_dir):
    """
    Create (or copy from cache) a 200-block-long chain and
    4 wallets.
    bitcoind and bitcoin-cli must be in search path.
    """

    if not os.path.isdir(os.path.join("cache", "node0")):
        devnull = open("/dev/null", "w+")
        # Create cache directories, run bitcoinds:
        for i in range(4):
            datadir=initialize_datadir("cache", i, [])
            args = [ os.getenv("BITCOIND", "bitcoind"), "-keypool=1", "-datadir="+datadir, "-discover=0" ]
            if i > 0:
                args.append("-connect=127.0.0.1:"+str(p2p_port(0)))
            bitcoind_processes[i] = subprocess.Popen(args)
            if os.getenv("PYTHON_DEBUG", ""):
                logging.debug("initialize_chain: bitcoind started, calling bitcoin-cli -rpcwait getblockcount")
            subprocess.check_call([ os.getenv("BITCOINCLI", "bitcoin-cli"), "-datadir="+datadir,
                                    "-rpcwait", "getblockcount"], stdout=devnull)
            if os.getenv("PYTHON_DEBUG", ""):
                logging.debug("initialize_chain: bitcoin-cli -rpcwait getblockcount completed")
        devnull.close()
        rpcs = []
        for i in range(4):
            try:
                url = "http://rt:rt@127.0.0.1:%d"%(rpc_port(i),)
                rpcs.append(AuthServiceProxy(url))
            except:
                sys.stderr.write("Error connecting to "+url+"\n")
                sys.exit(1)

        # Create a 200-block-long chain; each of the 4 nodes
        # gets 25 mature blocks and 25 immature.
        # blocks are created with timestamps 10 minutes apart, starting
        # at 1 Jan 2014
        block_time = 1388534400
        for i in range(2):
            for peer in range(4):
                for j in range(25):
                    set_node_times(rpcs, block_time)
                    rpcs[peer].generate(1)
                    block_time += 10*60
                # Must sync before next peer starts generating blocks
                sync_blocks(rpcs)

        # Shut them down, and clean up cache directories:
        stop_nodes(rpcs)
        wait_bitcoinds()
        for i in range(4):
            os.remove(log_filename("cache", i, "debug.log"))
            os.remove(log_filename("cache", i, "db.log"))
            os.remove(log_filename("cache", i, "peers.dat"))
            os.remove(log_filename("cache", i, "fee_estimates.dat"))

    for i in range(4):
        from_dir = os.path.join("cache", "node"+str(i))
        to_dir = os.path.join(test_dir,  "node"+str(i))
        shutil.copytree(from_dir, to_dir)
        initialize_datadir(test_dir, i) # Overwrite port/rpcport in zcash.conf

def initialize_chain_clean(test_dir, num_nodes):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        initialize_datadir(test_dir, i, websocket_port_by_mc_node_index(i))

def _rpchost_to_args(rpchost):
    '''Convert optional IP:port spec to rpcconnect/rpcport args'''
    if rpchost is None:
        return []

    match = re.match('(\[[0-9a-fA-f:]+\]|[^:]+)(?::([0-9]+))?$', rpchost)
    if not match:
        raise ValueError('Invalid RPC host spec ' + rpchost)

    rpcconnect = match.group(1)
    rpcport = match.group(2)

    if rpcconnect.startswith('['): # remove IPv6 [...] wrapping
        rpcconnect = rpcconnect[1:-1]

    rv = ['-rpcconnect=' + rpcconnect]
    if rpcport:
        rv += ['-rpcport=' + rpcport]
    return rv

def start_node(i, dirname, extra_args=None, rpchost=None, timewait=None, binary=None):
    """
    Start a bitcoind and return RPC connection to it
    """
    datadir = os.path.join(dirname, "node"+str(i))
    if binary is None:
        binary = os.getenv("BITCOIND", "zend")
    if not os.path.isfile(binary):
        raise Exception('no such file: ' + binary)

    args = [ binary, "-datadir="+datadir, "-keypool=1", "-discover=0", "-rest", "-websocket", "-logtimemicros"]
    if extra_args is not None: args.extend(extra_args)
    bitcoind_processes[i] = subprocess.Popen(args)
    devnull = open(os.devnull, "w+")
    if os.getenv("PYTHON_DEBUG", ""):
        logging.debug("start_node: bitcoind started, calling bitcoin-cli -rpcwait getblockcount")
    subprocess.check_call([ os.getenv("BITCOINCLI", "bitcoin-cli"), "-datadir="+datadir] +
                          _rpchost_to_args(rpchost)  +
                          ["-rpcwait", "getblockcount"], stdout=devnull)
    if os.getenv("PYTHON_DEBUG", ""):
        logging.debug("start_node: calling bitcoin-cli -rpcwait getblockcount returned")
    devnull.close()
    url = "http://rt:rt@%s:%d" % (rpchost or '127.0.0.1', rpc_port(i))
    if timewait is not None:
        proxy = AuthServiceProxy(url, timeout=timewait)
    else:
        proxy = AuthServiceProxy(url)
    proxy.url = url # store URL on proxy for info
    return proxy

def start_nodes(num_nodes, dirname, extra_args=None, rpchost=None, binary=None):
    """
    Start multiple bitcoinds, return RPC connections to them
    """
    if extra_args is None: extra_args = [ None for i in range(num_nodes) ]
    if binary is None: binary = [ None for i in range(num_nodes) ]
    return [ start_node(i, dirname, extra_args[i], rpchost, binary=binary[i]) for i in range(num_nodes) ]

def log_filename(dirname, n_node, logname):
    return os.path.join(dirname, "node"+str(n_node), "regtest", logname)

def check_node(i):
    bitcoind_processes[i].poll()
    return bitcoind_processes[i].returncode

def stop_node(node, i):
    node.stop()
    bitcoind_processes[i].wait()
    del bitcoind_processes[i]

def stop_nodes(nodes):
    for node in nodes:
        node.stop()
    del nodes[:] # Emptying array closes connections as a side effect

def set_node_times(nodes, t):
    for node in nodes:
        node.setmocktime(t)

def wait_bitcoinds():
    # Wait for all bitcoinds to cleanly exit
    for bitcoind in bitcoind_processes.values():
        bitcoind.wait()
    bitcoind_processes.clear()

def connect_nodes(from_connection, node_num):
    ip_port = "127.0.0.1:"+str(p2p_port(node_num))
    from_connection.addnode(ip_port, "onetry")
    # poll until version handshake complete to avoid race conditions
    # with transaction relaying
    while any(peer['version'] == 0 for peer in from_connection.getpeerinfo()):
        time.sleep(0.1)

def connect_nodes_bi(nodes, a, b):
    connect_nodes(nodes[a], b)
    connect_nodes(nodes[b], a)

def disconnect_nodes(from_connection, node_num):
    ip_port = "127.0.0.1:"+str(p2p_port(node_num))
    from_connection.disconnectnode(ip_port)
    # poll until version handshake complete to avoid race conditions
    # with transaction relaying
    while any(peer['version'] == 0 for peer in from_connection.getpeerinfo()):
        time.sleep(0.1)

def disconnect_nodes_bi(nodes, a, b):
    disconnect_nodes(nodes[a], b)
    disconnect_nodes(nodes[b], a)

def find_output(node, txid, amount):
    """
    Return index to output of txid with value amount
    Raises exception if there is none.
    """
    txdata = node.getrawtransaction(txid, 1)
    for i in range(len(txdata["vout"])):
        if txdata["vout"][i]["value"] == amount:
            return i
    raise RuntimeError("find_output txid %s : %s not found"%(txid,str(amount)))


def gather_inputs(from_node, amount_needed, confirmations_required=1):
    """
    Return a random set of unspent txouts that are enough to pay amount_needed
    """
    assert(confirmations_required >=0)
    utxo = from_node.listunspent(confirmations_required)
    random.shuffle(utxo)
    inputs = []
    total_in = Decimal("0.00000000")
    while total_in < amount_needed and len(utxo) > 0:
        t = utxo.pop()
        total_in += t["amount"]
        inputs.append({ "txid" : t["txid"], "vout" : t["vout"], "address" : t["address"] } )
    if total_in < amount_needed:
        raise RuntimeError("Insufficient funds: need %d, have %d"%(amount_needed, total_in))
    return (total_in, inputs)

def make_change(from_node, amount_in, amount_out, fee):
    """
    Create change output(s), return them
    """
    outputs = {}
    amount = amount_out+fee
    change = amount_in - amount
    if change > amount*2:
        # Create an extra change output to break up big inputs
        change_address = from_node.getnewaddress()
        # Split change in two, being careful of rounding:
        outputs[change_address] = Decimal(change/2).quantize(Decimal('0.00000001'), rounding=ROUND_DOWN)
        change = amount_in - amount - outputs[change_address]
    if change > 0:
        outputs[from_node.getnewaddress()] = change
    return outputs

def send_zeropri_transaction(from_node, to_node, amount, fee):
    """
    Create&broadcast a zero-priority transaction.
    Returns (txid, hex-encoded-txdata)
    Ensures transaction is zero-priority by first creating a send-to-self,
    then using its output
    """

    # Create a send-to-self with confirmed inputs:
    self_address = from_node.getnewaddress()
    (total_in, inputs) = gather_inputs(from_node, amount+fee*2)
    outputs = make_change(from_node, total_in, amount+fee, fee)
    outputs[self_address] = float(amount+fee)

    self_rawtx = from_node.createrawtransaction(inputs, outputs)
    self_signresult = from_node.signrawtransaction(self_rawtx)
    self_txid = from_node.sendrawtransaction(self_signresult["hex"], True)

    vout = find_output(from_node, self_txid, amount+fee)
    # Now immediately spend the output to create a 1-input, 1-output
    # zero-priority transaction:
    inputs = [ { "txid" : self_txid, "vout" : vout } ]
    outputs = { to_node.getnewaddress() : float(amount) }

    rawtx = from_node.createrawtransaction(inputs, outputs)
    signresult = from_node.signrawtransaction(rawtx)
    txid = from_node.sendrawtransaction(signresult["hex"], True)

    return (txid, signresult["hex"])

def random_zeropri_transaction(nodes, amount, min_fee, fee_increment, fee_variants):
    """
    Create a random zero-priority transaction.
    Returns (txid, hex-encoded-transaction-data, fee)
    """
    from_node = random.choice(nodes)
    to_node = random.choice(nodes)
    fee = min_fee + fee_increment*random.randint(0,fee_variants)
    (txid, txhex) = send_zeropri_transaction(from_node, to_node, amount, fee)
    return (txid, txhex, fee)

def random_transaction(nodes, amount, min_fee, fee_increment, fee_variants):
    """
    Create a random transaction.
    Returns (txid, hex-encoded-transaction-data, fee)
    """
    from_node = random.choice(nodes)
    to_node = random.choice(nodes)
    fee = min_fee + fee_increment*random.randint(0,fee_variants)

    (total_in, inputs) = gather_inputs(from_node, amount+fee)
    outputs = make_change(from_node, total_in, amount, fee)
    outputs[to_node.getnewaddress()] = float(amount)

    rawtx = from_node.createrawtransaction(inputs, outputs)
    signresult = from_node.signrawtransaction(rawtx)
    txid = from_node.sendrawtransaction(signresult["hex"], True)

    return (txid, signresult["hex"], fee)

def fail(message=""):
    raise AssertionError(message)

def assert_equal(expected, actual, message=""):
    if expected != actual:
        if message:
            message = "; %s" % message 
        raise AssertionError("(left == right)%s\n  left: <%s>\n right: <%s>" % (message, str(expected), str(actual)))

def assert_not_equal(expected, actual, message=""):
    if expected == actual:
        if message:
            message = "; %s" % message
        raise AssertionError("(left != right)%s\n  left: <%s>\n right: <%s>" % (message, str(expected), str(actual)))


def assert_true(condition, message = ""):
    if not condition:
        raise AssertionError(message)
        
def assert_false(condition, message = ""):
    assert_true(not condition, message)

def assert_greater_than(thing1, thing2):
    if thing1 <= thing2:
        raise AssertionError("%s <= %s"%(str(thing1),str(thing2)))

def assert_raises(exc, fun, *args, **kwds):
    try:
        fun(*args, **kwds)
    except exc:
        pass
    except Exception as e:
        raise AssertionError("Unexpected exception raised: "+type(e).__name__)
    else:
        raise AssertionError("No exception raised")

def fail(message=""):
    raise AssertionError(message)


# Returns an async operation result
def wait_and_assert_operationid_status_result(node, myopid, in_status='success', in_errormsg=None, timeout=300):
    logging.info('waiting for async operation {}'.format(myopid))
    result = None
    for _ in range(1, timeout):
        results = node.z_getoperationresult([myopid])
        if len(results) > 0:
            result = results[0]
            break
        time.sleep(1)

    assert_true(result is not None, "timeout occured")
    status = result['status']

    debug = os.getenv("PYTHON_DEBUG", "")
    if debug:
        logging.debug('...returned status: {}'.format(status))

    errormsg = None
    if status == "failed":
        errormsg = result['error']['message']
        if debug:
            logging.debug('...returned error: {}'.format(errormsg))
        assert_equal(in_errormsg, errormsg)

    assert_equal(in_status, status, "Operation returned mismatched status. Error Message: {}".format(errormsg))

    return result


# Returns txid if operation was a success or None
def wait_and_assert_operationid_status(node, myopid, in_status='success', in_errormsg=None, timeout=300):
    result = wait_and_assert_operationid_status_result(node, myopid, in_status, in_errormsg, timeout)
    if result['status'] == "success":
        return result['result']['txid']
    else:
        return None

# Find a coinbase address on the node, filtering by the number of UTXOs it has.
# If no filter is provided, returns the coinbase address on the node containing
# the greatest number of spendable UTXOs.
# The default cached chain has one address per coinbase output.
def get_coinbase_address(node, expected_utxos=None):
    addrs = [utxo['address'] for utxo in node.listunspent() if utxo['generated']]
    assert(len(set(addrs)) > 0)

    if expected_utxos is None:
        addrs = [(addrs.count(a), a) for a in set(addrs)]
        return sorted(addrs, reverse=True)[0][1]

    addrs = [a for a in set(addrs) if addrs.count(a) == expected_utxos]
    assert(len(addrs) > 0)
    return addrs[0]

"""
Perform SC creation, mine mainchain blocks, create genesis info.
Parameters:
 - mainchain_node: the mainchain node
 - public_key: a public key
 - withdrawal_epoch_length
 - forward_transfer_amount: the amount of the forward transfer.
 
Output: an array of two information:
 - the genesis info used for start the sidechain node
 - the height of the mainchain block at which the sidechain has been created (useful for future checks of mainchain block reference inclusion)
 - created sidechain id

"""
def initialize_new_sidechain_in_mainchain(mainchain_node, withdrawal_epoch_length, public_key, forward_transfer_amount,
                                          vrf_public_key, gen_sys_constant, cert_vk, csw_vk, btr_data_length,
                                          sc_creation_version, is_csw_enabled, type_of_circuit):
    number_of_blocks_to_enable_sc_logic = 479
    number_of_blocks = mainchain_node.getblockcount()
    diff = number_of_blocks_to_enable_sc_logic - number_of_blocks
    if diff > 1:
        logging.info("Generating {} blocks for reaching needed mc fork point...".format(diff))
        mainchain_node.generate(diff)

    if sc_creation_version <= 1:
        assert type_of_circuit == 0
    else:
        assert type_of_circuit == 1

    if type_of_circuit == 1:
        assert is_csw_enabled is False

    custom_creation_data = vrf_public_key

    if type_of_circuit == 0:
        fe_certificate_field_configs = certificate_field_config_csw_disabled

        if is_csw_enabled:
            fe_certificate_field_configs = certificate_field_config_csw_enabled
    else:
        fe_certificate_field_configs = certificate_with_key_rotation_field_config

    bitvector_certificate_field_configs = []  # [[254*8, 254*8]]
    ft_min_amount = 0
    btr_fee = 0

    sc_create_args = {
        "version": sc_creation_version,
        "withdrawalEpochLength": withdrawal_epoch_length,
        "toaddress": public_key,
        "amount": forward_transfer_amount,
        "wCertVk": cert_vk,
        "customData": custom_creation_data,
        "constant": gen_sys_constant,
        "wCeasedVk": csw_vk,
        "vFieldElementCertificateFieldConfig": fe_certificate_field_configs,
        "vBitVectorCertificateFieldConfig": bitvector_certificate_field_configs,
        "forwardTransferScFee": ft_min_amount,
        "mainchainBackwardTransferScFee": btr_fee,
        "mainchainBackwardTransferRequestDataLength": btr_data_length
    }
    sc_create_res = mainchain_node.sc_create(sc_create_args)
    transaction_id = sc_create_res["txid"]
    sidechain_id = sc_create_res["scid"]
    logging.info("Id of the sidechain transaction creation: {0}".format(transaction_id))
    logging.info("Sidechain created with Id: {0}".format(sidechain_id))

    mc_block_hash = mainchain_node.generate(1)[0]
    # For docs update
    tx_json_str = json.dumps(mainchain_node.gettransaction(transaction_id), indent=4, default=str)
    mc_block_hex = mainchain_node.getblock(mc_block_hash, False)
    #logging.info(mc_block_hex)

    return [mainchain_node.getscgenesisinfo(sidechain_id), mainchain_node.getblockcount(), sidechain_id]


"""
Perform forward transfer to SC, mine mainchain blocks, get sc info.
Parameters:
 - sidechain_id: id of the sidechain to be created
 - mainchain_node: the mainchain node
 - public_key: a public key
 - forward_transfer_amount: the amount of the forward transfer.

Output: an array of two information:
 - the info for sidechain
 - the height of the mainchain block at which the forward transfer was created

"""


def forward_transfer_to_sidechain(sidechain_id, mainchain_node,
                                  public_key, forward_transfer_amount, mc_return_address, generate_block=True):

    ft_args = [{
        "toaddress": public_key,
        "amount": forward_transfer_amount,
        "scid": sidechain_id,
        "mcReturnAddress": mc_return_address
    }]
    transaction_id = mainchain_node.sc_send(ft_args)
    logging.info("FT transaction id: {0}".format(transaction_id))

    if generate_block:
        mainchain_node.generate(1)
    return [mainchain_node.getscinfo(sidechain_id), mainchain_node.getblockcount()]


def swap_bytes(input_buf):
    return codecs.encode(codecs.decode(input_buf, 'hex')[::-1], 'hex').decode()


def get_spendable(mc_node, min_amount):
    # get a UTXO in node's wallet with minimal amount
    utx = False
    listunspent = mc_node.listunspent()
    for aUtx in listunspent:
        if aUtx['amount'] > min_amount:
            utx = aUtx
            change = aUtx['amount'] - min_amount
            break

    if utx == False:
        logging.info(listunspent)

    assert_equal(utx!=False, True)
    return utx, change
