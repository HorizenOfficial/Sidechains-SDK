from SidechainTestFramework.sc_test_framework import SidechainTestFramework
import time

'''Check for some simple methods if changing GET in POST requests in Scorex Hybrid API would result in no errors.
(Modified twinschain.jar still not uploaded)'''
class SidechainAuthServiceProxyTest(SidechainTestFramework):
    def add_options(self, parser):
        #empty implementation
        pass
    
    def setup_chain(self):
        #empty implementation
        pass
        
    def setup_network(self, split = False):
        #empty implementation
        pass
    
    def sc_generate_genesis_data(self):
        pass
    
    def run_test(self):
        node = self.sc_nodes[0]
        print("Debug Info result: " + str(node.debug_info()))
        print("Debug Start Mining result: " + str(node.debug_startMining()))
        time.sleep(25)
        print("Debug Info result: " + str(node.debug_info()))
        '''print("Debug My Blocks result: " + str(node._debug_myblocks()))
        print("Debug Generators result: " + str(node._debug_generators()))
        print("Debug Chain result: " + str(node._debug_chain()))
        print("Debug Stop Mining result: " + str(node._debug_stopMining()))
        print("Wallet Balances result: " + str(node._wallet_balances()))
        print("Wallet Generate Secret result: " + str(node._wallet_generateSecret()))'''
        
    
if __name__ == "__main__":
    SidechainAuthServiceProxyTest().main()
    