package com.horizen.api;

public interface ISidechainApiCall {

	public abstract ISidechainApiResponse onCall() throws SidechainApiException;
}
