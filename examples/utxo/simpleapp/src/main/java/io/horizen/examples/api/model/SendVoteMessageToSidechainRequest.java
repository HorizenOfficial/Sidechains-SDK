package io.horizen.examples.api.model;

public final class SendVoteMessageToSidechainRequest {
    private String proposition;
    private String vote;
    private String receivingSidechain;
    private String receivingAddress;
    private long fee;

    public String getProposition() {
        return proposition;
    }

    public String getVote() {
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