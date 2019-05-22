import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * transfer segment
 */

public class DataSegment {
    private byte checkBit;
    private int ACK;
    private int SYN;
    private int FIN;
    private int sequenceNumber;
    private int ACKNumber;
    private int segmentLength;
    private byte[] data;


    private DataSegment() {
        data = null;
    }

    DataSegment(int sequence, int ACKNumber, int ACK, int SYN, int FIN, byte[] data) {
        this.sequenceNumber = sequence;
        this.ACKNumber = ACKNumber;
        this.ACK = ACK;
        this.SYN = SYN;
        this.FIN = FIN;
        this.data = data;
        this.segmentLength = data == null ? 0 : data.length;
        this.checkBit = initCheckBit();
    }

    DataSegment(int sequenceNumber, int ACKNumber, byte[] data) {
        this.ACK = 0;
        this.SYN = 0;
        this.FIN = 0;
        this.sequenceNumber = sequenceNumber;
        this.ACKNumber = ACKNumber;
        this.data = data;
        this.segmentLength = data.length;
        this.checkBit = initCheckBit();
    }

    DataSegment(DataSegment dataSegment) {
        this.sequenceNumber = dataSegment.getSequenceNumber();
        this.ACKNumber = dataSegment.getACKNumber();
        this.ACK = dataSegment.getACK();
        this.SYN = dataSegment.getSYN();
        this.FIN = dataSegment.getFIN();
        this.data = Arrays.copyOf(dataSegment.getData(), dataSegment.getSegmentLength());
        this.segmentLength = dataSegment.getSegmentLength();
        this.checkBit = dataSegment.getCheckBit();
    }

    protected byte[] getBytes() {
        return data == null ? mergeBytes(getBytesInt(sequenceNumber), getBytesInt(ACKNumber), new byte[]{(byte) ((ACK << 2) + (SYN << 1) + FIN)}, new byte[]{checkBit}, getBytesInt(segmentLength)) : mergeBytes(getBytesInt(sequenceNumber), getBytesInt(ACKNumber), new byte[]{(byte) ((ACK << 2) + (SYN << 1) + FIN)}, new byte[]{checkBit}, getBytesInt(segmentLength), data);
    }

    private byte[] getBytesPure() {
        return data == null ? mergeBytes(getBytesInt(sequenceNumber), getBytesInt(ACKNumber), new byte[]{(byte) ((ACK << 2) + (SYN << 1) + FIN)}, getBytesInt(segmentLength)) : mergeBytes(getBytesInt(sequenceNumber), getBytesInt(ACKNumber), new byte[]{(byte) ((ACK << 2) + (SYN << 1) + FIN)}, getBytesInt(segmentLength), data);
    }

    private static byte[] getBytesInt(int data) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data).array();
    }

    private static int getInt(byte[] bytes) {
        return ByteBuffer.allocate(bytes.length).order(ByteOrder.BIG_ENDIAN).put(bytes).getInt(0);
    }

    private static byte[] mergeBytes(byte[]... arrays) {
        int ByteLength = Arrays.stream(arrays).filter(Objects::nonNull).mapToInt(array -> array.length).sum();
        byte[] bytes = new byte[ByteLength];
        int countLength = 0;
        for (byte[] byteData : arrays)
            if (byteData != null) {
                System.arraycopy(byteData, 0, bytes, countLength, byteData.length);
                countLength += byteData.length;
            }
        return bytes;
    }

    static DataSegment getFromBytes(byte[] segment) {
        if (segment.length >= 14) {
            DataSegment dataSegment = new DataSegment();
            byte signal = segment[8];
            dataSegment.setData(getInt(Arrays.copyOfRange(segment, 10, 14)) != 0 ? Arrays.copyOfRange(segment, 14, 14 + getInt(Arrays.copyOfRange(segment, 10, 14))) : null);
            dataSegment.setSequenceNumber(getInt(Arrays.copyOfRange(segment, 0, 4)));
            dataSegment.setACKNumber(getInt(Arrays.copyOfRange(segment, 4, 8)));
            dataSegment.setACK(signal / 4);
            signal %= 4;
            dataSegment.setSYN(signal / 2);
            signal %= 2;
            dataSegment.setCheckBit(segment[9]);
            dataSegment.setFIN(signal);
            dataSegment.setSegmentLength(getInt(Arrays.copyOfRange(segment, 10, 14)));
            return dataSegment;
        } else {
            return null;
        }
    }

    boolean isChecked() {
        return initCheckBit() == checkBit;
    }

    int getLength() {
        return this.getBytes().length;
    }

    int getSequenceNumber() {
        return sequenceNumber;
    }

    private void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    int getACKNumber() {
        return ACKNumber;
    }

    private void setACKNumber(int ACKNumber) {
        this.ACKNumber = ACKNumber;
    }

    public int getACK() {
        return ACK;
    }

    public void setACK(int ACK) {
        this.ACK = ACK;
    }

    public int getSYN() {
        return SYN;
    }

    public void setSYN(int SYN) {
        this.SYN = SYN;
    }

    public int getFIN() {
        return FIN;
    }

    public void setFIN(int FIN) {
        this.FIN = FIN;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    int getSegmentLength() {
        return segmentLength;
    }

    private void setSegmentLength(int segmentLength) {
        this.segmentLength = segmentLength;
    }

    private byte getCheckBit() {
        return checkBit;
    }

    private void setCheckBit(byte checkBit) {
        this.checkBit = checkBit;
    }

    private byte initCheckBit() {
        long sum = 0;
        for (byte byteData : getBytesPure()) {
            sum += (long) byteData < 0 ? (long) byteData + 256 : (long) byteData;
        }
        return (byte) (sum & 255);
    }
}
