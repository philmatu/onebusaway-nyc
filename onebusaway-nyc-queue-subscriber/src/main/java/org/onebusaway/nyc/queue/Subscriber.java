package org.onebusaway.nyc.queue;

import org.zeromq.ZMQ;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Subscriber {
	public static final String HOST_KEY = "mq.host";
	public static final String PORT_KEY = "mq.port";
	public static final String TOPIC_KEY = "mq.topic";
	public static final String PBDIR_KEY = "pb.dir";
	public static final String PB_LIMIT_KEY = "pb.limit";
	private static final String DEFAULT_HOST = "queue.staging.obanyc.com";
	private static final int DEFAULT_PORT = 5563;
	private static final String DEFAULT_TOPIC = "bhs_queue";
	private static final int DEFAULT_PB_LIMIT = -1;

	public static void main(String[] args) {

    // Prepare our context and subscriber
    ZMQ.Context context = ZMQ.context(1);
    ZMQ.Socket subscriber = context.socket(ZMQ.SUB);

    String host = DEFAULT_HOST;
    if (System.getProperty(HOST_KEY) != null) {
					host = System.getProperty(HOST_KEY);
    }
    int port = DEFAULT_PORT;
    if (System.getProperty(PORT_KEY) != null) {
			try {
				port = Integer.parseInt(System.getProperty(PORT_KEY));
			} catch (NumberFormatException nfe) {
				port = DEFAULT_PORT;
			}
		}
		String topic = DEFAULT_TOPIC;
		if (System.getProperty(TOPIC_KEY) != null) {
			topic = System.getProperty(TOPIC_KEY);
		}

		String pbdir = null;
		if (System.getProperty(PBDIR_KEY) != null) {
			pbdir = System.getProperty(PBDIR_KEY);
		}

		int pblimit = DEFAULT_PB_LIMIT;
		if (System.getProperty(PB_LIMIT_KEY) != null) {
			try {
				pblimit = Integer.parseInt(System.getProperty(PB_LIMIT_KEY));
			} catch (NumberFormatException nfe) {
				pblimit = DEFAULT_PB_LIMIT;
			}
		}

		String bind = "tcp://" + host + ":" + port;
		subscriber.connect(bind);
		subscriber.subscribe(topic.getBytes());
		System.out.println("listening on " + bind);
		int nprocess = 0;
		while (true) {
			// Read envelope with address
			String address = new String(subscriber.recv(0));
			// Read message contents
			byte[] contents = subscriber.recv(0);
			if (pbdir == null)
				process(address, new String(contents));
			else
				processToDir(pbdir, address, contents);

			nprocess++;
			if (pblimit > 0 && nprocess >= pblimit)
				break;
		}
	}
	private static void process(String address, String contents) {
		System.out.println(address + " : " + toDate(new Date()) +  " : " + contents);
	}

	private static void processToDir(String dir, String address, byte[] contents) {
		writeToFile(dir + "/" + toDate(new Date()) + ".pb", contents);
	}

	protected static void writeToFile(String filename, byte[] contents) {
		File file = new File(filename);
		if (file.getParentFile() != null)
			file.getParentFile().mkdirs();
		try {
			DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
			dos.write(contents);
			dos.close();
		} catch(IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static String toDate(Date date) {
		if (date != null) {
			return dateFormatter.format(date);
		}
		return null;
	}

}
