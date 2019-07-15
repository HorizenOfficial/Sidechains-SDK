#include <libsnark/gadgetlib1/protoboard.hpp>
#include <libsnark/zk_proof_systems/ppzksnark/r1cs_ppzksnark/r1cs_ppzksnark.hpp>
#include <libsnark/gadgetlib1/gadgets/verifiers/r1cs_ppzksnark_verifier_gadget.hpp>
#include <libff/algebra/curves/mnt/mnt4/mnt4_pp.hpp>
#include <libff/algebra/curves/mnt/mnt6/mnt6_pp.hpp>

template<typename FT>
class hashG_gencase {
private:
  FT u, x, y,
    t1, x1den, x1, x2, gx1, gx2, e1, e2, ysq;
  FT Z, coeff_b, coeff_a;

public:
  hashG_gencase(const FT c_a, const FT c_b, const FT pZ) {
    coeff_a = c_a;
    coeff_b = c_b;
    Z = pZ;
  };

  FT curve_f(const FT &px) {
    return px*px*px + coeff_a*px + coeff_b;
  };

  FT legendre_pm_one(const FT &px) {
    return px^(FT::euler);
  };

  // CMOV(a, b, c): If c == 1, return a, if c==-1 return b.
  FT CMOV_FT(const FT& xnew, const FT& xold, const FT& choice) {
    FT div2("2");
    return ((choice + 1)*xnew +
	    (choice - 1)*xold)
      * (div2.inverse());
  };

  void generate() {
    while(true) {
      u = FT::random_element();
      t1 = Z * u * u;
      x1den = t1 * (t1 + 1);  // merged

      // x1den * ((a/b)*x1 + 1) = -1
      x1 =  - coeff_b * coeff_a.inverse() * (x1den.inverse() + 1);

      x2 = t1 * x1;
      gx1 = curve_f(x1);
      gx2 = curve_f(x2);
      e1 = legendre_pm_one(gx1);
      e2 = legendre_pm_one(gx2);
      assert((e1 == FT::one() &&
	      e2 == -FT::one())  ||
             (e1 == -FT::one() &&
	      e2 == FT::one()));
      x = CMOV_FT(x1, x2, e1);
      ysq = CMOV_FT(gx1, gx2, e1);
      y = ysq.sqrt();
      break;
    }
  };
  FT get_u() {return u;};
  FT get_x() {return x;};
  FT get_y() {return y;};
  FT get_t1() {return t1;};
  FT get_x1den() {return x1den;};
  FT get_x1() {return x1;};
  FT get_x2() {return x2;};
  FT get_gx1() {return gx1;};
  FT get_gx2() {return gx2;};
  FT get_ysq() {return ysq;};
};

template<typename FT>
class hashG_gadget : libsnark::gadget<FT> {
private:
  libsnark::pb_variable<FT> u, x, y,
    t1, x1den, x1, x2, gx1, gx2,
    e1, e2, ysq;
  FT pZ;

public:
  hashG_gadget(libsnark::protoboard<FT>& pb,
               class hashG_gencase<FT>& src,
               const std::string& annotation_prefix="hashG") :
    libsnark::gadget<FT>(pb, annotation_prefix)
  {
    u.allocate(pb, FMT(this->annotation_prefix, " u"));
    x.allocate(pb, FMT(this->annotation_prefix, " x"));
    y.allocate(pb, FMT(this->annotation_prefix, " y"));

    t1.allocate(pb, FMT(this->annotation_prefix, " t1"));
    x1den.allocate(pb, FMT(this->annotation_prefix, " x1den"));
    x1.allocate(pb, FMT(this->annotation_prefix, " x1"));
    x2.allocate(pb, FMT(this->annotation_prefix, " x2"));
    gx1.allocate(pb, FMT(this->annotation_prefix, " gx1"));
    gx2.allocate(pb, FMT(this->annotation_prefix, " gx2"));
  };

  void generate_r1cs_constraints() {
    libsnark::linear_combination<FT> zu;
    zu.add_term(u, pZ);
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(zu, u, t1),
            FMT(this->annotation_prefix, " (Zu)*u = t1"));
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(t1, (t1 + 1), x1den),
            FMT(this->annotation_prefix, " t1*(t1 + 1) = x1den"));
/*
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(1, x1inv + 1, x1),
            FMT(this->annotation_prefix, " ba*(x1inv + 1) = x1"));
*/
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(x1, t1, x2),
            FMT(this->annotation_prefix, " x1*t1 = x2"));
  };
      /*
    x1 =  - coeff_b * coeff_a.inverse() * (x1den.inverse() + 1);
    (mab*x1 - 1) * x1den = 1

      x1den = t1 * (t1 + 1);  // merged & explicit inverted-x1
      (x1 - 1)*x1den = -mba;
       */
  void generate_r1cs_witness(class hashG_gencase<FT>& src) { // ?const
    this->pb.val(u) = src.get_u();
    this->pb.val(t1) = src.get_t1();
    this->pb.val(x1den) = src.get_x1den();
    this->pb.val(x1) = src.get_x1();
    this->pb.val(x2) = src.get_x2();
    // ...
  };
};

#define ppT libff::mnt6_pp
#define GroupT libff::mnt4_G1
#define FieldT libff::Fr<ppT>

int main() {
  ppT::init_public_params();
  libsnark::protoboard<FieldT> pb;

  const FieldT pZ("380229795275686117900195895937549876439284346033943019071556938246890012985846085904284971");
  hashG_gencase<FieldT> hc(GroupT::coeff_a, GroupT::coeff_b, pZ);

  hashG_gadget<FieldT> hg(pb, hc);
  hg.generate_r1cs_constraints();
  hg.generate_r1cs_witness(hc);
  assert(pb.is_satisfied());
  return 0;
}
