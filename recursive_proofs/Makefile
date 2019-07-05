#SNARK = /home/vmuser/src/libsnark
SNARK = /usr/local/src/libsnark

LIBS = -L$(SNARK)/build/libsnark -lsnark -L$(SNARK)/build/depends/libff/libff -lff  -L$(SNARK)/build/depends/ -lcrypto -lgmp -lgmpxx
INCLUDE = -I$(SNARK)/depends/libff -I$(SNARK)/depends/libfqfft  -I$(SNARK) 
COPTS = -DCURVE_ALT_BN128 -DDEBUG -DNO_PROC
COPTS += -std=c++1z -g -O1

join2proofs.o:		join2proofs.hpp join2proofs.cpp

test_rverifier.o:	test_rverifier.cpp

test_pexp.o:		test_pexp.cpp pexp.hpp pexp.tcc fp_gadgets.hpp

test_oncurve.o:		test_oncurve.cpp oncurve.h oncurve.tcc

test_HTC.o:		test_HTC.cpp HTC.h HTC.tcc

.cpp.o:
	g++ -c $(COPTS) $(INCLUDE) -o $@  $<

.o:
	g++ -o $@  $< $(LIBS)



