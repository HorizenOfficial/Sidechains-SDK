#!/usr/bin/env python2

import threading
import json
import time
from random import randint
from apiclient import APIClient

lock = threading.Lock()
ADDRESSES_POOL_MAX_SIZE = 50

class NodesActivity(object):

	def __init__(self, nodesInfo):
		self.addressesPool = []
		self.addressesPool.append('asda')
		self.addressesPool.pop()
		for info in nodesInfo:
			threading.Thread(target = self.startNodeActivity, args = (info["name"], info["url"], info["genesisAddresses"])).start()

	def printStat(self, name, msg):
		print(name + ": " + msg)

	def generateGenesisAddresses(self, client, name, genesisAddresses):
		try:
			for i in range(0, genesisAddresses):
				client.walletGenerateSecret()
			self.printStat(name, "genesis transaction addresses generated.") 
			return True
		except Exception as e:
			self.printStat(name, str(e))
			return False

	def startNodeActivity(self, name, url, genesisAddresses):
		client = APIClient(url)

		if not self.generateGenesisAddresses(client, name, genesisAddresses):
			return
		try:
			response = client.walletBalances()
			self.printStat(name, str(response.json()["totalBalance"]))
			response = client.walletGenerateSecret()
			self.printStat(name, json.dumps(response.json(), indent=4))
			
			# start mining/forging
			time.sleep(randint(2, 10))
			self.printStat(name, "Mining/forging started: " + str(client.debugStartMining().json()))
			if name == "node2":
				return

			# generate addresses and send random transactions
			while True:
				time.sleep(randint(50, 1000) * 0.01)
				with lock:
					for i in range(0, randint(0, 3)):
						if len(self.addressesPool) < ADDRESSES_POOL_MAX_SIZE:
							self.addressesPool.append(str(client.walletGenerateSecret().json()["newkey"]))

					if len(self.addressesPool) == 0:
						continue

					walletBalance = int(client.walletBalances().json()["totalBalance"])
					if walletBalance < 1000:
						continue

					amount = randint(100, walletBalance / 100)
					fee = randint(10, 100)
					recipient = self.addressesPool.pop(0)
					response = client.walletTransfer(amount, recipient, fee)
					self.printStat(name, "Send {} coins with fee {} to {} ".format(amount, fee, recipient) + str(response))


		except Exception as e:
			self.printStat(name, str(e))
			return
	

