/* BLS signature scheme, on top of libsnark "alt BN curve"
 * "small" G1: message and signature; "larger" G2: public key
 * 2019 Vadym Fedyukovych
 * todo: hash the message to-G1
 */
#include <libff/algebra/curves/alt_bn128/alt_bn128_pp.hpp>

using namespace libff;
using namespace std;

int main() {
  alt_bn128_G1 P1, Sig, hm;
  alt_bn128_G2 Q2, pub_key;
  alt_bn128_GT Tsig, Tk;
  alt_bn128_Fr priv_key;

  // setup: generators in G1 and G2
  alt_bn128_pp::init_public_params();
  P1 = alt_bn128_G1::one();
  Q2 = alt_bn128_G2::one();

  // private-public keys
  priv_key = alt_bn128_Fr::random_element();
  pub_key = priv_key * Q2;

  // expected: hash message to G1, produce hm
  hm = alt_bn128_Fr::random_element() * P1;
  // sign
  Sig = priv_key * hm;

  // verify
  Tsig = alt_bn128_reduced_pairing(Sig, Q2);
  Tk = alt_bn128_reduced_pairing(hm, pub_key);
  if(Tsig == Tk)
    cout << "BLS signature verified ok" << endl;
  else
    cout << "BLS signature not verified" << endl;

  Sig.print();
  pub_key.print();
  return 0;
}

