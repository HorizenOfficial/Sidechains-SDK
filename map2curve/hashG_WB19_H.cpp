/* Hash-to-curve function, to be used with libsnark "MNT curves"
 * 2019 Vadym Fedyukovych
 *
 * SWU method hashing to MNT-4 curve of libsnark
 * Wahby-Boneh 2019, hash function, hardcoded "Z" parameter
 * Still needs deterministic Tonelli-Shanks algorithm
 *
 * https://github.com/cfrg/draft-irtf-cfrg-hash-to-curve/blob/master/draft-irtf-cfrg-hash-to-curve.md
 * https://eprint.iacr.org/2019/403
 */
#include <libff/algebra/curves/mnt/mnt4/mnt4_pp.hpp>
#include <libff/algebra/curves/mnt/mnt4/mnt4_g1.hpp>

using namespace libff;
using namespace std;

#ifdef TESTCASE_MNT4
mnt4_Fq HashToBase(const void *msg) {
  return mnt4_Fq::random_element();
}
#else
mnt4_Fq HashToBase(const void *msg);
#endif

mnt4_Fq curve_f(const mnt4_Fq &x) {
  return x*x*x + mnt4_G1::coeff_a*x + mnt4_G1::coeff_b;
}

bool is_sq_legendre(const mnt4_Fq &x) {
  mnt4_Fq xpwr = x^(mnt4_Fq::euler);
  return xpwr == mnt4_Fq::one();
}

/* TODO
mnt4_Fq inv0(const mnt4_Fq &x) {
}
*/

// CMOV(new, old, c): If c = 1, return a, else return b.
mnt4_Fq CMOV(const mnt4_Fq &xnew, const mnt4_Fq &xold, const bool c) {
  if(c)
    return xnew;
  return xold;
}

mnt4_G1 hash_mnt4_G1(const void *msg) {
  const mnt4_Fq Z("380229795275686117900195895937549876439284346033943019071556938246890012985846085904284971");
  mnt4_Fq x, y,
    u, t1, t2, x1, gx1, x2, gx2;
  bool e;

  // implementing SWU method as described at 4.3 of CFRG "Hashing to Elliptic Curves"
  u = HashToBase(msg);
  t1 = Z * u * u;
  t2 = t1 * t1;
  x1 = t1 + t2;
  x1 = - mnt4_G1::coeff_b * mnt4_G1::coeff_a.inverse() * (x1.inverse() + 1);
  gx1 = curve_f(x1);
  x2 = t1 * x1;
  // new part, not following CFRG
  gx2 = curve_f(x2);
  e = is_sq_legendre(gx1);
  x = CMOV(x1, x2, e);
  y = CMOV(gx1, gx2, e);
  y = y.sqrt();
  return mnt4_G1(x, y, mnt4_Fq::one());
}

#ifdef TESTCASE_MNT4
int main() {
  mnt4_pp::init_public_params();
  mnt4_G1 P;
  mnt4_Fq x, xd, gx;

  for(int j=0; j<10000; j++) {
    P = hash_mnt4_G1("hello hash");
    if(P.Y()*P.Y() == curve_f(P.X())) {
      cout << "ok" << endl;
    } else {
      cout << "err" << endl;
    }
  }
  return 0;
}
#endif
