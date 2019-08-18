
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sender {
    private DatagramSocket socket;
    private InetAddress receiverHostIP;
    private int receiverPort;
    private String filePath;
    private int MWS;
    private int MSS;
    private int gamma;
    private long startTime;
    private PrintWriter logger;
    private PLDModule PLDModule;
    private boolean isWorking;
    private int GBNBase;
    private int nDuplicate;
    private int nSent;
    private int nTimeout;
    private int nTransmit;

    private Sender(String args[]) throws Exception {
        this.socket = new DatagramSocket();
        isWorking = true;
        GBNBase = 0;
        nDuplicate = 0;
        nSent = 0;
        nTimeout = 0;
        nTransmit = 0;
        receiverHostIP = InetAddress.getByName(args[0]);
        receiverPort = Integer.parseInt(args[1]);
        filePath = args[2];
        MWS = Integer.parseInt(args[3]);
        MSS = Integer.parseInt(args[4]);
        gamma = Integer.parseInt(args[5]);
        startTime = System.currentTimeMillis();
        logger = new PrintWriter(new FileWriter("Sender_log.txt"));
        PLDModule = new PLDModule(socket, logger, startTime, receiverHostIP, receiverPort, Integer.parseInt(args[13]), Float.parseFloat(args[6]),
                Float.parseFloat(args[7]), Float.parseFloat(args[8]), Float.parseFloat(args[9]), Float.parseFloat(args[10]),
                Float.parseFloat(args[11]), Integer.parseInt(args[12]));
    }

    public static void main(String[] args) {
        try {
            System.out.println("Sending...");
            new Sender(args).start();
            System.out.println("Finish...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        int clientIsn = 0;
        int serverIsn = 0;
        int nPLD = 0;
        long timeout = 500 + gamma * 250;

        // do hand shake
        while (true) {
            send(socket, new DataSegment(clientIsn, serverIsn, 0, 1, 0, null), "snd", "S");
            DataSegment receiveSegment = receiveSegment();
            logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "SA", receiveSegment.getSequenceNumber(), receiveSegment.getSegmentLength(), receiveSegment.getACKNumber());
            if (receiveSegment.getSYN() != 1 || receiveSegment.getACKNumber() != clientIsn + 1) continue;
            serverIsn = receiveSegment.getSequenceNumber() + 1;
            send(socket, new DataSegment(clientIsn + 1, serverIsn, 1, 0, 0, null), "snd", "A");
            break;
        }

        // read the file and split to data segment
        GBNBase = ++clientIsn;
        int nextSeg = GBNBase;
        int readCursor;
        byte[] readBuffer = new byte[MSS];
        List<DataSegment> dataSegmentList = new ArrayList<>();
        RandomAccessFile file = new RandomAccessFile(filePath, "r");
        while ((readCursor = file.read(readBuffer)) != -1) {
            dataSegmentList.add(new DataSegment(clientIsn, serverIsn, Arrays.copyOf(readBuffer, readCursor)));
            clientIsn += readCursor;
        }
        file.close();

        int fileSize = dataSegmentList.stream().mapToInt(DataSegment::getSegmentLength).sum();
        Map<Integer, Long> times = new ConcurrentHashMap<>();
        java.util.Timer timer = new java.util.Timer();
        timer.schedule(new Timeout(times, timeout, dataSegmentList), 800, 800);
        getReceiveThread(dataSegmentList, fileSize).start();

        while (GBNBase < fileSize) {
            while (nextSeg < fileSize && nextSeg - GBNBase <= MWS) {
                DataSegment fileDataSegment = dataSegmentList.get(nextSeg / MSS);
                PLDModule.handle(fileDataSegment);
                nextSeg += fileDataSegment.getSegmentLength();
                times.put(fileDataSegment.getSequenceNumber(), System.currentTimeMillis());
                nPLD++;
                nTransmit++;
            }
        }

        boolean pending = false;
        send(socket, new DataSegment(nextSeg, serverIsn, 0, 0, 1, null), "snd", "F");
        while (true) {
            DataSegment receiveDataSegment = receiveSegment();
            if (pending || receiveDataSegment.getACK() != 1 || receiveDataSegment.getACKNumber() != nextSeg + 1) {
                if (pending && receiveDataSegment.getFIN() == 1 && receiveDataSegment.getACK() == 1 && receiveDataSegment.getACKNumber() == nextSeg + 1) {
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "F", receiveDataSegment.getSequenceNumber(), receiveDataSegment.getSegmentLength(), receiveDataSegment.getACKNumber());
                    send(socket, new DataSegment(nextSeg + 1, receiveDataSegment.getSequenceNumber() + 1, 1, 0, 0, null), "snd", "A");
                    break;
                }
            } else {
                logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "A", receiveDataSegment.getSequenceNumber(), receiveDataSegment.getSegmentLength(), receiveDataSegment.getACKNumber());
                pending = true;
            }
        }
        timer.cancel();
        PLDModule.close();
        logClose(fileSize, nPLD);
        logger.close();
    }

    private void duplicateACKHandle(List<DataSegment> dataSegmentList, Map<Integer, Integer> ACKs, int ACKKey) {
        if (ACKs.containsKey(ACKKey)) {
            if (ACKs.get(ACKKey) + 1 < 3) {
                ACKs.put(ACKKey, ACKs.get(ACKKey) + 1);
            } else {
                DataSegment fileDataSegment = dataSegmentList.get(ACKKey / MSS);
                try {
                    send(socket, fileDataSegment, "snd/RXT", "D");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                nSent++;
                nTransmit++;
                ACKs.remove(ACKKey);
            }
        } else {
            ACKs.put(ACKKey, 1);
        }
    }

    private void send(DatagramSocket senderSocket, DataSegment dataSegment, String eventName, String packetName) throws IOException {
        senderSocket.send(new DatagramPacket(dataSegment.getBytes(), dataSegment.getLength(), receiverHostIP, receiverPort));
        logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", eventName, (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, packetName, dataSegment.getSequenceNumber(), dataSegment.getSegmentLength(), dataSegment.getACKNumber());
    }

    private DataSegment receiveSegment() throws IOException {
        byte[] sa_receive = new byte[14];
        DatagramPacket receivePacket = new DatagramPacket(sa_receive, sa_receive.length);
        socket.receive(receivePacket);
        return DataSegment.getFromBytes(sa_receive);
    }

    private void logClose(int fileSize, int nPLD) {
        logger.println("=============================================================");
        logger.printf("%-45s %12d\n", "Size of the filePath (in Bytes)", fileSize);
        logger.printf("%-45s %12d\n", "Segments transmitted (including drop & RXT)", nTransmit);
        logger.printf("%-45s %12d\n", "Number of Segments handled by PLDModule", nPLD);
        logger.printf("%-45s %12d\n", "Number of Segments dropped", PLDModule.getnDrop());
        logger.printf("%-45s %12d\n", "Number of Segments Corrupted", PLDModule.getnCorrupt());
        logger.printf("%-45s %12d\n", "Number of Segments Re-ordered", PLDModule.getnReOrder());
        logger.printf("%-45s %12d\n", "Number of Segments Duplicated", PLDModule.getDuplicatedAmount());
        logger.printf("%-45s %12d\n", "Number of Segments Delayed", PLDModule.getnDelay());
        logger.printf("%-45s %12d\n", "Number of Retransmissions due to TIMEOUT", nTimeout);
        logger.printf("%-45s %12d\n", "Number of FAST RETRANSMISSION", nSent);
        logger.printf("%-45s %12d\n", "Number of DUP ACKS received", nDuplicate);
        logger.println("=============================================================");
    }

    private Thread getReceiveThread(List<DataSegment> dataSegmentList, int fileSize) {
        return new Thread(() -> {
            Map<Integer, Integer> ACKs = new HashMap<>();
            while (isWorking) {
                DataSegment receiveSegment = null;
                try {
                    receiveSegment = receiveSegment();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int ACKKey = Objects.requireNonNull(receiveSegment).getACKNumber();
                int reSeqNumber = receiveSegment.getSequenceNumber();
                if (GBNBase >= ACKKey) {
                    nDuplicate++;
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv/DA", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "A", reSeqNumber, receiveSegment.getSegmentLength(), ACKKey);
                    duplicateACKHandle(dataSegmentList, ACKs, ACKKey);
                } else if (receiveSegment.getACK() != 1) {
                    nDuplicate++;
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv/DA", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "A", reSeqNumber, receiveSegment.getSegmentLength(), ACKKey);
                    duplicateACKHandle(dataSegmentList, ACKs, ACKKey);
                } else {
                    logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rcv", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "A", reSeqNumber, receiveSegment.getSegmentLength(), ACKKey);
                    GBNBase = ACKKey;
                }
                isWorking = GBNBase < fileSize;
            }
        });
    }

    private class Timeout extends TimerTask {
        private final Map<Integer, Long> timeList;
        private final long timeout;
        private final List<DataSegment> dataSegmentList;

        Timeout(Map<Integer, Long> timeList, long timeout, List<DataSegment> dataSegmentList) {
            this.timeList = timeList;
            this.timeout = timeout;
            this.dataSegmentList = dataSegmentList;
        }

        @Override
        public void run() {
            for (Map.Entry<Integer, Long> entry : timeList.entrySet()) {
                long timeDiff = System.currentTimeMillis() - entry.getValue();
                if (entry.getKey() < GBNBase || timeDiff <= timeout) {
                    continue;
                }
                DataSegment fileDataSegment = dataSegmentList.get(entry.getKey() / MSS);
                try {
                    send(socket, fileDataSegment, "snd/RXT", "D");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                nTransmit++;
                nTimeout++;
            }
        }
    }
}