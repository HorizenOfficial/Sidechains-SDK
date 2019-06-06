from SidechainTestFramework.sc_test_framework import SidechainTestFramework

class SCTest(SidechainTestFramework):
    def add_options(self, parser):
        #empty implementation
        pass
    
    def setup_chain(self):
        #empty implementation
        pass
        
    def setup_network(self, split = False):
        #empty implementation
        pass
    
    def sc_setup_chain(self):
        #SC chain setup
        pass
    
    def sc_setup_network(self, split = False):
        #SC network setup
        pass
        
    def sc_add_options(self, parser):
        #Additional parser options for SC
        pass
        
    def run_test(self):
        #Test logic for SC
        pass
        
if __name__ == "__main__":
    SCTest().main()