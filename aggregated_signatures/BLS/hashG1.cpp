/* Hash-to-curve function, to be used with libsnark "alt BN curve"
 * 2019 Vadym Fedyukovych
 *
 * Indifferentiable Hashing to Barreto–Naehrig Curves
 * Pierre-Alain Fouque and Mehdi Tibouchi
 * https://github.com/chris-wood/draft-irtf-cfrg-hash-to-curve/pull/20
 * https://github.com/chris-wood/draft-irtf-cfrg-hash-to-curve/issues/69
 */
#include <libff/algebra/curves/alt_bn128/alt_bn128_pp.hpp>

using namespace libff;
using namespace std;

#ifdef POC_HASHBN
alt_bn128_Fq HashToBase(const void *alpha) {
  return alt_bn128_Fq::random_element();
}
#else
alt_bn128_Fq HashToBase(const void *alpha);
#endif

alt_bn128_Fq curve_f(const alt_bn128_Fq &x) {
  return x*x*x + alt_bn128_coeff_b;
}

// if s ==1 choose new, else choose old
alt_bn128_Fq CMOV(const alt_bn128_Fq &xnew, const alt_bn128_Fq &xold, const alt_bn128_Fq &s) {
  if(s == alt_bn128_Fq::one())
    return xnew;
  
  return xold;
}

alt_bn128_G1 hashG1(const void *alpha) {
  alt_bn128_Fq t, s, x, y,
    x1, x2, x3, fx1, fx2, fx3, s1, s2, s3,
    aux, a2;

  t = HashToBase(alpha);

  alt_bn128_pp::init_public_params();
  s = 3;
  aux = -s;
  s = aux.sqrt();

  x1 = - alt_bn128_Fq::one() + s;
  x2 = - alt_bn128_Fq::one() - s;
  aux = 2;
  x1 *= aux.inverse();  // \frac{-1 + s}{2}
  x2 *= aux.inverse();  // \frac{-1 - s}{2}

  aux = alt_bn128_Fq::one() + alt_bn128_coeff_b + t*t;
  a2 = s*t*t * aux.inverse();  // \frac{s t^2}{1 + b + t^2}
  x1 -= a2;
  x2 += a2;

  a2 = t*t;
  a2 *= 3;
  // 1 - \frac{(1 + b + t^2)^2}{3 t^2}
  x3 = alt_bn128_Fq::one() - aux*aux * a2.inverse();

  fx1 = curve_f(x1); fx2 = curve_f(x2); fx3 = curve_f(x3);
  s1 = power(fx1, alt_bn128_Fq::euler);
  s2 = power(fx2, alt_bn128_Fq::euler);
  s3 = power(fx3, alt_bn128_Fq::euler);
#ifdef POC_HASHBN
  if(!(s1 == alt_bn128_Fq::one() ||
       s2 == alt_bn128_Fq::one() ||
       s3 == alt_bn128_Fq::one())) {
    cout << s1 << endl
	 << s2 << endl
	 << s3 << endl
	 << x1 << endl
	 << x2 << endl
	 << x3 << endl
      	 << t << endl;
    exit(0);
  }
#endif
  x = x3;
  x = CMOV(x2, x, s2);
  x = CMOV(x1, x, s1);
  aux = curve_f(x);
  y = aux.sqrt();
  y *= power(t, alt_bn128_Fq::euler);

  return alt_bn128_G1(x, y, alt_bn128_Fq::one());
}

#ifdef POC_HASHBN
int main() {
  alt_bn128_G1 P;
  alt_bn128_pp::init_public_params();

  for(int j=0; j<10000; j++) {
    P = hashG1("hello hash");
    if(P.Y*P.Y == curve_f(P.X)) {
      cout << "ok" << endl;
    } else {
      cout << "err" << endl;
    }
  }
  
  return 0;
}
#endif
