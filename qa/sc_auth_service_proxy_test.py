from SidechainTestFramework.sc_test_framework import SidechainTestFramework

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
    
    def run_test(self):
        sc_node = self.sc_nodes[0]
        print("Debug Info result: " + node._debug_info())
        print("Debug My Blocks result: " + node._debug_myblocks())
        print("Debug Generators result: " + node._debug_generators())
        print("Debug Chain result: " + node._debug_chain())
        print("Debug Start Mining result: " + node._debug_startMining())
        print("Debug Stop Mining result: " + node._debug_stopMining())
        print("Wallet Balances result: " + node._wallet_balances())
        print("Wallet Generate Secret result: " + node._wallet_generateSecret())
        
    
if __name__ == "__main__":
    SidechainAuthServiceProxyTest().main()
    