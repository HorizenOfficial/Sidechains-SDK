/* Proof that two others specific proof were verified
 *
 * Infopulse Horizen 2019
 * written by Vadym Fedyukovych
 */

#include <libff/algebra/curves/mnt/mnt4/mnt4_pp.hpp>
#include <libff/algebra/curves/mnt/mnt6/mnt6_pp.hpp>
#include <libsnark/gadgetlib1/protoboard.hpp>
#include <libsnark/zk_proof_systems/ppzksnark/r1cs_ppzksnark/r1cs_ppzksnark.hpp>
#include <libsnark/gadgetlib1/gadgets/verifiers/r1cs_ppzksnark_verifier_gadget.hpp>
#include <libsnark/gadgetlib1/gadgets/hashes/knapsack/knapsack_gadget.hpp>
#include "join2proofs.hpp"

template <typename FieldT, typename ppTA, typename ppTB>
void make_primary_input(const libsnark::protoboard<FieldT>& pbA,
                        libff::bit_vector& input_as_bits,
                        std::shared_ptr<libsnark::r1cs_ppzksnark_verification_key_variable<ppTB>> vk,
                        std::shared_ptr<libsnark::r1cs_ppzksnark_proof_variable<ppTB>> proof,
                        const std::string& annotation) {
  const size_t elt_size = FieldT::size_in_bits();

  assert(pbA.primary_input().size() == 2);
  const libsnark::r1cs_ppzksnark_keypair<ppTA> keypair
    = libsnark::r1cs_ppzksnark_generator<ppTA>(pbA.get_constraint_system());
  const libsnark::r1cs_ppzksnark_proof<ppTA> pi
    = libsnark::r1cs_ppzksnark_prover<ppTA>(keypair.pk, pbA.primary_input(), pbA.auxiliary_input());
  bool isvalid = libsnark::r1cs_ppzksnark_verifier_strong_IC<ppTA>(keypair.vk, pbA.primary_input(), pi);
  if(isvalid) {
    std::cout << annotation << " ok" << std::endl;
  } else {
    std::cout << annotation << " is not ok" << std::endl;
  }

  std::cout << "Bits input size " << pbA.primary_input().size() << std::endl;
  for (const FieldT &el : pbA.primary_input()) {
    libff::bit_vector v = libff::convert_field_element_to_bit_vector<FieldT>(el, elt_size);
    input_as_bits.insert(input_as_bits.end(), v.begin(), v.end());
    std::cout << el << std::endl;
  }

  vk->generate_r1cs_witness(keypair.vk);
  proof->generate_r1cs_witness(pi);
}

template <typename ppT_A, typename ppT_B>
void run2proofs() {
  typedef libff::Fr<ppT_A> FieldT_A;
  typedef libff::Fr<ppT_B> FieldT_B;

  // generate an instance at random
  jproof_two_proofs<FieldT_A> rndP1P2;

  libsnark::protoboard<FieldT_A> pbA1, pbA2;
  jproof_square_gadget<ppT_A, FieldT_A> P1(pbA1);
  jproof_mult2_gadget<ppT_A, FieldT_A> P2(pbA2);

  P1.generate_r1cs_constraints();
  P2.generate_r1cs_constraints();

  P1.generate_r1cs_witness(rndP1P2.get_x(), rndP1P2.get_y());
  P2.generate_r1cs_witness(rndP1P2.get_x(), rndP1P2.get_z());

  // circuit/gadget verifying two proofs above
  libsnark::protoboard<FieldT_B> pbB;
  jproof_compliance_gadget<ppT_B, FieldT_B, FieldT_A, ppT_A> complg(pbB, FieldT_A::size_in_bits(), 2);
  std::cout << "P3 aux input size = "
  //          << pbB.num_variables()
  //          << pbB.primary_input().size()
            << pbB.auxiliary_input().size()
            << std::endl;

  complg.generate_r1cs_constraints();
  complg.generate_r1cs_witness(pbA1, pbA2);

  libsnark::r1cs_ppzksnark_keypair<ppT_B> keypair_about
    = libsnark::r1cs_ppzksnark_generator<ppT_B>(pbB.get_constraint_system());
  const libsnark::r1cs_ppzksnark_proof<ppT_B> pi_about
    = libsnark::r1cs_ppzksnark_prover<ppT_B>(keypair_about.pk, pbB.primary_input(), pbB.auxiliary_input());
  bool isvalid = libsnark::r1cs_ppzksnark_verifier_strong_IC<ppT_B>(keypair_about.vk, pbB.primary_input(), pi_about);
  if(isvalid) {
    std::cout << "Combined proof ok" << std::endl;
  } else {
    std::cout << "Combined proof is not ok" << std::endl;
  }
}

int main() {
  libff::mnt4_pp::init_public_params();
  libff::mnt6_pp::init_public_params();

  run2proofs<libff::mnt4_pp, libff::mnt6_pp>();
  return 0;
}
