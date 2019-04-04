package edu.scu.Utils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

import static edu.scu.Utils.Constants.sizeOfFingerTable;

/**
 * @author Raghav Bhandari
 * @author Krishna Kandhani
 * @author Abhiman Kolte
 * @author Dhruv Mevada
 */

public class Util
{
    private static HashMap<Integer, Long> powerOfTwo = null;

    public Util()
    {
        //initialize power of two table
        powerOfTwo = new HashMap<Integer, Long>();
        long base = 1;
        for (int i = 0; i < sizeOfFingerTable +1; i++) {
            powerOfTwo.put(i, base);
            base *= 2;
        }
    }

    /**
     * Compute a socket address' sizeOfFingerTable bit identifier
     *
     * @param addr: socket address
     * @return sizeOfFingerTable-bit identifier in long type
     */
    public static long hashSocketAddress(InetSocketAddress addr) {
        //int i = addr.hashCode();
        //return hashHashCode(i);
        Logger.log(addr.getHostString());
        String lastTriplet = addr.getHostString().substring(addr.getHostString().lastIndexOf(".")+1);
        Logger.log(lastTriplet);
        return Integer.parseInt(lastTriplet) % 32;
    }

    /**
     * Compute a string's sizeOfFingerTable bit identifier
     *
     * @param s: string
     * @return sizeOfFingerTable-bit identifier in long type
     */
    public static long hashString(String s) {
        int i = s.hashCode();
        return hashHashCode(i);
    }

    /**
     * Compute a sizeOfFingerTable bit integer's identifier
     *
     * @param i: integer
     * @return sizeOfFingerTable-bit identifier in long type
     */
    private static long hashHashCode(int i) {
//
//        //sizeOfFingerTable bit regular hash code -> byte[4]
//        byte[] hashbytes = new byte[4];
//        hashbytes[0] = (byte) (i >> 24);
//        hashbytes[1] = (byte) (i >> 16);
//        hashbytes[2] = (byte) (i >> 8);
//        hashbytes[3] = (byte) (i /*>> 0*/);
//
//        // try to create SHA1 digest
//        MessageDigest md = null;
//        try {
//            md = MessageDigest.getInstance("SHA-1");
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        }
//
//        // successfully created SHA1 digest
//        // try to convert byte[4]
//        // -> SHA1 result byte[]
//        // -> compressed result byte[4]
//        // -> compressed result in long type
//        if (md != null) {
//            md.reset();
//            md.update(hashbytes);
//            byte[] result = md.digest();
//
//            byte[] compressed = new byte[4];
//            for (int j = 0; j < 4; j++) {
//                byte temp = result[j];
//                for (int k = 1; k < sizeOfFingerTable; k++) {
//                    temp = (byte) (temp ^ result[j + k]);
//                }
//                compressed[j] = temp;
//            }
//
//            long ret = (compressed[0] & 0xFF) << 24 | (compressed[1] & 0xFF) << 16 | (compressed[2] & 0xFF) << 8 | (compressed[3] & 0xFF);
//            ret = ret & (long) 0xFFFFFFFFl;
//            return ret;
//        }


        return 0;
    }


    public static long computeRelativeId(long universal, long local) {
        long ret = universal - local;
        if (ret < 0) {
            ret += powerOfTwo.get(sizeOfFingerTable);
        }
        return ret;
    }



    public static long ithStart(long nodeid, int i) {
        return (nodeid + powerOfTwo.get(i - 1)) % powerOfTwo.get(sizeOfFingerTable);
    }

    /**
     * Get power of 2
     *
     * @param k
     * @return 2^k
     */
    public static long getPowerOfTwo(int k) {
        return powerOfTwo.get(k);
    }

    /**
     * Generate requested address by sending request to server
     *
     * @param server
     * @param req:   request
     * @return generated socket address,
     * might be null if
     * (1) invalid input
     * (2) response is null (typically cannot send request)
     * (3) fail to create address from reponse
     */
    public static InetSocketAddress requestAddress(InetSocketAddress server, String req)
    {

        // invalid input, return null
        if (server == null || req == null)
        {
            return null;
        }

        // send request to server
        String response = sendRequest(server, req);

        // if response is null, return null
        if (response == null) {
            return null;
        }

        // or server cannot find anything, return server itself
        else if (response.startsWith("NOTHING"))
            return server;

            // server find something,
            // using response to create, might fail then and return null
        else {
            InetSocketAddress ret = Util.createSocketAddress(response.split("_")[1]);
            return ret;
        }
    }

    public static String sendRequest(InetSocketAddress server, String req) {

        // invalid input
        if (server == null || req == null)
            return null;

        Socket talkSocket = null;

        // try to open talkSocket, output request to this socket
        // return null if fail to do so
        try {
            talkSocket = new Socket(server.getHostString(), server.getPort());
            PrintStream output = new PrintStream(talkSocket.getOutputStream());
            output.println(req);
        } catch (IOException e) {
            Logger.log("\nCannot send request to "+server.toString()+"\nRequest is: "+req+"\n");
            return null;
        }

        // sleep for a short time, waiting for response
        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // get input stream, try to read something from it
        InputStream input = null;
        try {
            input = talkSocket.getInputStream();
        } catch (IOException e) {
            Logger.log("Cannot get input stream from " + server.toString() + "\nRequest is: " + req + "\n");
        }
        String response = Util.inputStreamToString(input);

        // try to close socket
        try {
            talkSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot close socket", e);
        }
        return response;
    }

    /**
     * Creates an InetSocketAddress using ip address and port number.
     *
     * @return created InetSocketAddress object
     */
    public static InetSocketAddress createSocketAddress(String addr)
    {
        // input null, return null
        if (addr == null) {
            return null;
        }

        // split input into ip string and port string
        String[] splitted = addr.split(":");

        // can split string
        if (splitted.length >= 2) {

            //get and pre-process ip address string
            String ip = splitted[0];
            if (ip.startsWith("/")) {
                ip = ip.substring(1);
            }
            //Logger.log(ip);
            //parse ip address, if fail, return null
//            InetAddress m_ip = null;
//            try {
//               // m_ip = InetAddress.getByName(ip);
//            } catch (UnknownHostException e) {
//                e.printStackTrace();
//                System.out.println("Cannot create ip address: "+ip);
//                return null;
//            }

            // parse port number
            String port = splitted[1];
            int m_port = Integer.parseInt(port);

            // combine ip addr and port in socket address
            return new InetSocketAddress(ip, m_port);
        }

        // cannot split string
        else {
            return null;
        }
    }

    /**
     * Read one line from input stream
     *
     * @param in: input steam
     * @return line, might be null if:
     * (1) invalid input
     * (2) cannot read from input stream
     */
    public static String inputStreamToString(InputStream in) {

        // invalid input
        if (in == null) {
            return null;
        }

        // try to read line from input stream
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line = null;
        try {
            line = reader.readLine();
        } catch (IOException e) {
            Logger.log("Cannot read line from input stream.");
            return null;
        }

        return line;
    }

}