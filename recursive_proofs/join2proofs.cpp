/* Proof that two others specific proof were verified
 * based on general broad "recursive verification" (previous) example
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

// create and verify a basic proof
template <typename ppT, typename FieldT>
void make_basic_proof(const libsnark::protoboard<FieldT>& pb,
                      libsnark::r1cs_ppzksnark_keypair<ppT>& kkeypair,
                      const std::string& annotation) {
  const libsnark::r1cs_ppzksnark_keypair<ppT> keypair
    = libsnark::r1cs_ppzksnark_generator<ppT>(pb.get_constraint_system());
  const libsnark::r1cs_ppzksnark_proof<ppT> pi
    = libsnark::r1cs_ppzksnark_prover<ppT>(keypair.pk, pb.primary_input(), pb.auxiliary_input());
  bool isvalid = libsnark::r1cs_ppzksnark_verifier_strong_IC<ppT>(keypair.vk, pb.primary_input(), pi);
  if(isvalid) {
    std::cout << annotation << " ok" << std::endl;
  } else {
    std::cout << annotation << " is not ok" << std::endl;
  }
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

  P1.makeproof();
  P2.makeproof();

  // circuit/gadget verifying two proofs above
  libsnark::protoboard<FieldT_B> pbB;
  jproof_compliance_gadget<ppT_B, FieldT_B> complg(pbB, FieldT_A::size_in_bits(), 1);
  complg.generate_r1cs_constraints();
  complg.generate_r1cs_witness();

  libsnark::r1cs_ppzksnark_keypair<ppT_B> keypair_about;
  //  make_basic_proof<ppT_B, FieldT_B>(pbB, keypair_about);
}

int main() {
  libff::mnt4_pp::init_public_params();
  libff::mnt6_pp::init_public_params();

  run2proofs<libff::mnt4_pp, libff::mnt6_pp>();
  return 0;
}
