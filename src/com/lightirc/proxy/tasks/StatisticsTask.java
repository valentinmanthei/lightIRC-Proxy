package com.lightirc.proxy.tasks;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.lightirc.proxy.pipe.Pipe;
import com.lightirc.proxy.pipe.PipeEndpoint;

public class StatisticsTask extends TimerTask {
	private ConcurrentHashMap<SocketChannel, PipeEndpoint> pipeEndpoints;

	private Logger log = Logger.getLogger(StatisticsTask.class);

	public StatisticsTask(
			ConcurrentHashMap<SocketChannel, PipeEndpoint> pipeEndpoints) {
		this.pipeEndpoints = pipeEndpoints;
	}

	public void run() {
		if (pipeEndpoints.size() > 0) {
			ArrayList<Pipe> pipes = new ArrayList<Pipe>();
			int connectedPipes = 0;

			for (SocketChannel socketChannel : pipeEndpoints.keySet()) {
				Pipe pipe = pipeEndpoints.get(socketChannel).getPipe();

				if (!pipes.contains(pipe)) {
					pipes.add(pipe);
					if (pipe.isConnected()) {
						connectedPipes++;
					}
				}
			}

			if (pipes.size() > 0) {
				log.info("Currently " + pipes.size() + " pipe(s), "
						+ connectedPipes + " connected");
			}
		}
	}
	
}
