package io.horizen.examples.api.model;

public final class SendVoteMessageToSidechainRequest {
    private String proposition;
    private int vote;
    private String receivingSidechain;
    private String receivingAddress;
    private long fee;

    public String getProposition() {
        return proposition;
    }

    public int getVote() {
        return vote;
    }

    public String getReceivingSidechain() {
        return receivingSidechain;
    }

    public String getReceivingAddress() {
        return receivingAddress;
    }

    public long getFee() {
        return fee;
    }

    public void setProposition(String proposition) {
        this.proposition = proposition;
    }

    public void setVote(int vote) {
        this.vote = vote;
    }

    public void setReceivingSidechain(String receivingSidechain) {
        this.receivingSidechain = receivingSidechain;
    }

    public void setReceivingAddress(String receivingAddress) {
        this.receivingAddress = receivingAddress;
    }

    public void setFee(long fee) {
        this.fee = fee;
    }

    @Override
    public String toString() {
        return "SendVoteMessageToSidechainRequest{" +
                "proposition='" + proposition + '\'' +
                ", vote=" + vote +
                ", receivingSidechain='" + receivingSidechain + '\'' +
                ", receivingAddress='" + receivingAddress + '\'' +
                ", fee=" + fee +
                '}';
    }
}