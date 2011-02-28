package com.lightirc.proxy.events;

import com.lightirc.proxy.pipe.Pipe;

public abstract class Event {
	private Pipe pipe;

	public Event(Pipe pipe) {
		this.pipe = pipe;
	}

	public Pipe getPipe() {
		return pipe;
	}
	
}
