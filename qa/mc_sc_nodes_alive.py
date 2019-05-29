from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from util import assert_true

class MCSCNodesAlive(SidechainTestFramework):
    def run_test(self):
        i = 0
        for node in self.nodes:
            res = node.getinfo()
            assert_true(res is not None, "MC node {0} not alive !".format(i))
            print("MC node {0} alive !\nResponse to getinfo RPC call: {1} ".format(i, res))
            i = i + 1
        i = 0
        '''Dummy (POST) call to check if SC node is alive. Note that Scorex API requires to specify a path to call a method. 
        It's not possible to do as MC node because slashes in a method name would result in a syntax error, so we use "_" instead
        and replace them with "/" later'''
        for sc_node in self.sc_nodes:
            input = "\"test\""
            res = sc_node._utils_hash_blake2b(input)
            assert_true(res is not None, "SC node {0} not alive !".format(i))
            print("SC node {0} alive !\nResponse to hashblake2b API call with input {1}: {2}".format(i, input , res))
            i = i + 1
            
if __name__ == "__main__":
    MCSCNodesAlive().main()
    