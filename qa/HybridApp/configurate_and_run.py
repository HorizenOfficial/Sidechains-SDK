#!/usr/bin/env python2

import os
import shutil
import subprocess
import time
import tempfile
from nodesactivity import NodesActivity

# initial configs values
nodes = 3
apiAddress = "127.0.0.1"
apiPort = 8200
bindPort = 8300

def clear(tmpdir):
	print('Removing old settings...')
	shutil.rmtree(tmpdir, ignore_errors=True)
	print('Removing old data...')
	shutil.rmtree('/tmp/scorex_test', ignore_errors=True)


def getKnownPeers(node):
	if node == 0:
		return ""
	return "\"" + apiAddress + ":" + str(bindPort) + "\""
	
def getOfflineGeneration(node):
	return "false"

def getWalletSeed(nodeNumber):
	if nodeNumber < 3:
		return "minerNode" + str(nodeNumber + 1)
	return "node" + str(nodeNumber + 1)

def getGenesisAddresses(nodeNumber):
	if nodeNumber < 2:
		return 19
	elif nodeNumber == 2:
		return 9
	return 0

def createConfigs(nodeNumber, tmpdir):
	with open('./template.conf','r') as templateFile:
		tmpConfig = templateFile.read()
	configsData = []
	for i in range(0, nodeNumber):
		datadir = os.path.join(tmpdir, "sc_node"+str(i))
		if not os.path.isdir(datadir):
			os.makedirs(datadir)
		config = tmpConfig % {
			'NODE_NUMBER' : i,
			'WALLET_SEED' : getWalletSeed(i),
			'API_ADDRESS' : apiAddress,
			'API_PORT' : apiPort + i,
			'BIND_PORT' : bindPort + i,
			'KNOWN_PEERS' : getKnownPeers(i),
			'OFFLINE_GENERATION' : getOfflineGeneration(i)
			}
		configsData.append({
			"name" : "node" + str(i),
			"genesisAddresses" : getGenesisAddresses(i),
			"url" : "http://" + apiAddress + ":" + str(apiPort + i)
		})
		with open(os.path.join(datadir, "node"+str(i)+".conf"), 'w+') as configFile:
			configFile.write(config)
	return configsData

def runScorexNodes(nodeNumber, tmpdir):
	bashCmd = 'gnome-terminal -x java -cp twinsChain.jar examples.hybrid.HybridApp ' + tmpdir+'/sc_node%(NODE_NUMBER)s/node%(NODE_NUMBER)s.conf'
	for i in range(0, nodeNumber):
		cmd = bashCmd % {'NODE_NUMBER' : i}
		print(cmd)
		subprocess.Popen(cmd.split(), stdout=subprocess.PIPE) 

tmpdir = tempfile.mkdtemp(prefix="sc_test")
print(tmpdir)		
confData = createConfigs(nodes, tmpdir)
runScorexNodes(nodes, tmpdir)

time.sleep(40)
na = NodesActivity(confData)
