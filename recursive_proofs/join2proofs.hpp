/* Proof that two others specific proof were verified
 * based on general broad "recursive verification" (previous) example
 *
 * Infopulse Horizen 2019
 * written by Vadym Fedyukovych
 */

template <typename ppT, typename FieldT>
void make_basic_proof(const libsnark::protoboard<FieldT>& pb,
                      libsnark::r1cs_ppzksnark_keypair<ppT>& keypair,
                      const std::string& annotation="Basic proof");

template<typename FieldT>
class jproof_two_proofs {
private:
  FieldT m_x, m_y, m_z;

public:
  jproof_two_proofs(const FieldT& x, const FieldT& y, const FieldT& z) {
    m_x = x; m_y = y; m_z = z;
  };

  jproof_two_proofs() {
    m_x = FieldT::random_element();
    m_y = m_x * m_x;
    m_z = m_x * 2; // + FieldT::one();
  };

  FieldT get_x() {return m_x;};
  FieldT get_y() {return m_y;};
  FieldT get_z() {return m_z;};
};

template<typename ppT, typename FieldT>
class jproof_square_gadget : libsnark::gadget<FieldT> {
private:
  libsnark::pb_variable<FieldT> vx, vy;
  libsnark::r1cs_ppzksnark_keypair<ppT> keypair;

public:
  const int public_input_size = 1; // primary_input_size

  jproof_square_gadget(libsnark::protoboard<FieldT>& pb,
                       const std::string& annotation_prefix="jproof_square"):
    libsnark::gadget<FieldT>(pb, annotation_prefix)
  {
    vy.allocate(pb, FMT(this->annotation_prefix, " y"));
    vx.allocate(pb, FMT(this->annotation_prefix, " x"));
    pb.set_input_sizes(public_input_size);
  };

  void generate_r1cs_constraints() {
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FieldT>(vx, vx, vy),
            FMT(this->annotation_prefix, " x*x = y"));
  };

  void generate_r1cs_witness(const FieldT& x, const FieldT& y) {
    this->pb.val(vx) = x;
    this->pb.val(vy) = y;
    assert(this->pb.is_satisfied());
  };

  void makeproof() {
    make_basic_proof<ppT, FieldT>(this->pb, keypair, "Square");
  };
};

template<typename ppT, typename FieldT>
class jproof_mult2_gadget : libsnark::gadget<FieldT> {
private:
  libsnark::pb_variable<FieldT> vx, vz;
  libsnark::r1cs_ppzksnark_keypair<ppT> keypair;

public:
  const int public_input_size = 1; // primary_input_size

  jproof_mult2_gadget(libsnark::protoboard<FieldT>& pb,
                      const std::string& annotation_prefix="jproof_multiply_2"):
    libsnark::gadget<FieldT>(pb, annotation_prefix)
  {
    vz.allocate(pb, FMT(this->annotation_prefix, " z"));
    vx.allocate(pb, FMT(this->annotation_prefix, " x"));
    pb.set_input_sizes(public_input_size);
  };

  void generate_r1cs_constraints() {
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FieldT>(vx, 2, vz),
            FMT(this->annotation_prefix, " x*2 = z"));
  };

  void generate_r1cs_witness(const FieldT& x, const FieldT& z) {
    this->pb.val(vx) = x;
    this->pb.val(vz) = z;
    assert(this->pb.is_satisfied());
  };

  void makeproof() {
    make_basic_proof<ppT, FieldT>(this->pb, keypair, "Mult-2");
  };
};

template<typename ppT, typename FieldT>
class jproof_compliance_gadget : libsnark::gadget<FieldT> {
private:
  //  libsnark::pb_variable<FieldT> a;

public:
  jproof_compliance_gadget(libsnark::protoboard<FieldT>& pb,
                           const size_t elt_size, //  = FieldT_A::size_in_bits();
			   const size_t primary_input_size,
                           const std::string& annotation_prefix="jproof_compliance"):
    libsnark::gadget<FieldT>(pb, annotation_prefix)
  {
    const size_t primary_input_size_in_bits = elt_size * primary_input_size;
    const size_t vk_size_in_bits =
      libsnark::r1cs_ppzksnark_verification_key_variable<ppT>::size_in_bits(primary_input_size);

    libsnark::pb_variable_array<FieldT> vk1_bits, vk2_bits;
    vk1_bits.allocate(pb, vk_size_in_bits, "vk1_bits");
    vk2_bits.allocate(pb, vk_size_in_bits, "vk2_bits");

    libsnark::pb_variable_array<FieldT> primary_input1_bits, primary_input2_bits;
    primary_input1_bits.allocate(pb, primary_input_size_in_bits, "primary_input1_bits");
    primary_input2_bits.allocate(pb, primary_input_size_in_bits, "primary_input2_bits");

    libsnark::r1cs_ppzksnark_proof_variable<ppT> proof1(pb, "proof-1");
    libsnark::r1cs_ppzksnark_proof_variable<ppT> proof2(pb, "proof-2");

    libsnark::r1cs_ppzksnark_verification_key_variable<ppT> vk1(pb, vk1_bits, primary_input_size, "vk-1");
    libsnark::r1cs_ppzksnark_verification_key_variable<ppT> vk2(pb, vk2_bits, primary_input_size, "vk-2");

    libsnark::pb_variable<FieldT> result1, result2;
    result1.allocate(pb, "result-1");
    result2.allocate(pb, "result-2");

    libsnark::r1cs_ppzksnark_verifier_gadget<ppT> verifier1(pb, vk1, primary_input1_bits, elt_size, proof1, result1, "verifier-1"),
                                                  verifier2(pb, vk2, primary_input2_bits, elt_size, proof2, result2, "verifier-2");
  };

  void generate_r1cs_constraints() {
  /*
    this->pb.add_r1cs_constraint(
            libsnark::r1cs_constraint<FieldT>(, , ),
            FMT(this->annotation_prefix, " "));

    proof1.generate_r1cs_constraints();
    proof2.generate_r1cs_constraints();
    verifier1.generate_r1cs_constraints();
    verifier2.generate_r1cs_constraints();
  */
  };

  void generate_r1cs_witness() {
  /*
    this->pb.val(vz) = z;
    assert(this->pb.is_satisfied());

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
  */
  };
};
