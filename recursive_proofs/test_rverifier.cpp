/* Proof that two others proof were verified
 * based on test_verifier() libsnark example
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

template<typename FieldT>
void ddump_cs(libsnark::protoboard<FieldT> pb) {
  libsnark::r1cs_variable_assignment<FieldT> full_variable_assignment = pb.primary_input();
  auto cs = pb.get_constraint_system();
  std::cout << "Number of constraints: " << cs.num_constraints() << std::endl
	    << "Number of variables: " << pb.num_variables() << std::endl
	    << "Number of inputs: " << pb.num_inputs() << std::endl;
  auto aux = pb.auxiliary_input();
  full_variable_assignment.insert(full_variable_assignment.end(), aux.begin(), aux.end());
  for(size_t c = 0; c < cs.constraints.size(); ++c)
    dump_r1cs_constraint(cs.constraints[c], full_variable_assignment, cs.variable_annotations);
}

template <typename ppT_A, typename ppT_B>
void rverifier() {
  typedef libff::Fr<ppT_A> FieldT_A;
  typedef libff::Fr<ppT_B> FieldT_B;

  const int primary_input_size = 3;

  // create a simple demo circuit
  libsnark::protoboard<FieldT_A> pbA1;
  libsnark::pb_variable<FieldT_A> px, py, pz;
  px.allocate(pbA1, "x");
  py.allocate(pbA1, "y");
  pz.allocate(pbA1, "z");
  pbA1.val(px) = 3;
  pbA1.val(py) = 5;
  pbA1.val(pz) = 15;
  pbA1.add_r1cs_constraint(libsnark::r1cs_constraint<FieldT_A>(px, py, pz), "A1: x*y = z");  
  pbA1.set_input_sizes(primary_input_size);
  assert(pbA1.is_satisfied());
  ddump_cs<FieldT_A>(pbA1);

  // create and verify a proof of first circuit
  const libsnark::r1cs_ppzksnark_keypair<ppT_A> keypair1
    = libsnark::r1cs_ppzksnark_generator<ppT_A>(pbA1.get_constraint_system());
  const libsnark::r1cs_ppzksnark_proof<ppT_A> pi1
    = libsnark::r1cs_ppzksnark_prover<ppT_A>(keypair1.pk, pbA1.primary_input(), pbA1.auxiliary_input());
  bool isvalid1 = libsnark::r1cs_ppzksnark_verifier_strong_IC<ppT_A>(keypair1.vk, pbA1.primary_input(), pi1);
  if(isvalid1) {
    std::cout << "First proof ok" << std::endl;
  } else {
    std::cout << "First proof is not ok" << std::endl;
  }

  // create another simple circuit
  libsnark::protoboard<FieldT_A> pbA2;
  libsnark::pb_variable<FieldT_A> ps, pt, pu;
  ps.allocate(pbA2, "s");
  pt.allocate(pbA2, "t");
  pu.allocate(pbA2, "u");
  pbA2.val(ps) = 6;
  pbA2.val(pt) = 8;
  pbA2.val(pu) = 63;
  pbA2.add_r1cs_constraint(libsnark::r1cs_constraint<FieldT_A>(ps+1, pt+1, pu), "A2: (s+1)*(t+1) = z");  
  pbA2.set_input_sizes(primary_input_size);
  assert(pbA2.is_satisfied());
  ddump_cs<FieldT_A>(pbA2);

  // create and verify a proof of the second circuit
  const libsnark::r1cs_ppzksnark_keypair<ppT_A> keypair2
    = libsnark::r1cs_ppzksnark_generator<ppT_A>(pbA2.get_constraint_system());
  const libsnark::r1cs_ppzksnark_proof<ppT_A> pi2
    = libsnark::r1cs_ppzksnark_prover<ppT_A>(keypair2.pk, pbA2.primary_input(), pbA2.auxiliary_input());
  bool isvalid2 = libsnark::r1cs_ppzksnark_verifier_strong_IC<ppT_A>(keypair2.vk, pbA2.primary_input(), pi2);
  if(isvalid2) {
    std::cout << "Second proof ok" << std::endl;
  } else {
    std::cout << "Second proof is not ok" << std::endl;
  }

  const size_t elt_size = FieldT_A::size_in_bits();
  const size_t primary_input_size_in_bits = elt_size * primary_input_size;
  const size_t vk_size_in_bits = libsnark::r1cs_ppzksnark_verification_key_variable<ppT_B>::size_in_bits(primary_input_size);

  libsnark::protoboard<FieldT_B> pb;
  libsnark::pb_variable_array<FieldT_B> vk1_bits, vk2_bits;
  vk1_bits.allocate(pb, vk_size_in_bits, "vk1_bits");
  vk2_bits.allocate(pb, vk_size_in_bits, "vk2_bits");

  libsnark::pb_variable_array<FieldT_B> primary_input1_bits, primary_input2_bits;
  primary_input1_bits.allocate(pb, primary_input_size_in_bits, "primary_input1_bits");
  primary_input2_bits.allocate(pb, primary_input_size_in_bits, "primary_input2_bits");

  libsnark::r1cs_ppzksnark_proof_variable<ppT_B> proof1(pb, "proof-1");
  libsnark::r1cs_ppzksnark_proof_variable<ppT_B> proof2(pb, "proof-2");

  libsnark::r1cs_ppzksnark_verification_key_variable<ppT_B> vk1(pb, vk1_bits, primary_input_size, "vk-1");
  libsnark::r1cs_ppzksnark_verification_key_variable<ppT_B> vk2(pb, vk2_bits, primary_input_size, "vk-2");

  libsnark::pb_variable<FieldT_B> result1, result2;
  result1.allocate(pb, "result-1");
  result2.allocate(pb, "result-2");

  // ..and another r1cs_ppzksnark_verifier_gadget<ppT_B>
  // resulting in recursive verification of two incoming proofs
  libsnark::r1cs_ppzksnark_verifier_gadget<ppT_B> verifier1(pb, vk1, primary_input1_bits, elt_size, proof1, result1, "verifier-1"),
                                                  verifier2(pb, vk2, primary_input2_bits, elt_size, proof2, result2, "verifier-2");

  // const size_t block_size = libsnark::r1cs_ppzksnark_verification_key_variable<ppT_A>::size_in_bits(primary_input_size_transla);
  libff::bit_vector hash_input_bits;
  libsnark::block_variable<FieldT_A> hash_input_block(pbA1, hash_input_bits.size(), "input_block");
  // digest_variable<FieldT_A> hash_output(pb, knapsack_CRH_with_bit_out_gadget<FieldT_A>::get_digest_len(), "output_digest");
  libsnark::pb_linear_combination_array<FieldT_A> hash_output;
  libsnark::knapsack_CRH_with_field_out_gadget<FieldT_A> keyhash(pbA1, hash_input_bits.size(), hash_input_block, hash_output, "hash");

  proof1.generate_r1cs_constraints();
  proof2.generate_r1cs_constraints();
  verifier1.generate_r1cs_constraints();
  verifier2.generate_r1cs_constraints();

  std::cout << "Bits-1 input size " << pbA1.primary_input().size() << std::endl;
  libff::bit_vector input1_as_bits;
  for (const FieldT_A &el : pbA1.primary_input()) {
    libff::bit_vector v = libff::convert_field_element_to_bit_vector<FieldT_A>(el, elt_size);
    input1_as_bits.insert(input1_as_bits.end(), v.begin(), v.end());
    std::cout << el << std::endl;
  }
  primary_input1_bits.fill_with_bits(pb, input1_as_bits);

  std::cout << "Bits-2 input size " << pbA2.primary_input().size() << std::endl;
  libff::bit_vector input2_as_bits;
  for (const FieldT_A &el : pbA2.primary_input()) {
    libff::bit_vector v = libff::convert_field_element_to_bit_vector<FieldT_A>(el, elt_size);
    input2_as_bits.insert(input2_as_bits.end(), v.begin(), v.end());
    std::cout << el << std::endl;
  }
  primary_input2_bits.fill_with_bits(pb, input2_as_bits);

  vk1.generate_r1cs_witness(keypair1.vk);
  vk2.generate_r1cs_witness(keypair2.vk);
  proof1.generate_r1cs_witness(pi1);
  proof2.generate_r1cs_witness(pi2);
  verifier1.generate_r1cs_witness();
  verifier2.generate_r1cs_witness();
  pb.val(result1) = FieldT_B::one();
  pb.val(result2) = FieldT_B::one();

  assert(pb.is_satisfied());
  ddump_cs<FieldT_B>(pb);

  const libsnark::r1cs_ppzksnark_keypair<ppT_B> keypair_about
    = libsnark::r1cs_ppzksnark_generator<ppT_B>(pb.get_constraint_system());
  const libsnark::r1cs_ppzksnark_proof<ppT_B> pi_about
    = libsnark::r1cs_ppzksnark_prover<ppT_B>(keypair_about.pk, pb.primary_input(), pb.auxiliary_input());
  bool isvalid_about = libsnark::r1cs_ppzksnark_verifier_strong_IC<ppT_B>(keypair_about.vk, pb.primary_input(), pi_about);
  if(isvalid_about) {
    std::cout << "Combined proof ok" << std::endl;
  } else {
    std::cout << "Combined proof is not ok" << std::endl;
  }
}

int main() {
  libff::mnt4_pp::init_public_params();
  libff::mnt6_pp::init_public_params();

  rverifier<libff::mnt4_pp, libff::mnt6_pp>();
  return 0;
}
