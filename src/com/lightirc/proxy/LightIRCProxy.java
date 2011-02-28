package com.lightirc.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;

import com.lightirc.proxy.events.Event;
import com.lightirc.proxy.events.OpsChangeEvent;
import com.lightirc.proxy.events.PipeCloseEvent;
import com.lightirc.proxy.events.ServerConnectEvent;
import com.lightirc.proxy.pipe.Pipe;
import com.lightirc.proxy.pipe.PipeEndpoint;
import com.lightirc.proxy.tasks.ExpirePipeTask;
import com.lightirc.proxy.tasks.StatisticsTask;
import com.lightirc.proxy.work.ProxyWorker;
import com.lightirc.proxy.work.WorkFragment;

public class LightIRCProxy implements Runnable {
	public static final int DEFAULT_PORT = 8003;
	public static final int THREADS = Runtime.getRuntime()
			.availableProcessors() + 1;

	private Selector selector;
	private long pipes;
	String hostname;
	private int port;

	private ConcurrentHashMap<SocketChannel, PipeEndpoint> pipeEndpoints = new ConcurrentHashMap<SocketChannel, PipeEndpoint>();
	private BlockingQueue<WorkFragment> queue = new LinkedBlockingDeque<WorkFragment>();
	private LinkedList<Event> events = new LinkedList<Event>();

	private ExecutorService executorService;

	private Logger log = Logger.getLogger(LightIRCProxy.class);

	public LightIRCProxy(int port) {
		this.port = port;

		log.info("Starting LightIRCProxy using " + THREADS
				+ " worker threads on port " + port);
	}

	public void queueEvent(Event event) {
		synchronized (events) {
			events.add(event);
		}

		selector.wakeup();
	}

	public void run() {
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException exception) {
			hostname = "lightIRCProxy";
		}

		Timer timer = new Timer();
		timer.schedule(new ExpirePipeTask(pipeEndpoints), 5000, 2000);
		timer.schedule(new StatisticsTask(pipeEndpoints), 5000, 60000);

		executorService = Executors.newFixedThreadPool(THREADS);
		for (int i = 0; i < THREADS; i++) {
			executorService.execute(new ProxyWorker(queue));
		}

		ServerSocketChannel serverSocketChannel;

		try {
			selector = SelectorProvider.provider().openSelector();

			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.socket().bind(new InetSocketAddress(port));
			serverSocketChannel.configureBlocking(false);

			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException exception) {
			exception.printStackTrace();
		}

		log.info("Startup complete");

