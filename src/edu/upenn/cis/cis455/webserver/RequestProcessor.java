package edu.upenn.cis.cis455.webserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.Thread.State;
import java.net.Socket;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;

/**
 * This is a CIS555 assignment to build a server that supports "GET" and "HEAD" request.
 * 
 * @author <Jincheng Cao>
 */

public class RequestProcessor implements Runnable {
	private static List pool = new LinkedList();
	private File rootDirectory;
	private String index = "index.html";
	public boolean noArgument = false;
	private static int poolSize = 25000;
	public static boolean shutdown = false;
	public static boolean control = true;
	public static Thread[] ts;

	public RequestProcessor(File rootDirectory, String index,
			Thread[] ts) {

		this.rootDirectory = rootDirectory;
		try {
			this.rootDirectory = rootDirectory
					.getCanonicalFile();
		} catch (IOException e) {
		}
		if (index != null) {
			this.index = index;
		}
		this.ts = ts;
	}

	public RequestProcessor() {
		this.noArgument = true;
	}
	
	/**
	 * Takes a request and adds it to the socket pool. 
	 * Synchronizes the socket pool.
	 * Lets the request wait if the pool is full.
	 * @param request
	 *            a request socket.
	 */
	public static void processRequest(Socket request)
			throws InterruptedException {
		while (pool.size() == poolSize) {
			synchronized (pool) {
				System.out.println("socket pool is full!");
				pool.wait();
			}
		}
		synchronized (pool) {
			pool.add(pool.size(), request);
			pool.notifyAll();
		}
	}

