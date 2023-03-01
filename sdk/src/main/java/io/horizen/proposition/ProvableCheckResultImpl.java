package io.horizen.proposition;

import io.horizen.secret.Secret;
import java.util.ArrayList;
import java.util.List;

public class ProvableCheckResultImpl<S extends Secret> implements ProvableCheckResult<S> {

    private boolean canBeProved;
    private List<S> secretList = new ArrayList<>();

    public ProvableCheckResultImpl(boolean canBeProved){
        this.canBeProved = canBeProved;
    }
    public ProvableCheckResultImpl(boolean canBeProved, S singleSecret){
        this.canBeProved = canBeProved;
        this.secretList.add(singleSecret);
    }

    public ProvableCheckResultImpl(boolean canBeProved, List<S> secretList){
        this.canBeProved = canBeProved;
        this.secretList = secretList;
    }

    public boolean canBeProved(){
        return canBeProved;
    }

    public List<S> secretsNeeded(){
        return secretList;
    }
}