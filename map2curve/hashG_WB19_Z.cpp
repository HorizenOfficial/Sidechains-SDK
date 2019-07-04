/* Hash-to-curve function, to be used with libsnark "MNT curves"
 * 2019 Vadym Fedyukovych
 *
 * SWU method hashing to MNT-4 curve of libsnark
 * Wahby-Boneh 2019, generating "Z" parameter
 *
 * https://github.com/cfrg/draft-irtf-cfrg-hash-to-curve/blob/master/draft-irtf-cfrg-hash-to-curve.md
 * https://eprint.iacr.org/2019/403
 */
#include <libff/algebra/curves/mnt/mnt4/mnt4_pp.hpp>
#include <libff/algebra/curves/mnt/mnt4/mnt4_g1.hpp>

using namespace libff;
using namespace std;

mnt4_Fq curve_f(const mnt4_Fq &x) {
  return x*x*x + mnt4_G1::coeff_a*x + mnt4_G1::coeff_b;
}

bool is_sq_legendre(mnt4_Fq x) {
  mnt4_Fq xpwr = x^(mnt4_Fq::euler);
  return xpwr == mnt4_Fq::one();
}

int main() {
  mnt4_G1 P;
  mnt4_pp::init_public_params();
  mnt4_Fq Z, x, xd, gx;

  for(int cnt_try=1; cnt_try++; ) {
    Z = mnt4_Fq::random_element();
    if(Z == mnt4_Fq::zero())
      continue;
    if(is_sq_legendre(Z))
      continue;

    xd = Z * mnt4_G1::coeff_a;
    x = mnt4_G1::coeff_a * xd.inverse();
    gx = curve_f(x);
    if(!is_sq_legendre(gx))
      continue;

    cout << cnt_try << endl
         << Z << endl;
    break;
  }
  return 0;
}
