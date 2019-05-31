from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from util import assert_equal
import time

class SidechainNodeBlockGenerationTest(SidechainTestFramework):
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
        print("Node 0 starts mining...")
        node = self.sc_nodes[0]
        info = node.debug_info()
        assert_equal(int(info["height"]),2)
        print("Mine 1 PoW and forge 1 PoS block...")
        node.debug_startMining()
        time.sleep(25)
        info = node.debug_info()
        assert_equal(int(info["height"]),4)
        
if __name__ == "__main__":
    SidechainNodeBlockGenerationTest().main()
    