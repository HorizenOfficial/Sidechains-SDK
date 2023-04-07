package io.horizen.examples.api.model;

public final class GetVotesFromAddressRequest {
    private String address;

    public GetVotesFromAddressRequest(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}