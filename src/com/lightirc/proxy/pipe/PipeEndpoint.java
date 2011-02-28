package com.lightirc.proxy.pipe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class PipeEndpoint {
	public enum Type {
		CLIENT, SERVER,
	}

	private Type type;
	private Pipe pipe;
	private PipeEndpoint otherEndpoint;

	private SocketChannel socketChannel;
	private ByteBuffer buffer;

	public PipeEndpoint(Pipe pipe, Type type) {
		this.pipe = pipe;
		this.type = type;
	}

	public ByteBuffer getBuffer() {
		if (buffer == null) {
			buffer = ByteBuffer.allocate(8192);
		}

		return buffer;
	}

	public Pipe getPipe() {
		return pipe;
	}

	public Type getType() {
		return type;
	}

	public PipeEndpoint getOtherEndpoint() {
		return otherEndpoint;
	}

	public void setOtherEndpoint(PipeEndpoint otherEndpoint) {
		this.otherEndpoint = otherEndpoint;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	public void setSocketChannel(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}

	public void close() {
		try {
			if (socketChannel != null) {
				socketChannel.close();
			}
		} catch (IOException exeption) {
		}

		if (buffer != null) {
			buffer.clear();
		}
	}

	public String toString() {
		return (socketChannel != null && socketChannel.isConnected()) ? socketChannel
				.socket().getInetAddress().toString()
				: "null";
	}
	
}
