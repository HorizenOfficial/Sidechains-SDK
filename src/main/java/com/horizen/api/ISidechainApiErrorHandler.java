package com.horizen.api;

public interface ISidechainApiErrorHandler {

	public ISidechainApiResponse handle(Throwable t);
}
