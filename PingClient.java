import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.util.Date;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.DatagramSocket;
// send packets to server, and receive response from server.
// calculate packet loss rate and average RTT
public class PingClient {
	private static final int TIME_OUT = 1000;//1000ms timeout
	public static void main(String args[]) throws Exception {
		if (args.length < 2) {
			System.out.println("Required arguements: host, port");
			return;
		}
	}
	InetAddress server;
	int port = Integer.parseInt(args[1]); //port number PING
	server = InetAddress.getByName(args[0]); //server address PING
	DatagramSocket socket = new DatagramSocket(); //create datagram socket

	int sequence_number = 1;
	int lost = 0;

	long minDelay = 0;
    long maxDelay = 0;
    long averageDelay = 0;
	// send packets
	for (sequence_number = 1; sequence_number <= 10; sequence_number++) {
		// create a new date, and get the time with unit ms
		Date currentTime = new Date();
		long msSend = currentTime.getTime();
		// the information in the packet is the PING number
		// and the time
		String str = "PING " + sequence_number + " " + msSend + "\n";
		//format: PING sequence_number time CRLF
		byte[] buffer = new byte[1024];
		buffer = str.getBytes();
		// transfer the type to BYTES
		// and the packet should include string/ip/port..
		DatagramPacket ping = new DatagramPacket(buffer, buffer.length, server, port);
		//create ping datagram to host:port
		socket.send(ping);
		// send the packet out by using socket.send

		//detect replies
		try {
			socket.setSoTimeout(TIME_OUT);
			//1000ms timeout
			//receive response
		        DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
		        socket.receive(response);
			//record delay (rtt)
		        currentTime = new Date();
		        long msReceived = currentTime.getTime();
		        long delay = msReceived - msSend;
		        if (minDelay == 0 & maxDelay == 0) {
		        	minDelay = delay;
		        	maxDelay = delay;
		        }
		        if (delay < minDelay)
		        	minDelay = delay;
		        else if (delay > maxDelay)
		        	maxDelay = delay;
		        averageDelay += delay / (sequence_number + 1);
		        printData(response, sequence_number, delay);
			}
			// timeout, packet lost
			// if its over time, then print TIME_OUT
			// need to tell the number of packet lost, min/max/avg delay
			catch (IOException e) {
				System.out.println("Packet " + sequence_number + " timeout");
				lost = lost + 1;
				//update lost packets count
			}
		}
		//output final results
		//as there are total 10 ping request to the server
		System.out.println("10 Packets Sent, " + lost + " Packets Lost\n" +
			"min rtt = " + minDelay + " ms" +
			", max rtt = " + maxDelay + " ms" +
			", average rtt = " + averageDelay +" ms");
		socket.close();
	}

	//print packet data, modified from PingServer.java
	private static void printData(DatagramPacket request, int sequence, long delayTime) throws Exception{
		// provide references to the bytes from the array of packets.
		byte[] buf = request.getData();
		// Wrap the bytes in a byte arr
		// bytearray->input stream reader->buffer reader
		
		// so that you can read the data as a stream of bytes.
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);
		// Wrap the byte array output stream in an input stream reader,
		// so you can read the data as a stream of characters.
		InputStreamReader isr = new InputStreamReader(bais);
		// Wrap the input stream reader in a buffered reader,
		// so you can read the character data a line at a time.
		// (A line is a sequence of chars terminated by any combination of \r and \n.)
		BufferedReader br = new BufferedReader(isr);
		// The message data is contained in a single line, so read this line.
		String line = br.readLine();
		// Print host address and data received from it.
		// if its not timeout, what i should printout
		System.out.println(
		"ping to " + request.getAddress().getHostAddress() +
		", seq = " + sequence +
		", rtt = " + delayTime + " ms");
	}
}
