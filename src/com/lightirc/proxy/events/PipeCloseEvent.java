package com.lightirc.proxy.events;

import com.lightirc.proxy.pipe.Pipe;

public class PipeCloseEvent extends Event {
	public PipeCloseEvent(Pipe pipe) {
		super(pipe);
	}
	
}
