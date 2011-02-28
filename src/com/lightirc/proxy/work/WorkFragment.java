package com.lightirc.proxy.work;

import com.lightirc.proxy.LightIRCProxy;
import com.lightirc.proxy.pipe.PipeEndpoint;

public class WorkFragment {
	public enum Type {
		WAIT_FOR_SERVER_DETAILS, SERVER_CONNECTED, EXCHANGE,
	}

	private LightIRCProxy proxy;
	private PipeEndpoint pipeEndpoint;
	private Type type;

	public WorkFragment(LightIRCProxy proxy, PipeEndpoint pipeEndpoint,
			Type type) {
		this.proxy = proxy;
		this.pipeEndpoint = pipeEndpoint;
		this.type = type;
	}

	public LightIRCProxy getProxy() {
		return proxy;
	}

	public PipeEndpoint getPipeEndpoint() {
		return pipeEndpoint;
	}

	public Type getType() {
		return type;
	}
	
}
