SNARK = /project/zen/libsnark
LIBS = -L$(SNARK)/build/libsnark -lsnark -L$(SNARK)/build/depends/libff/libff -lff  -L$(SNARK)/build/depends/ -lgmp -lgmpxx
INCLUDE = -I$(SNARK)/depends/libff -I$(SNARK)/depends/libfqfft  -I$(SNARK) 
COPTS = -DCURVE_ALT_BN128 -DDEBUG=1 -DNO_PROC
COPTS += POC_HASHBN
COPTS += -g -O1

all:	bls_demo

.cpp.o:
	g++ -o $@  $< $(LIBS)

.o:
	g++ -c $(COPTS) $(INCLUDE) -o $@  $<

bls_demo:	bls_demo.o

bls_demo.o:	bls_demo.cpp

hashG1:		hashG1.o

hashG1.o:	hashG1.cpp