	/**
	 * Removes one socket from the pool and gets input stream.
	 * Analyzes the request and handles it based on the request category.
	 * Writes to the output stream with proper response.
	 */
	public void run() {
		while (!shutdown) {
			Socket connection;
			synchronized (pool) {
				while (pool.isEmpty()) {
					try {
						pool.wait();
					} catch (InterruptedException e) {
					}
				}
				connection = (Socket) pool.remove(0);
			}
			try {
				OutputStream raw = new BufferedOutputStream(
						connection.getOutputStream());
				Writer out = new OutputStreamWriter(raw);
				Reader in = new InputStreamReader(new BufferedInputStream(
						connection.getInputStream()), "ASCII");
				StringBuffer request = new StringBuffer(1000);
				while (true) {
					int c = in.read();
					if (c == '\t' || c == '\n' || c == -1) {
						break;
					}
					request.append((char) c);
				}
				String get = request.toString().replaceAll("%20", "\\ ");
				System.out.println(get);
				if (noArgument == true) {
					System.out.println("No Argument");
					noArgumentProcessor(out, get, raw);
				} else {
					String[] partsOfGet = get.split(" ");

					String method = partsOfGet[0];
					String firstSlash = "";
					for (int i = 1; i < partsOfGet.length - 1; i++) {
						firstSlash += partsOfGet[i];
						if (i != partsOfGet.length - 2)
							firstSlash += " ";
					}
					System.out.println(firstSlash);
					if (firstSlash.compareTo("/shutdown") == 0) {
						shutdown = true;
						out.close();
						in.close();
						raw.close();
					} else if (firstSlash.compareTo("/control") == 0) {
						control = true;
						controlHanddler(out, get, raw);
					} else if (firstSlash.compareTo("/") == 0) {
						File parsedFile = rootDirectory;
						categoryParsedFile(parsedFile, out, get, raw);
					} else {
						File parsedFile = new File(
								rootDirectory.getCanonicalPath(),
								firstSlash);
						categoryParsedFile(parsedFile, out, get, raw);
					}

				}
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					connection.close();
				} catch (IOException e2) {
				}
			}
		}
	}
	
	/**
	 * Takes a writer, an output stream and a string from the request.
	 * Handles control request.
	 * Presents author information and thread states.
	 * Offers shutdown button.
	 * @param out
	 *            a writer.
	 * @param get
	 *            a string from the request.
	 * @param raw
	 *            an output stream.
	 */

	private void controlHanddler(Writer out, String get, OutputStream raw)
			throws IOException {
		String state = "";
		out.write("HTTP/1.1 200 OK\r\n");
		Date now = new Date();
		out.write("Date: " + now + "\r\n");
		out.write("Server: HttpServer 1.1\r\n");
		out.write("Content-Type: " + "text/html" + "\r\n\r\n");
		out.flush();
		out.write("<HTML>\r\n");
		out.write("<HEAD><TITLE>Name And Login Name</TITLE></HRAD>\r\n");
		out.write("<BODY>\r\n");
		out.write("<H1>Full Name: Jincheng Cao</H1>\r\n");
		out.write("<H1>Login Name: caoj</H1>\r\n");
		out.write("<H1>control panel</H1>\r\n");
		out.write("<ul>\r\n");
		for (int i = 0; i < ts.length; i++) {
			State status = ts[i].getState();
			state = "thread " + i + " state: " + status + "\n";
			out.write("<li><H2>" + state + "</H2></li>\r\n");
		}
		out.write("</ul>\r\n");
		out.write("<style>\r\n");
		out.write("h2{font-size: 16} h1{font-size:20}");
		out.write("</style>\r\n");
		out.write("<a href=" + "'" + "/shutdown" + "'" + ">"
				+ "<button type='button'>Shutdown</button>" + "</a>");
		out.write("</BODY></HTML>\r\n");
		out.flush();

	}

	/**
	 * Takes a file to be parsed, a writer, an output stream and a string from the request.
	 * Handles request other than "shutdown" and "control".
	 * Categorizes the file and parses it into each helper method.
	 * @param parsedFile
	 *            a file to be processed.
	 * @param out
	 *            a writer.
	 * @param get
	 *            a string from the request.
	 * @param raw
	 *            an output stream.
	 */
	private void categoryParsedFile(File parsedFile, Writer out, String get,
			OutputStream raw) throws IOException {
		if (parsedFile.isFile()) {
			System.out.println("is file");
			fileProcessor(out, get, raw);
		} else if (parsedFile.isDirectory()) {
			System.out.println("is directory");
			directoryProcessor(out, get, parsedFile);
		} else if (!rootDirectory.exists()) {
			System.out.println("Doesn't exist");
			nonExistProcessor(out, get);
		} else {
			System.out.println("else");
			nonExistProcessor(out, get);
		}

	}
	
	/**
	 * Takes an output stream, a writer and a string from the request.
	 * Handles request when there's no argument.
	 * Presents the author's information.
	 * @param out
	 *            a writer.
	 * @param get
	 *            a string from the request.
	 * @param raw
	 *            an output stream.
	 */
	private void noArgumentProcessor(Writer out, String get, OutputStream raw)
			throws IOException {
		out.write("HTTP/1.1 200 OK\r\n");
		Date now = new Date();
		out.write("Date: " + now + "\r\n");
		out.write("Server: HttpServer 1.1\r\n");
		out.write("Content-Type: " + "text/html" + "\r\n\r\n");
		out.flush();
		out.write("<HTML>\r\n");
		out.write("<HEAD><TITLE>Name And Login Name</TITLE></HRAD>\r\n");
		out.write("<BODY>\r\n");
		out.write("<H1>Full Name: Jincheng Cao</H1>\r\n");
		out.write("<H1>Login Name: caoj</H1>\r\n");
		out.write("</BODY></HTML>\r\n");
		out.flush();
	}
	/**
	 * Takes an output stream, a writer and a string from the request.
	 * Handles request when the file does not exist.
	 * Presents the error message
	 * @param out
	 *            a writer.
	 * @param get
	 *            a string from the request.
	 */
	private void nonExistProcessor(Writer out, String get) throws IOException {
		String[] partsOfGet = get.split(" ");
		String version = partsOfGet[partsOfGet.length - 1];
		String method = partsOfGet[0];

			out.write("HTTP/1.1 404 File Not Found\r\n");
			Date now = new Date();
			out.write("Date: " + now + "\r\n");
			out.write("Server: HttpServer 1.1\r\n");
			out.write("Content-Type: text/html\r\n\r\n");
			out.flush();

        if(method.compareTo("GET") == 0){
			out.write("<HTML>\r\n");
			out.write("<HEAD><TITLE>File Not Found</TITLE></HRAD>\r\n");
			out.write("<BODY>\r\n");
			out.write("<H1>HTTP Error 404: File Not Found</H1>");
			out.write("</BODY></HTML>\r\n");
			out.flush();
        }
	}
	/**
	 * Takes a file to be parsed, a writer and a string from the request.
	 * Handles request when the link is a directory
	 * Presents all the files/folders in that directory
	 * @param out
	 *            a writer.
	 * @param get
	 *            a string from the request.
	 * @param parsedFile
	 *            a file to be parsed
	 */
	private void directoryProcessor(Writer out, String get, File parsedFile)
			throws IOException {
		String[] partsOfGet = get.split(" ");
		String version = partsOfGet[partsOfGet.length - 1];
		String method = partsOfGet[0];
		File folder = new File(parsedFile.getAbsolutePath());
		File[] files = folder.listFiles();
	
			out.write("HTTP/1.1 200 OK\r\n");
			Date now = new Date();
			out.write("Date: " + now + "\r\n");
			out.write("Server: JHTTP 1.0\r\n");
			// out.write("Content-length: " + theData.length + "\r\n");
			out.write("Content-Type: " + "text/html" + "\r\n\r\n");
			out.flush();

		if(method.compareTo("GET") == 0){
			out.write("<html>\r\n");
			out.write("<ul>\r\n");
			for (File f : files) {
				String relativePath = f.getAbsolutePath().replace(
						rootDirectory.getAbsolutePath(), "");
				String str = "<li><a href=" + "'" + relativePath + "'" + ">"
						+ f.getName() + "</a></li>\r\n";
				out.write(str);
	
			}
			out.write("</ul>\r\n");
			out.write("</htl>\r\n");
			out.flush();
		}
	}
	/**
	 * Takes an output stream, a writer and a string from the request.
	 * Handles request when the link is a file.
	 * Presents the file in the browser.
	 * @param out
	 *            a writer.
	 * @param get
	 *            a string from the request.
	 * @param raw
	 *            an output stream.
	 */
	private void fileProcessor(Writer out, String get, OutputStream raw)
			throws IOException {
		String fileName;
		String contentType;

		String[] partsOfGet = get.split(" ");
		String method = partsOfGet[0];
		String version = "";
		if (method.compareTo("GET") == 0 || method.compareTo("HEAD") == 0) {

			fileName = "";
			for (int i = 1; i < partsOfGet.length - 1; i++) {
				fileName += partsOfGet[i];
				if (i != partsOfGet.length - 2)
					fileName += " ";
			}
			System.out.println(fileName);
			int lastslash = rootDirectory.getCanonicalPath()
					.lastIndexOf("/");
			String temp = rootDirectory.getCanonicalPath();
			String root = temp.substring(0, lastslash + 1);
			String localName = temp.substring(lastslash + 1, temp.length());
			if (fileName.compareTo("/") == 0) {
				fileName += localName;
			}
			System.out.println(fileName);
			contentType = getContentType(fileName);

			version = partsOfGet[partsOfGet.length - 1];
			System.out.println("version is" + version);

			String filePath = rootDirectory.getCanonicalPath()
					+ fileName;
			File thisFile = new File(filePath);

				if (thisFile.canRead()) {
					DataInputStream fi = new DataInputStream(
							new BufferedInputStream(
									new FileInputStream(thisFile)));
					byte[] data = new byte[(int) thisFile.length()];
					fi.readFully(data);
					fi.close();
					System.out.println("should shown");
					out.write("HTTP/1.1 200 OK\r\n");
					Date now = new Date();
					out.write("Date: " + now + "\r\n");
					out.write("Server: JHTTP 1.0\r\n");
					out.write("Content-length: " + data.length + "\r\n");
					out.write("Content-Type: " + contentType + "\r\n\r\n");
					out.flush();
					if(method.compareTo("GET") == 0){
						raw.write(data);
						raw.flush();
					}
				} else {
					out.write("HTTP/1.1 404 File Not Found\r\n");
					Date now = new Date();
					out.write("Date: " + now + "\r\n");
					out.write("Server: HttpServer 1.1\r\n");
					out.write("Content-Type: text/html\r\n\r\n");
					out.flush();
					
					out.write("<HTML>\r\n");
					out.write("<HEAD><TITLE>File Not Found</TITLE></HRAD>\r\n");
					out.write("<BODY>\r\n");
					out.write("<H1>HTTP Error 404: File Not Found</H1>");
					out.write("</BODY></HTML>\r\n");
					out.flush();
				}

		} else {
			out.write("HTTP/1.1 501 Not Implemented\r\n");
			Date now = new Date();
			out.write("Date: " + now + "\r\n");
			out.write("Server: HttpServer 1.1\r\n");
			out.write("Content-Type: text/html\r\n\r\n");
			out.flush();
			if(method.compareTo("GET")==0){
			out.write("<HTML>\r\n");
			out.write("<HEAD><TITLE>Not Implemented</TITLE></HRAD>\r\n");
			out.write("<BODY>\r\n");
			out.write("<H1>HTTP Error 501: Not Implemented</H1>");
			out.write("</BODY></HTML>\r\n");
			out.flush();
	        }
		}
			
	}

	/**
	 * Takes a name of a file.
	 * Guesses the content type of the file based on its name.
	 * @param name
	 *            name of a file.
	 * @return a string representing the content type of the file.
	 */
	public static String getContentType(String name) {
		if (name.endsWith(".html") || name.endsWith(".htm")) {
			return "text/html";
		} else if (name.endsWith(".txt") || name.endsWith(".java")) {
			return "text/plain";
		} else if (name.endsWith(".gif")) {
			return "image/gif";
		} else if (name.endsWith(".class")) {
			return "application/octet-stream";
		} else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
			return "image/jpeg";
		} else if (name.endsWith(".png")) {
			return "image/png";
		} else if (name.endsWith(".mp3")) {
			return "audio/mpeg";
		} else if (name.endsWith(".pdf")) {
			return "application/pdf";
		} else if (name.endsWith(".doc") || name.endsWith(".docx")) {
			return "application/msword";
		} else if (name.endsWith(".zip") || name.endsWith(".exe")) {
			return "application/octet-stream";
		} else {
			return "text/plain";
		}
	}
}