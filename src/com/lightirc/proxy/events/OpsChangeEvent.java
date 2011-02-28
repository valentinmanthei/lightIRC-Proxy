package com.lightirc.proxy.events;

import java.nio.channels.SocketChannel;

import com.lightirc.proxy.pipe.Pipe;

public class OpsChangeEvent extends Event {
	private SocketChannel socketChannel;
	private int ops;

	public OpsChangeEvent(Pipe pipe, SocketChannel socketChannel, int ops) {
		super(pipe);

		this.socketChannel = socketChannel;
		this.ops = ops;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	public int getOps() {
		return ops;
	}
	
}
