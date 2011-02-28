package com.lightirc.proxy.events;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import com.lightirc.proxy.pipe.Pipe;

public class ServerConnectEvent extends Event {
	private SocketChannel socketChannel;
	private InetSocketAddress inetSocketAddress;

	public ServerConnectEvent(Pipe pipe, SocketChannel socketChannel,
			InetSocketAddress inetSocketAddress) {
		super(pipe);

		this.socketChannel = socketChannel;
		this.inetSocketAddress = inetSocketAddress;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	public InetSocketAddress getInetSocketAddress() {
		return inetSocketAddress;
	}
	
}
