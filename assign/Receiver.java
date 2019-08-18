import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
    private DatagramSocket socket;
    private String filePath;
    private PrintWriter logger;
    private InetAddress IPAddress;
    private int clientPort;
    private long startTime;

    private Receiver(String[] args) throws Exception {
        socket = new DatagramSocket(Integer.parseInt(args[0]));
        filePath = args[1];
        logger = new PrintWriter(new FileWriter("Receiver_log.txt"));
    }

    public static void main(String[] args) {
        try {
            System.out.println("Starting...");
            new Receiver(args).start();
            System.out.println("Finish...");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        String mode = "LISTEN";
        startTime = System.currentTimeMillis();
        List<DataSegment> dataSegments = new ArrayList<>();
        List<Integer> cacheIndexes = new ArrayList<>();
        Map<Integer, DataSegment> coaches = new HashMap<>();

        int receiveBase = 0;
        int nSegment = 0;
        int nDataSegment = 0;
        int nCurrentSegment = 0;
        int nDuplicateSegment = 0;
        int nDuplicateACK = 0;
        int receiveLength = 14;
        int dataLength = 0;
        int serverIsn = 0;

        while (!Objects.equals(mode, "CLOSE")) {
            nSegment++;
            byte[] receiveData = new byte[receiveLength];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);
            IPAddress = receivePacket.getAddress();
            clientPort = receivePacket.getPort();
            byte[] obj = receivePacket.getData();
            DataSegment receiveDataSegment = DataSegment.getFromBytes(obj);

            if (!(receiveDataSegment == null | !Objects.requireNonNull(receiveDataSegment).isChecked())) {
                int segmentSequenceNumber = receiveDataSegment.getSequenceNumber();
                if (Objects.equals(mode, "LISTEN") && receiveDataSegment.getSYN() == 1) {
                    receiveBase = segmentSequenceNumber;
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "S", segmentSequenceNumber, receiveDataSegment.getSegmentLength(), receiveDataSegment.getACKNumber());
                    DataSegment dataSegment = new DataSegment(serverIsn++, receiveBase + 1, 0, 1, 0, null);
                    send(socket, dataSegment, "snd", "SA");
                    mode = "RECEIVE";
                } else if (Objects.equals(mode, "RECEIVE") && segmentSequenceNumber == receiveBase + 1 && receiveDataSegment.getACKNumber() == serverIsn && receiveDataSegment.getACK() == 1) {
                    receiveBase = segmentSequenceNumber;
                    receiveLength = 1024;
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "A", segmentSequenceNumber, receiveDataSegment.getSegmentLength(), receiveDataSegment.getACKNumber());
                    mode = "ESTABLISH";
                } else if (Objects.equals(mode, "ESTABLISH") && receiveDataSegment.getFIN() == 1) {
                    receiveBase = segmentSequenceNumber;
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "F", segmentSequenceNumber, receiveDataSegment.getSegmentLength(), receiveDataSegment.getACKNumber());
                    send(socket, new DataSegment(serverIsn, receiveBase + 1, 1, 0, 0, null), "snd", "A");
                    send(socket, new DataSegment(serverIsn, receiveBase + 1, 1, 0, 1, null), "snd", "F");
                    mode = "LAST";
                } else if (Objects.equals(mode, "LAST") && segmentSequenceNumber == receiveBase + 1 && receiveDataSegment.getACKNumber() == serverIsn + 1 && receiveDataSegment.getACK() == 1) {
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "A", segmentSequenceNumber, receiveDataSegment.getSegmentLength(), receiveDataSegment.getACKNumber());
                    mode = "CLOSE";
                } else if (Objects.equals(mode, "ESTABLISH")) {
                    ++nDataSegment;
                    if (dataLength < receiveDataSegment.getSegmentLength()) {
                        dataLength = receiveDataSegment.getSegmentLength();
                        receiveLength = dataLength + 14;
                    }
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "D", segmentSequenceNumber, receiveDataSegment.getSegmentLength(), receiveDataSegment.getACKNumber());
                    String nameToLog = "snd";
                    if (segmentSequenceNumber >= receiveBase) {
                        if (segmentSequenceNumber != receiveBase) {
                            if (!cacheIndexes.isEmpty() && (cacheIndexes.get(0) == segmentSequenceNumber || coaches.containsKey(segmentSequenceNumber))) {
                                if (cacheIndexes.get(0) == segmentSequenceNumber || coaches.containsKey(segmentSequenceNumber)) {
                                    nDuplicateSegment++;
                                    nDuplicateACK++;
                                    nameToLog = "snd/DA";
                                }
                            } else {
                                cacheIndexes.add(segmentSequenceNumber);
                                Collections.sort(cacheIndexes);
                                coaches.put(segmentSequenceNumber, receiveDataSegment);
                                nameToLog = "snd/DA";
                                nDuplicateACK++;
                            }
                        } else {
                            try{
                                dataSegments.add(receiveDataSegment);
                                receiveBase += receiveDataSegment.getSegmentLength();
                                while (!cacheIndexes.isEmpty() && (receiveBase == cacheIndexes.get(0))) {
                                    cacheIndexes.remove(0);
                                    dataSegments.add(coaches.get(cacheIndexes.get(0)));
                                    coaches.remove(cacheIndexes.get(0));
                                    receiveBase += coaches.get(cacheIndexes.get(0)).getSegmentLength();
                                }
                            } catch (Exception ignored){}
                        }
                    } else {
                        nDuplicateSegment++;
                        continue;
                    }
                    DataSegment ack = new DataSegment(serverIsn, receiveBase, 1, 0, 0, null);
                    send(socket, ack, nameToLog, "A");
                }
            }
        }
        socket.close();

        RandomAccessFile accessFile = new RandomAccessFile(new File(filePath), "rw");
        int writeCursor = 0;
        for (DataSegment dataSegment : dataSegments) {
            accessFile.seek(writeCursor);
            accessFile.write(Arrays.copyOfRange(dataSegment.getData(), 0, dataSegment.getSegmentLength()), 0, dataSegment.getSegmentLength());
            writeCursor += dataSegment.getSegmentLength();
        }
        accessFile.close();

        logger.println("=============================================================");
        logger.printf("%-45s %12d\n", "Amount of data received (bytes)", receiveBase - 1);
        logger.printf("%-45s %12d\n", "Total Segments Received", nSegment);
        logger.printf("%-45s %12d\n", "Data segments received", nDataSegment);
        logger.printf("%-45s %12d\n", "Data segments with Bit Errors", nCurrentSegment);
        logger.printf("%-45s %12d\n", "Duplicate data segments received", nDuplicateSegment);
        logger.printf("%-45s %12d\n", "Duplicate ACKs sent", nDuplicateACK);
        logger.println("=============================================================");
        logger.close();
    }

    private void send(DatagramSocket senderSocket, DataSegment dataSegment, String eventName, String packetName) throws IOException {
        senderSocket.send(new DatagramPacket(dataSegment.getBytes(), dataSegment.getLength(), IPAddress, clientPort));
        logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", eventName, (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, packetName, dataSegment.getSequenceNumber(), dataSegment.getSegmentLength(), dataSegment.getACKNumber());
    }
}
