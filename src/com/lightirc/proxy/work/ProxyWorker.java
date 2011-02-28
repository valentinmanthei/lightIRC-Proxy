package com.lightirc.proxy.work;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.lightirc.proxy.events.OpsChangeEvent;
import com.lightirc.proxy.events.ServerConnectEvent;
import com.lightirc.proxy.pipe.Pipe;
import com.lightirc.proxy.pipe.PipeEndpoint;

public class ProxyWorker implements Runnable {
	private BlockingQueue<WorkFragment> queue;

	private Logger log = Logger.getLogger(ProxyWorker.class);

	public ProxyWorker(BlockingQueue<WorkFragment> queue) {
		this.queue = queue;
	}

	public void run() {
		boolean running = true;

		while (running) {
			try {
				WorkFragment workFragment = queue.take();

				work(workFragment);

				if (!workFragment.getPipeEndpoint().getPipe().isClosed()) {
					workFragment.getProxy().queueEvent(
							new OpsChangeEvent(workFragment.getPipeEndpoint()
									.getPipe(), workFragment.getPipeEndpoint()
									.getSocketChannel(), SelectionKey.OP_READ));
				}
			} catch (InterruptedException exception) {
				running = false;
			}
		}
	}

	public void work(WorkFragment workFragment) {
		PipeEndpoint pipeEndpoint = workFragment.getPipeEndpoint();
		Pipe pipe = pipeEndpoint.getPipe();

		if (!pipe.isClosed()) {
			switch (workFragment.getType()) {
			case WAIT_FOR_SERVER_DETAILS:
				waitForServerDetails(workFragment);
				break;
			case SERVER_CONNECTED:
				serverConnected(workFragment);
				break;
			case EXCHANGE:
				exchange(pipeEndpoint);
				break;
			}
		}
	}

	private int readIntoBuffer(PipeEndpoint pipeEndpoint) {
		Pipe pipe = pipeEndpoint.getPipe();
		ByteBuffer buffer = pipeEndpoint.getBuffer();
		SocketChannel socketChannel = pipeEndpoint.getSocketChannel();

		int read = 0;

		try {
			buffer.clear();
			read = socketChannel.read(buffer);
			buffer.flip();

			log.debug(pipe + " read " + read + " bytes from " + pipeEndpoint);
		}
		/* Remote forced close */
		catch (IOException exception) {
			pipe.close();
		}

		/* Reached EOF */
		if (read == -1) {
			pipe.close();
		}

		return read;
	}

	private void waitForServerDetails(WorkFragment workFragment) {
		PipeEndpoint pipeEndpoint = workFragment.getPipeEndpoint();
		Pipe pipe = pipeEndpoint.getPipe();

		if (readIntoBuffer(pipeEndpoint) > 0) {
			String input = new String(pipeEndpoint.getBuffer().array());
			if (input.startsWith("PROXY")) {
				String[] parts = input.split(" ");

				if (parts.length == 3) {
					Matcher matcher = Pattern.compile("([0-9]*)").matcher(
							parts[2]);
					if (matcher.find()) {
						int port = new Integer(matcher.group());
						InetSocketAddress inetSocketAddress = new InetSocketAddress(
								parts[1], port);

						log.info(pipe + " requests endpoint "
								+ inetSocketAddress.toString());

						try {
							SocketChannel socketChannelToServer = SocketChannel
									.open();
							socketChannelToServer.configureBlocking(false);

							pipeEndpoint.getOtherEndpoint().setSocketChannel(
									socketChannelToServer);

							workFragment.getProxy().queueEvent(
									new ServerConnectEvent(pipe,
											socketChannelToServer,
											inetSocketAddress));
						} catch (IOException exception) {
							pipe.close();
						}
					}
				}
			}
		}
	}

	private void serverConnected(WorkFragment workFragment) {
		Pipe pipe = workFragment.getPipeEndpoint().getPipe();
		SocketChannel socketChannel = workFragment.getPipeEndpoint()
				.getSocketChannel();

		String message = ":" + workFragment.getProxy().getHostname()
				+ " PROXY\r\n";

		byte[] bytes = message.getBytes();
		ByteBuffer writeStringBuffer = ByteBuffer.allocate(bytes.length);
		writeStringBuffer = (ByteBuffer) writeStringBuffer.put(bytes).flip();

		try {
			int write = socketChannel.write(writeStringBuffer);

			log.debug(pipe + " wrote " + write + " bytes (" + message + ") to "
					+ workFragment.getPipeEndpoint());
		} catch (IOException exception) {
			pipe.close();
		}
	}

	private void exchange(PipeEndpoint pipeEndpoint) {
		Pipe pipe = pipeEndpoint.getPipe();
		SocketChannel ownSocketChannel = pipeEndpoint.getSocketChannel();
		SocketChannel otherSocketChannel = pipeEndpoint.getOtherEndpoint()
				.getSocketChannel();

		if (readIntoBuffer(pipeEndpoint) > 0) {
			if (ownSocketChannel.isOpen() && otherSocketChannel.isOpen()) {
				try {
					int write = otherSocketChannel.write(pipeEndpoint
							.getBuffer());

					log.debug(pipe + " wrote " + write + " bytes to "
							+ pipeEndpoint.getOtherEndpoint());
				} catch (IOException exception) {
					exception.printStackTrace();

					pipe.close();
				}
			}
		}
	}

	public void close() {
		Thread.currentThread().interrupt();
	}
	
}
