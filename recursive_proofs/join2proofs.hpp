/* Proof that two others specific proof were verified
 * based on general broad "recursive verification" (previous) example
 *
 * Given a P1 proof that given x and y prove that x*x = y
 * Given a P2 proof that given x and z prove that x*2 = z
 * "x" and "y" are public input for P1
 * "x" and "z" are public input for P2
 * no witness
 *
 * Infopulse Horizen 2019
 * written by Vadym Fedyukovych
 */

template <typename FieldT, typename ppTA, typename ppT_B>
void make_primary_input(const libsnark::protoboard<FieldT>& pbA,
                        libff::bit_vector& input_as_bits,
                        std::shared_ptr<libsnark::r1cs_ppzksnark_verification_key_variable<ppT_B>> vk,
                        std::shared_ptr<libsnark::r1cs_ppzksnark_proof_variable<ppT_B>> proof,
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
  const int public_input_size = 2; // primary_input_size

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
};

template<typename ppT, typename FieldT>
class jproof_mult2_gadget : libsnark::gadget<FieldT> {
private:
  libsnark::pb_variable<FieldT> vx, vz;
  libsnark::r1cs_ppzksnark_keypair<ppT> keypair;

public:
  const int public_input_size = 2; // primary_input_size

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
};

template<typename ppT, typename FieldT, typename FieldTsimple, typename ppTsimple>
class jproof_compliance_gadget : libsnark::gadget<FieldT> {
private:
  std::shared_ptr<libsnark::r1cs_ppzksnark_verifier_gadget<ppT>> verifier1, verifier2;
  std::shared_ptr<libsnark::r1cs_ppzksnark_proof_variable<ppT>> proof1, proof2;
  std::shared_ptr<libsnark::r1cs_ppzksnark_verification_key_variable<ppT>> vk1, vk2;
  libsnark::pb_variable<FieldT> result1, result2;
  libsnark::pb_variable_array<FieldT> vk1_bits, vk2_bits;
  libsnark::pb_variable_array<FieldT> primary_input1_bits, primary_input2_bits;

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

    vk1_bits.allocate(pb, vk_size_in_bits, "vk1_bits");
    vk2_bits.allocate(pb, vk_size_in_bits, "vk2_bits");

    primary_input1_bits.allocate(pb, primary_input_size_in_bits, "primary_input1_bits");
    primary_input2_bits.allocate(pb, primary_input_size_in_bits, "primary_input2_bits");

    proof1.reset(new libsnark::r1cs_ppzksnark_proof_variable<ppT>(pb, "proof-1"));
    proof2.reset(new libsnark::r1cs_ppzksnark_proof_variable<ppT>(pb, "proof-2"));

    vk1.reset(new libsnark::r1cs_ppzksnark_verification_key_variable<ppT>(pb, vk1_bits, primary_input_size, "vk-1"));
    vk2.reset(new libsnark::r1cs_ppzksnark_verification_key_variable<ppT>(pb, vk2_bits, primary_input_size, "vk-2"));

    result1.allocate(pb, "result-1");
    result2.allocate(pb, "result-2");

    verifier1.reset(new libsnark::r1cs_ppzksnark_verifier_gadget<ppT>(pb,
                      *vk1, primary_input1_bits, elt_size, *proof1, result1,
                      FMT(annotation_prefix, " verifier-1")));
    verifier2.reset(new libsnark::r1cs_ppzksnark_verifier_gadget<ppT>(pb,
                      *vk2, primary_input2_bits, elt_size, *proof2, result2,
                      FMT(annotation_prefix, " verifier-2")));

  };

  void generate_r1cs_constraints() {
    this->proof1->generate_r1cs_constraints();
    this->proof2->generate_r1cs_constraints();
    this->verifier1->generate_r1cs_constraints();
    this->verifier2->generate_r1cs_constraints();
  };

  void generate_r1cs_witness(const libsnark::protoboard<FieldTsimple>& pbA1,
			     const libsnark::protoboard<FieldTsimple>& pbA2) {

    libff::bit_vector input1_as_bits, input2_as_bits;
    const size_t elt_size = FieldTsimple::size_in_bits();

    make_primary_input<FieldTsimple, ppTsimple, ppT>(pbA1, input1_as_bits, this->vk1, this->proof1, "Square");
    this->primary_input1_bits.fill_with_bits(this->pb, input1_as_bits);

    make_primary_input<FieldTsimple, ppTsimple, ppT>(pbA2, input2_as_bits, this->vk2, this->proof2, "Mult-2");
    this->primary_input2_bits.fill_with_bits(this->pb, input2_as_bits);

    this->verifier1->generate_r1cs_witness();
    this->verifier2->generate_r1cs_witness();
    this->pb.val(result1) = FieldT::one();
    this->pb.val(result2) = FieldT::one();
    assert(this->pb.is_satisfied());
  };
};
