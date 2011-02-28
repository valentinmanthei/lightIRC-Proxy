package com.lightirc.proxy.pipe;

import org.apache.log4j.Logger;

import com.lightirc.proxy.LightIRCProxy;
import com.lightirc.proxy.events.PipeCloseEvent;

public class Pipe {
	private PipeEndpoint client;
	private PipeEndpoint server;

	private boolean closed;

	private LightIRCProxy proxy;

	private long init;
	private long id;

	private Logger log = Logger.getLogger(Pipe.class);

	public Pipe(LightIRCProxy proxy, long id) {
		this.proxy = proxy;
		this.id = id;
		this.init = System.currentTimeMillis();

		client = new PipeEndpoint(this, PipeEndpoint.Type.CLIENT);
		server = new PipeEndpoint(this, PipeEndpoint.Type.SERVER);

		client.setOtherEndpoint(server);
		server.setOtherEndpoint(client);
	}

	public void close() {
		closed = true;

		log.info("Pipe " + id + " closed from " + client + " to " + server);

		if (client != null) {
			client.close();
		}

		if (server != null) {
			server.close();
		}

		proxy.queueEvent(new PipeCloseEvent(this));
	}

	public long getInit() {
		return init;
	}

	public long getId() {
		return id;
	}

	public PipeEndpoint getClient() {
		return client;
	}

	public PipeEndpoint getServer() {
		return server;
	}

	public boolean isClosed() {
		return closed;
	}

	public boolean isConnected() {
		boolean connected = false;

		if (client != null && client.getSocketChannel() != null
				&& client.getSocketChannel().isConnected() && server != null
				&& server.getSocketChannel() != null
				&& server.getSocketChannel().isConnected()) {
			connected = true;
		}

		return connected;
	}

	public String toString() {
		return "Pipe ID " + id;
	}

}
