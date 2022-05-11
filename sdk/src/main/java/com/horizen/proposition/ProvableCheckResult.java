package com.horizen.proposition;

import com.horizen.secret.Secret;

import java.util.List;

public interface ProvableCheckResult<S extends Secret> {

    boolean canBeProved();

    List<S> secretsNeeded();
}
