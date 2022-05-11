import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class MulticastReceiver {
    public static void main(String[] args) {
        Listener l = new Listener(args);
        l.listen();
    }
}

class Listener {
    private static final int MINLEN = 40;
    private final String[] args;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final boolean running;
    private Date previous;
    private MessageDigest md5;
    private int ownId;

    public Listener(String[] args) {
        this.args = args;
        running = true;
        previous = new Date();
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return (sb.toString());
    }

    public void listen() {
        Date d = new Date();

        TeePrintStream ts;
        try {
            ts = new TeePrintStream(System.out, "udp-receiver-test-log-" + d.getTime() + ".txt", true);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        }


        System.setOut(ts);

        if (args.length < 3) {
            System.out.println("Parameters required: address, port, ownid");
            return;
        }


        System.out.println("Program parameters: address, port, ownid");
        for (String arg : args) {
            System.out.println(arg);
        }

        System.out.println("Starting listener on port " + args[0] + " at " + d.getTime());
        MulticastSocket socket = getMulticastSocket();
        if (socket == null) {
            return;
        }

        byte[] buffer = new byte[4096];
        DatagramPacket dg = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(dg);
            } catch (IOException e) {
                e.printStackTrace();
            }

            byte[] received = Arrays.copyOfRange(dg.getData(), 0, dg.getLength());
            dg.setLength(buffer.length);


            if (received.length >= 40) {

                printBytes(received);
            } else {
                System.out.println(sdf.format(new Date()) + " Datagram too short: " + bytesToHex(received));
            }

        }

        ts.close();
    }

    private MulticastSocket getMulticastSocket() {
        int port = Integer.parseInt(args[1]);
        ownId = Integer.parseInt(args[2]);

        InetAddress address;
        MulticastSocket socket;

        try {
            address = InetAddress.getByName(args[0]);
            socket = new MulticastSocket(port);
            socket.joinGroup(address);

        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return socket;
    }

    private void printBytes(byte[] msg) {
        Date now = new Date();
        long timeSinceLastPacket = now.getTime() - previous.getTime();

        if (msg == null) {
            System.out.println("Message null or too short");
        } else if (msg.length < MINLEN) {
            System.out.println(sdf.format(new Date()) + " Datagram too short: " + bytesToHex(msg));
        } else {
            byte[] rec_hash_bytes = Arrays.copyOfRange(msg, msg.length - 16, msg.length);
            // 16..1 in the tail
            for (byte i = 16; i > 0; --i) {
                msg[msg.length - i] = i;
            }

            byte[] rec_calc_hash = md5.digest(msg);

            boolean equals = true;
            for (int i = 0; i < rec_hash_bytes.length; ++i) {
                if (rec_hash_bytes[i] != rec_calc_hash[i]) {
                    equals = false;
                    System.out.println(sdf.format(new Date()) + " hash fail: " + bytesToHex(msg));
                    break;
                }
            }
            if (equals) {
                ByteBuffer receivedBuffer = ByteBuffer.wrap(msg);

                int rec_id = receivedBuffer.get();
                int rec_segno = receivedBuffer.getInt();
                long rec_time = receivedBuffer.getLong();
                int rec_len = receivedBuffer.getInt();


                if (rec_len != msg.length) {
                    //System.out.println(sdf.format(new Date()) +" length fail: " + bytesToHex(msg));
                } else if (ownId != rec_id) {
                    System.out.println(sdf.format(now) + " RECV From: " + rec_id + ", No: " + rec_segno + ", Len: " + msg.length + ", Age: " + String.format("%06d", (now.getTime() - rec_time)) + " TimeSince: " + String.format("%06d", timeSinceLastPacket) + ",: ");


                    previous = now;   // only update on successful messages
                } else {
                    System.out.println(sdf.format(now) + " Own message: " + bytesToHex(msg));
                }
            }
        }
    }
}



class UDPSocket {
    private final DatagramPacket packet;
    private final byte[] buffer;
    DatagramSocket socket;
    int port;

    public UDPSocket(int port, int bufferSize) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        buffer = new byte[bufferSize];
        packet = new DatagramPacket(buffer, buffer.length);
        this.port = port;
    }

    public byte[] Receive() {
        // System.out.println("Waiting to receive on socket on port: " + port);
        try {
            socket.receive(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] bytes = Arrays.copyOfRange(buffer, 0, packet.getLength());

        // String msg = new String(buffer, 0, packet.getLength());
        //System.out.println("Received " + packet.getLength() + " bytes from " + packet.getAddress().getHostName());

        packet.setLength(buffer.length);
        return bytes;
    }
}

class TeePrintStream extends PrintStream {
    protected PrintStream parent;

    protected String fileName;

    /**
     * Construct a TeePrintStream given an existing PrintStream, an opened OutputStream, and a boolean to control auto-flush. This is the main constructor, to which others delegate via "this".
     */
    public TeePrintStream(PrintStream orig, OutputStream os, boolean flush) throws IOException {
        super(os, true);
        fileName = "(opened Stream)";
        parent = orig;
    }

    /**
     * Construct a TeePrintStream given an existing PrintStream and an opened OutputStream.
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
    public TeePrintStream(PrintStream orig, String fn, boolean flush) throws IOException {
        this(orig, new FileOutputStream(fn), flush);
    }

    /**
     * A simple test case.
     */
    public static void main(String[] args) throws IOException {
        TeePrintStream ts = new TeePrintStream(System.err, "err.log", true);
        System.setErr(ts);
        System.err.println("An imitation error message");
        ts.close();
    }

    /**
     * Return true if either stream has an error.
     */
    public boolean checkError() {
        return parent.checkError() || super.checkError();
    }

    /**
     * override write(). This is the actual "tee" operation.
     */
    public void write(int x) {
        parent.write(x); // "write once;
        super.write(x); // write somewhere else."
    }

    /**
     * override write(). This is the actual "tee" operation.
     */
    public void write(byte[] x, int o, int l) {
        parent.write(x, o, l); // "write once;
        super.write(x, o, l); // write somewhere else."
    }

    /**
     * Close both streams.
     */
    public void close() {
        parent.close();
        super.close();
    }

    /**
     * Flush both streams.
     */
    public void flush() {
        parent.flush();
        super.flush();
    }
}