package com.lightirc.proxy.tasks;

import java.nio.channels.SocketChannel;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.lightirc.proxy.pipe.Pipe;
import com.lightirc.proxy.pipe.PipeEndpoint;

public class ExpirePipeTask extends TimerTask {
	private static final int MAX_IDLE_DURATION = 5000;

	private ConcurrentHashMap<SocketChannel, PipeEndpoint> pipeEndpoints;

	public ExpirePipeTask(
			ConcurrentHashMap<SocketChannel, PipeEndpoint> pipeEndpoints) {
		this.pipeEndpoints = pipeEndpoints;
	}

	public void run() {
		long limit = System.currentTimeMillis() - MAX_IDLE_DURATION;

		for (SocketChannel socketChannel : pipeEndpoints.keySet()) {
			Pipe pipe = pipeEndpoints.get(socketChannel).getPipe();

			if (!pipe.isConnected() && pipe.getInit() < limit) {
				pipe.close();
			}
		}
	}
	
}