		while (true) {
			LinkedList<Event> tmpEvents;
			synchronized (events) {
				tmpEvents = new LinkedList<Event>(events);
				events.clear();
			}

			Iterator<Event> iterator = tmpEvents.iterator();
			while (iterator.hasNext()) {
				Event event = iterator.next();

				if (event instanceof OpsChangeEvent) {
					OpsChangeEvent opsChangeEvent = (OpsChangeEvent) event;

					SelectionKey key = opsChangeEvent.getSocketChannel()
							.keyFor(selector);
					if (key != null && key.isValid()) {
						key.interestOps(opsChangeEvent.getOps());
					}
				} else if (event instanceof ServerConnectEvent) {
					ServerConnectEvent serverConnectEvent = (ServerConnectEvent) event;

					try {
						serverConnectEvent.getSocketChannel().register(
								selector, SelectionKey.OP_CONNECT);
						serverConnectEvent.getSocketChannel().connect(
								serverConnectEvent.getInetSocketAddress());

						synchronized (pipeEndpoints) {
							pipeEndpoints.put(
									serverConnectEvent.getSocketChannel(),
									event.getPipe().getServer());
						}
					} catch (Exception exception) {
						event.getPipe().close();
					}
				} else if (event instanceof PipeCloseEvent) {
					SocketChannel socketChannelClient = event.getPipe()
							.getClient().getSocketChannel();
					SocketChannel socketChannelServer = event.getPipe()
							.getServer().getSocketChannel();

					synchronized (pipeEndpoints) {
						if (socketChannelClient != null
								&& pipeEndpoints.remove(socketChannelClient) != null) {
							log.debug("Client SocketChannel successfully removed from "
									+ event.getPipe());
						}
						if (socketChannelServer != null
								&& pipeEndpoints.remove(socketChannelServer) != null) {
							log.debug("Server SocketChannel successfully removed from "
									+ event.getPipe());
						}
					}
				}
			}

			try {
				selector.select();
			} catch (IOException exception) {
				exception.printStackTrace();
			}

			Iterator<SelectionKey> selectedKeys = selector.selectedKeys()
					.iterator();
			while (selectedKeys.hasNext()) {
				SelectionKey key = selectedKeys.next();
				selectedKeys.remove();

				if (!key.isValid()) {
					continue;
				}

				try {
					if (key.isAcceptable()) {
						accept(key);
					} else if (key.isConnectable()) {
						connect(key);
					} else if (key.isReadable()) {
						read(key);
					}
				} catch (IOException exception) {
					exception.printStackTrace();
				}
			}
		}
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key
				.channel();

		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);

		Pipe pipe = new Pipe(this, ++pipes);
		pipe.getClient().setSocketChannel(socketChannel);
		socketChannel.register(selector, SelectionKey.OP_READ);

		log.info(pipe + " has a client from "
				+ socketChannel.socket().getRemoteSocketAddress().toString());

		synchronized (pipeEndpoints) {
			pipeEndpoints.put(socketChannel, pipe.getClient());
		}
	}

	private void connect(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		PipeEndpoint pipeEndpoint = null;

		synchronized (pipeEndpoints) {
			pipeEndpoint = pipeEndpoints.get(socketChannel);
		}

		if (pipeEndpoint != null) {
			boolean connected = false;

			try {
				connected = socketChannel.finishConnect();
			} catch (Exception exception) {
				log.error("Connection to server " + pipeEndpoint.toString()
						+ " failed");
			}

			if (!connected) {
				pipeEndpoint.getPipe().close();
			} else {
				log.info(pipeEndpoint.getPipe() + " connected to "
						+ pipeEndpoint.toString());

				queue.add(new WorkFragment(this, pipeEndpoint
						.getOtherEndpoint(), WorkFragment.Type.SERVER_CONNECTED));

				socketChannel.register(selector, SelectionKey.OP_READ);
			}
		}
	}

	private void read(SelectionKey key) throws IOException {
		PipeEndpoint pipeEndpoint = null;

		synchronized (pipeEndpoints) {
			pipeEndpoint = pipeEndpoints.get((SocketChannel) key.channel());
		}

		if (pipeEndpoint != null) {
			queueEvent(new OpsChangeEvent(pipeEndpoint.getPipe(),
					pipeEndpoint.getSocketChannel(), 0));

			WorkFragment workFragment = null;

			if (pipeEndpoint.getType().equals(PipeEndpoint.Type.CLIENT)) {
				/* No server yet */
				if (pipeEndpoint.getOtherEndpoint().getSocketChannel() == null) {
					workFragment = new WorkFragment(this, pipeEndpoint,
							WorkFragment.Type.WAIT_FOR_SERVER_DETAILS);
				} else {
					workFragment = new WorkFragment(this, pipeEndpoint,
							WorkFragment.Type.EXCHANGE);
				}
			} else if (pipeEndpoint.getType().equals(PipeEndpoint.Type.SERVER)) {
				workFragment = new WorkFragment(this, pipeEndpoint,
						WorkFragment.Type.EXCHANGE);
			}

			if (workFragment != null) {
				queue.add(workFragment);
			}
		} else {
			log.warn("PipeEndpoint not found");
		}
	}

	public String getHostname() {
		return hostname;
	}

	public static void main(String[] args) {
		int port;

		if (args.length != 1) {
			port = DEFAULT_PORT;
		} else {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException exception) {
				port = DEFAULT_PORT;
			}
		}

		new Thread(new LightIRCProxy(port)).start();
	}

}
