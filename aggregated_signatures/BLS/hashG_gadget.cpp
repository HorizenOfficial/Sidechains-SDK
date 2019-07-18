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

public:
  FT Z, coeff_b, coeff_a;

  hashG_gencase(const FT c_a, const FT c_b, const FT pZ) {
    coeff_a = c_a;
    coeff_b = c_b;
    Z = pZ;
    generate();
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
    return ((choice + 1)*xnew -
	    (choice - 1)*xold)
      * (div2.inverse());
  };

  void generate() {
    while(true) {
      u = FT::random_element();
      t1 = Z * u * u;
      x1den = t1 * (t1 + 1);

      x1 =  - coeff_b * coeff_a.inverse() * (x1den.inverse() + 1);
      // to verify this: ((a/b)*x1 + 1) * x1den = -1

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
  FT get_e1() {return e1;};
  FT get_e2() {return e2;};
  FT get_ysq() {return ysq;};
};

// hashG_gencase::curve_f(x)
// y2 = x^3 + ax + b
template<typename FT>
class eqG_gadget : libsnark::gadget<FT> {
private:
  libsnark::pb_variable<FT> x, y2, x2;

public:
  eqG_gadget(libsnark::protoboard<FT>& pb,
               const std::string& annotation_prefix=" eqG") :
    libsnark::gadget<FT>(pb, annotation_prefix)
  {
    x.allocate(pb, FMT(this->annotation_prefix, " x"));
    y2.allocate(pb, FMT(this->annotation_prefix, " y2"));
    x2.allocate(pb, FMT(this->annotation_prefix, " x2"));
    std::cout << "Init sub-circuit " << annotation_prefix << std::endl;
  };

  void generate_r1cs_constraints() {
    libsnark::linear_combination<FT> x2pa, y2mb;

    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(x, x, x2),
            FMT(this->annotation_prefix, " x*x = x2"));
    //    x2pa.add_term(var, coeff);
    //    y2ma.add_term(var, coeff);
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(x2pa, x, y2mb),
            FMT(this->annotation_prefix, " (x2 + a)*x = y2 - b"));
    std::cout << "Constraints sub-circuit " << this->annotation_prefix << std::endl;
  };

  void generate_r1cs_witness() {
    this->pb.val(x) = 0;
    this->pb.val(y2) = 0;
    this->pb.val(x2) = 0;
    std::cout << "Witness sub-circuit " << this->annotation_prefix << std::endl;
  };
};

template<typename FT>
class hashG_gadget : libsnark::gadget<FT> {
private:
  libsnark::pb_variable<FT> u, x, y,
    t1, x1den, x1, x2, gx1, gx2,
    e1, e2, ysq;
  FT pZ, abinv;
  std::shared_ptr<eqG_gadget<FT>> x1gx1, x2gx2;

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
    e1.allocate(pb, FMT(this->annotation_prefix, " e1"));
    e2.allocate(pb, FMT(this->annotation_prefix, " e2"));
    ysq.allocate(pb, FMT(this->annotation_prefix, " ysq"));

    pZ = src.Z;
    abinv = src.coeff_a * src.coeff_b.inverse();

    x1gx1.reset(new eqG_gadget<FT>(pb,
                      FMT(annotation_prefix, "-gx1")));
    x2gx2.reset(new eqG_gadget<FT>(pb,
                      FMT(annotation_prefix, "-gx2")));
  };

  void generate_r1cs_constraints() {
    libsnark::linear_combination<FT> zu, x1ab, x_twice, ysq_twice;

    zu.add_term(u, pZ);
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(zu, u, t1),
            FMT(this->annotation_prefix, " (Zu)*u = t1"));

    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(t1, (t1 + 1), x1den),
            FMT(this->annotation_prefix, " t1*(t1 + 1) = x1den"));

/*
    calculation was: x1 =  - coeff_b * coeff_a.inverse() * (x1den.inverse() + 1);
    ((a/b)*x1 + 1) * x1den = -1
*/
    x1ab.add_term(x1, abinv);
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(x1ab + 1, x1den, -1),
            FMT(this->annotation_prefix, " (abinv*x1 + 1) * x1den = -1"));

    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(x1, t1, x2),
            FMT(this->annotation_prefix, " x1*t1 = x2"));

    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(e1 - 1, e1 + 1, 0),
            FMT(this->annotation_prefix, " (e1 - 1) * (e1 + 1) = 0"));
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(e2 - 1, e2 + 1, 0),
            FMT(this->annotation_prefix, " (e2 - 1) * (e2 + 1) = 0"));
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(e1 + e2, 1, 0),
            FMT(this->annotation_prefix, " e1 + e2 = 0"));
/*
    2x ==  ((e1 + 1)*x1 -
	    (e1 - 1)*x2)
    e1 * (x1 - x2) = 2x - (x1 + x2)
*/
    x_twice.add_term(x, 2);
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(e1, x1 - x2, x_twice - (x1 + x2)),
            FMT(this->annotation_prefix, " e1 * (x1 - x2) = 2x - (x1 + x2)"));
    ysq_twice.add_term(ysq, 2);
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(e1, gx1 - gx2, ysq_twice - (gx1 + gx2)),
            FMT(this->annotation_prefix, " e1 * (gx1 - gx2) = 2*ysq - (gx1 + gx2)"));

    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FT>(y, y, ysq),
            FMT(this->annotation_prefix, " y*y = ysq"));

    this->x1gx1->generate_r1cs_constraints();
    this->x2gx2->generate_r1cs_constraints();
  };

  void generate_r1cs_witness(class hashG_gencase<FT>& src) { // ?const
    this->pb.val(u) = src.get_u();
    this->pb.val(t1) = src.get_t1();
    this->pb.val(x1den) = src.get_x1den();
    this->pb.val(x1) = src.get_x1();
    this->pb.val(x2) = src.get_x2();
    this->pb.val(gx1) = src.get_gx1();
    this->pb.val(gx2) = src.get_gx2();
    this->pb.val(e1) = src.get_e1();
    this->pb.val(e2) = src.get_e2();
    this->pb.val(x) = src.get_x();
    this->pb.val(ysq) = src.get_ysq();
    this->pb.val(y) = src.get_y();

    this->x1gx1->generate_r1cs_witness();
    this->x2gx2->generate_r1cs_witness();
  };
};

#define ppT libff::mnt6_pp
#define GroupT libff::mnt4_G1
#define FieldT libff::Fr<ppT>

int main() {
  libff::mnt4_pp::init_public_params();
  libff::mnt6_pp::init_public_params();
  libsnark::protoboard<FieldT> pb;

  // this parameter (the non-residue) was generated for testing and demo
  const FieldT pZ("380229795275686117900195895937549876439284346033943019071556938246890012985846085904284971");
  hashG_gencase<FieldT> hc(GroupT::coeff_a, GroupT::coeff_b, pZ);

  hashG_gadget<FieldT> hg(pb, hc);
  hg.generate_r1cs_constraints();
  hg.generate_r1cs_witness(hc);
  assert(pb.is_satisfied());
  return 0;
}
