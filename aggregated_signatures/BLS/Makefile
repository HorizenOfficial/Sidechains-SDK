SNARK = /project/zen/libsnark
LIBS = -L$(SNARK)/build/libsnark -lsnark -L$(SNARK)/build/depends/libff/libff -lff  -L$(SNARK)/build/depends/ -lgmp -lgmpxx
INCLUDE = -I$(SNARK)/depends/libff -I$(SNARK)/depends/libfqfft  -I$(SNARK) 
COPTS = -DCURVE_ALT_BN128 -DDEBUG=1 -DNO_PROC
COPTS += -g -O1

all:	bls_demo

bls_demo:	bls_demo.o
	g++ -o $@  $< $(LIBS)

bls_demo.o:	bls_demo.cpp
	g++ -c $(COPTS) $(INCLUDE) -o $@  $<


