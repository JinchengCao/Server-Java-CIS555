package edu.upenn.cis.cis455.webserver;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This is a CIS555 assignment to build a server that supports "GET" and "HEAD"
 * request
 * 
 * @author <Jincheng Cao>
 */

public class HttpServer extends Thread {
	private File rootDirectory;
	private String index = "index.html";
	private ServerSocket server;
	private int numberOfThreads = 200;
	private boolean noArgument = false;
	public Thread[] ts = new Thread[numberOfThreads];
	private static boolean shutdown = false;

	public HttpServer(File rootDirectory, int port, String indexFileName)
			throws IOException {
		this.rootDirectory = rootDirectory;
		this.index = indexFileName;
		this.server = new ServerSocket(port);
	}

	private HttpServer(File rootDirectory, int port) throws IOException {
		this(rootDirectory, port, "index.html");
	}

	private HttpServer() throws IOException {
		this.noArgument = true;
		this.server = new ServerSocket(8080);
	}

	public void run() {
		for (int i = 0; i < numberOfThreads; i++) {
			if (this.noArgument == true) {
				ts[i] = new Thread(new RequestProcessor());
				ts[i].start();
			} else {
				ts[i] = new Thread(new RequestProcessor(rootDirectory,
						index, ts));
				ts[i].start();
			}

		}

		System.out.println("Accepting connection on port "
				+ server.getLocalPort());
		System.out.println("Root: " + rootDirectory);

		while (!RequestProcessor.shutdown) {
			try {
				Socket request = server.accept();
				RequestProcessor.processRequest(request);
				System.out.println("trying");

			} catch (IOException e) {
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
		}

		try {
			server.close();
			this.shutdown = true;
			for (int i = 0; i < numberOfThreads; i++) {
				ts[i].interrupt();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Takes in port number and a root directory.
	 * Runs web server and processes requests.
	 * @param args
	 *            a port number as arg[0] and a root directory/file as arg[1]
	 */
	public static void main(String[] args) {
		File docroot;
		if (args.length == 0) {
			try {
				HttpServer webserver = new HttpServer();
				webserver.start();
			} catch (IOException e) {
				System.out.println("Server could not start because of an "
						+ e.getClass());
				System.out.println(e);
			}
		}
		try {
			docroot = new File(args[1]);
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Usage: java JHTTP docroot port indexfile");
			return;
		}
		int port;
		try {
			port = Integer.parseInt(args[0]);
			if (port < 0 || port > 65535) {
				port = 80;
			}
		} catch (Exception e) {
			port = 80;
		}
		try {
			HttpServer webserver = new HttpServer(docroot, port);
			webserver.start();

		} catch (IOException e) {
			System.out.println("Server could not start because of an "
					+ e.getClass());
			System.out.println(e);
		}
	}
}