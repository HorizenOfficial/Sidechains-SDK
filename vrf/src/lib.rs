extern crate jni;

use jni::JNIEnv;
use jni::objects::{JClass};
use jni::sys::{jbyteArray, jboolean};
use jni::sys::{JNI_TRUE};

#[no_mangle]
pub extern "system" fn Java_com_horizen_vrf_VRFProof_nativeProofToVRFHash(
    _env: JNIEnv,
    // this is the class that owns our
    // static method. Not going to be
    // used, but still needs to have
    // an argument slot
    _class: JClass,
    _proof: jbyteArray
) -> jbyteArray {

    let mut _input = _env.convert_byte_array(_proof).unwrap();
    _input.reverse();
    _env.byte_array_from_slice(&_input).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_horizen_vrf_VRFPublicKey_nativeVerify(
    _env: JNIEnv,
    // this is the class that owns our
    // static method. Not going to be
    // used, but still needs to have
    // an argument slot
    _class: JClass,
    _key: jbyteArray,
    _message: jbyteArray,
    _proof: jbyteArray
) -> jboolean {
    let result: jboolean = JNI_TRUE;

    result
}

#[no_mangle]
pub extern "system" fn Java_com_horizen_vrf_VRFSecretKey_nativeProve(
    _env: JNIEnv,
    // this is the class that owns our
    // static method. Not going to be
    // used, but still needs to have
    // an argument slot
    _class: JClass,
    _key: jbyteArray,
    _message: jbyteArray
) -> jbyteArray {

    //let result_class = _env.find_class("com/horizen/vrf/VRFProof").unwrap();
    //let result = _env.new_object(result_class, "()V", &[]).unwrap();

    let mut _input = _env.convert_byte_array(_message).unwrap();
    _input.reverse();
    _env.byte_array_from_slice(&_input).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_horizen_vrf_VRFSecretKey_nativeVRFHash(
    _env: JNIEnv,
    // this is the class that owns our
    // static method. Not going to be
    // used, but still needs to have
    // an argument slot
    _class: JClass,
    _key: jbyteArray,
    _message: jbyteArray
) -> jbyteArray {

    let mut _input = _env.convert_byte_array(_message).unwrap();
    _input.reverse();
    _env.byte_array_from_slice(&_input).unwrap()
}