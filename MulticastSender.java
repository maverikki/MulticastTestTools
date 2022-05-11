import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class MulticastSender {
    private static final int MIN_LENGTH = 40;
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("Required parameters: ID, address, port, amount, delayMs, length, ttl");
            return;
        }

        int argsIndex = 0;
        int id = Integer.parseInt(args[argsIndex++]);
        InetAddress address = null;
        try {
            address = InetAddress.getByName(args[argsIndex++]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        int port = Integer.parseInt(args[argsIndex++]);
        int amount = Integer.parseInt(args[argsIndex++]);
        int delayMs = Integer.parseInt(args[argsIndex++]);
        int length = Integer.parseInt(args[argsIndex++]);
        int ttl = Integer.parseInt(args[argsIndex]);

        if (length < MIN_LENGTH) {
            System.out.println("Length should be at least " + MIN_LENGTH);
            return;
        }

        if (id < 0 || id > 9) {
            System.out.println("Id should be 0-9");
            return;
        }

        Date d = new Date();

        TeePrintStream ts;
        try {
            ts = new TeePrintStream(System.out, "udp-sender-test-log-" + d.getTime() + ".txt", true);
            System.setOut(ts);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        System.out.println("Program parameters: ID, address, port, amount, delayMs, length, ttl");
        for (String arg : args) {
            System.out.println(arg);
        }

        if (amount == 0) {
            amount = Integer.MAX_VALUE;
        }

        Random r = new Random(new Date().getTime());

        MulticastSocket socket;
        try {
            socket = new MulticastSocket();
            socket.setTimeToLive(ttl);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        DatagramPacket dg;

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        }

        int packetNumber = 0;
        while (packetNumber < amount) {

            byte[] bytes = new byte[length];
            r.nextBytes(bytes);

            // 16..1 in the tail
            for (byte i = 16; i > 0; --i) {
                bytes[bytes.length - i] = i;
            }

            ByteBuffer bb = ByteBuffer.wrap(bytes);
            bb.rewind();
            // 0: ID
            bb.put((byte) id);

            // 1-4: seg no
            bb.putInt(packetNumber);

            // 5-12: date
            Date sendDate = new Date();
            long sendTime = sendDate.getTime();
            bb.putLong(sendTime);

            // 13-16: length
            bb.putInt(length);

            // 17-20: 1234
            bb.put((byte) 1);
            bb.put((byte) 2);
            bb.put((byte) 3);
            bb.put((byte) 4);

            // calc hash to len -16
            byte[] hash = md5.digest(bb.array());

            // copy hash to len-16, len
            bb.position(length - 16);
            bb.put(hash, 0, 16);

            bb.rewind();

            System.out.println(sdf.format(d) + " From: " + id + " segno: " + packetNumber + " Len: " + length + " Data: " + bytesToHex(bb.array()));
            dg = new DatagramPacket(bb.array(), length, address, port);

            try {
                socket.send(dg);
                Thread.sleep(delayMs);

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ++packetNumber;
        }
        ts.close();
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return (sb.toString());
    }
}
class TeePrintStream extends PrintStream {
    protected PrintStream parent;

    protected String fileName;

    /** A simple test case. */
    public static void main(String[] args) throws IOException {
        TeePrintStream ts = new TeePrintStream(System.err, "err.log", true);
        System.setErr(ts);
        System.err.println("An imitation error message");
        ts.close();
    }

    /**
     * Construct a TeePrintStream given an existing PrintStream, an opened
     * OutputStream, and a boolean to control auto-flush. This is the main
     * constructor, to which others delegate via "this".
     */
    public TeePrintStream(PrintStream orig, OutputStream os, boolean flush)
            throws IOException {
        super(os, true);
        fileName = "(opened Stream)";
        parent = orig;
    }

    /**
     * Construct a TeePrintStream given an existing PrintStream and an opened
     * OutputStream.
     */
    public TeePrintStream(PrintStream orig, OutputStream os) throws IOException {
        this(orig, os, true);
    }

    /*
     * Construct a TeePrintStream given an existing Stream and a filename.
     */
    public TeePrintStream(PrintStream os, String fn) throws IOException {
        this(os, fn, true);
    }

    /*
     * Construct a TeePrintStream given an existing Stream, a filename, and a
     * boolean to control the flush operation.
     */
    public TeePrintStream(PrintStream orig, String fn, boolean flush)
            throws IOException {
        this(orig, new FileOutputStream(fn), flush);
    }

    /** Return true if either stream has an error. */
    public boolean checkError() {
        return parent.checkError() || super.checkError();
    }

    /** override write(). This is the actual "tee" operation. */
    public void write(int x) {
        parent.write(x); // "write once;
        super.write(x); // write somewhere else."
    }

    /** override write(). This is the actual "tee" operation. */
    public void write(byte[] x, int o, int l) {
        parent.write(x, o, l); // "write once;
        super.write(x, o, l); // write somewhere else."
    }

    /** Close both streams. */
    public void close() {
        parent.close();
        super.close();
    }

    /** Flush both streams. */
    public void flush() {
        parent.flush();
        super.flush();
    }
}
