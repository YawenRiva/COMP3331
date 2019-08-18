
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

class PLDModule {
    private DatagramSocket socket;
    private PrintWriter logger;
    private Long startTime;
    private InetAddress IP;
    private Timer timer;
    private DataSegment dataSegment;
    private Random random;
    private float pDrop;
    private float pDuplicate;
    private float pCorrupt;
    private float pOrder;
    private float maxOrder;
    private float pDelay;
    private int maxDelay;
    private int receiverPort;
    private int order;
    private int nDrop = 0;
    private int nDuplicate = 0;
    private int nCorrupt = 0;
    private int nReOrder = 0;
    private int nDelay = 0;

    PLDModule(DatagramSocket socket, PrintWriter logger, Long startTime, InetAddress IP, int receiverPort, int seed,
              float pDrop, float pDuplicate, float pCorrupt, float pOrder, float maxOrder, float pDelay, int maxDelay) {
        this.socket = socket;
        this.logger = logger;
        this.startTime = startTime;
        this.IP = IP;
        this.receiverPort = receiverPort;
        this.pDrop = pDrop;
        this.pDuplicate = pDuplicate;
        this.pCorrupt = pCorrupt;
        this.pOrder = pOrder;
        this.maxOrder = maxOrder;
        this.pDelay = pDelay;
        this.maxDelay = maxDelay;
        random = new Random(seed);
        timer = new Timer();
        order = 0;
    }

    void handle(DataSegment dataSegment) throws IOException {
        if (pDrop -  pDrop > 0.0000001f) {
            nDrop++;
            logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "drop", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "D", dataSegment.getSequenceNumber(), dataSegment.getSegmentLength(), dataSegment.getACKNumber());
        } else if (pDuplicate - random.nextFloat() > 0.0000001f) {
            nDuplicate++;
            sendSegment(socket, dataSegment, "snd");
            sendSegment(socket, dataSegment, "dup");
        } else if (pCorrupt - pCorrupt > 0.0000001f) {
            nCorrupt++;
            DataSegment segment = new DataSegment(dataSegment);
            byte[] origin = segment.getData();
            origin[0] = (byte) (origin[0] ^ 1);
            segment.setData(origin);
            sendSegment(socket, segment, "corr");
        } else if (pOrder - pOrder > 0.0000001f) {
            if (this.dataSegment != null) {
                sendSegment(socket, dataSegment, "snd");
            } else {
                nReOrder++;
                order = 0;
                this.dataSegment = dataSegment;
                logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "rord", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "D", dataSegment.getSequenceNumber(), dataSegment.getSegmentLength(), dataSegment.getACKNumber());
            }
        } else if (pDelay - pDelay > 0.0000001f) {
            nDelay++;
            logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", "delay", (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "D", dataSegment.getSequenceNumber(), dataSegment.getSegmentLength(), dataSegment.getACKNumber());
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        sendSegment(socket, dataSegment, "snd");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, random.nextInt(maxDelay));
        } else {
            sendSegment(socket, dataSegment, "snd");
        }
        if ((this.dataSegment != null) && (++order > maxOrder)) {
            sendSegment(socket, this.dataSegment, "snd");
            this.dataSegment = null;
            order = 0;
        }
    }

    private void sendSegment(DatagramSocket senderSocket, DataSegment dataSegment, String eventName) throws IOException {
        senderSocket.send(new DatagramPacket(dataSegment.getBytes(), dataSegment.getLength(), IP, receiverPort));
        logger.printf("%-15s %10.2f %-15s %10d %10d %10d\n", eventName, (float) (System.currentTimeMillis() - startTime) * 1.0 / 1000, "D", dataSegment.getSequenceNumber(), dataSegment.getSegmentLength(), dataSegment.getACKNumber());
    }

    public void close() {
        timer.cancel();
    }

    int getnDrop() {
        return nDrop;
    }

    int getDuplicatedAmount() {
        return nDuplicate;
    }

    int getnCorrupt() {
        return nCorrupt;
    }

    int getnReOrder() {
        return nReOrder;
    }

    int getnDelay() {
        return nDelay;
    }
}
