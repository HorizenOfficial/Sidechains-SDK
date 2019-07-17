package com.horizen.api;

public abstract class SidechainApiErrorHandler implements ISidechainApiErrorHandler {

	@Override
	public ISidechainApiResponse handle(Throwable t) {
		return null;
	}
}
