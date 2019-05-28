#!/usr/bin/env python2

import requests


class APIClient(object):

	url = ""

	def __init__(self, url):
		self.url = url


	# Debug #
	def debugInfo(self):
		return requests.get(self.url + "/debug/info")

	def debugDelay(self, encodedSignature, count):
		return requests.get(self.url + "/debug/delay/" + encodedSignature + "/" + str(count))

	def debugMyBLocks(self):
		return requests.get(self.url + "/debug/myblocks")

	def debugGenerators(self):
		return requests.get(self.url + "/debug/generators")

	def debugChain(self):
		return requests.get(self.url + "/debug/chain")

	def debugStartMining(self):
		return requests.get(self.url + "/debug/startMining")

	def debugStopMining(self):
		return requests.get(self.url + "/debug/stopMining")

	# Stats #
	def statsTail(self, count):
		return requests.get(self.url + "/stats/tail/" + str(count))

	def statsMeadDifficulty(self, start, end):
		return requests.get(self.url + "/stats/meanDifficulty/" + str(start) + "/" + str(end))

	# Utils #
	def utilsSeed(self):
		return requests.get(self.url + "/utils/seed")

	def utilsLength(self, length):
		return requests.get(self.url + "/utils/seed/" + str(length))
	
	# ToDo: not sure, check it
	def utilsHashBlake2b(self, messageJson):
		return requests.post(self.url + "/utils/hash/blake2b", json = messageJson)

	# Peers #
	def peersAll(self):
		return requests.get(self.url + "/peers/all")

	def peersConnected(self):
		return requests.get(self.url + "/peers/connected")

	def peersBlacklisted(self):
		return requests.get(self.url + "/peers/blacklisted")


	def peersConnect(self, addressJson):
		return requests.post(self.url + "/peers/connect", json = addressJson)

	# NodeView #
	def nodeViewPool(self):
		return requests.get(self.url + "/nodeView/pool")

	def nodeViewOpenSurface(self):
		return requests.get(self.url + "/nodeView/openSurface")

	def nodeViewPersistentModifier(self, encodedId):
		return requests.get(self.url + "/nodeView/persistentModifier/" + str(encodedId))

	# Wallet #
	def walletBalances(self):
		return requests.get(self.url + "/wallet/balances")


	def walletTransfer(self, amount, recipient, fee):
		return requests.post(self.url + "/wallet/transfer", json = {"amount" : amount, "recipient" : recipient, "fee" : fee})
	
	def walletTransferWithDefaultFee(self, amount, recipient):
		return requests.post(self.url + "/wallet/transfer", json = {"amount" : amount, "recipient" : recipient})

	def walletGenerateSecret(self):
		return requests.get(self.url + "/wallet/generateSecret")
