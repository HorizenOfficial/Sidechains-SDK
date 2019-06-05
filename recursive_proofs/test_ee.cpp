/* 
 * Infopulse Horizen 2019
 * written by Vadym Fedyukovych
 */
#include <libff/algebra/curves/mnt/mnt4/mnt4_pp.hpp>
#include <libff/algebra/curves/mnt/mnt6/mnt6_pp.hpp>
#include <libsnark/gadgetlib1/protoboard.hpp>
#include <libsnark/zk_proof_systems/ppzksnark/r1cs_ppzksnark/r1cs_ppzksnark.hpp>
#include <libsnark/gadgetlib1/gadgets/pairing/pairing_checks.hpp>

template<typename FieldT, typename ppT>
class ee_proof : libsnark::gadget<FieldT> {
private:
  std::shared_ptr<libsnark::check_e_equals_e_gadget<ppT> > checksig;

public:
  ee_proof(libsnark::protoboard<FieldT>& pb,
           const std::string& annotation_prefix="ee_proof") :
    libsnark::gadget<FieldT>(pb, annotation_prefix)
  {
    libsnark::pb_variable<FieldT> sig_valid;

    libsnark::G1_precomputation<ppT> sig_precmp;
    libsnark::G1_precomputation<ppT> msg_precmp;
    libsnark::G2_precomputation<ppT> pubk_precmp;
    libsnark::G2_precomputation<ppT> generator_precmp;

    checksig.reset(new libsnark::check_e_equals_e_gadget<ppT>(pb,
                         sig_precmp,
                         generator_precmp,
                         msg_precmp,
                         pubk_precmp,
                         sig_valid,
                         FMT(annotation_prefix, " check_ee_valid")));
  };

  void generate_r1cs_constraints() {
    /*
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FieldT>(),
            FMT(this->annotation_prefix, " "));
    */
    checksig->generate_r1cs_constraints();
  };

  void generate_r1cs_witness() {
    /*
    this->pb.val(
    assert(this->pb.is_satisfied());
    */
    checksig->generate_r1cs_witness();
  };
};

typedef libff::mnt4_pp ppaT;
typedef libff::Fr<ppaT> FieldaT;
int main() {
  libff::mnt4_pp::init_public_params();
  libsnark::protoboard<FieldaT> pb;
  ee_proof<FieldaT, ppaT> eep(pb, "EETest");
  return 0;
}
