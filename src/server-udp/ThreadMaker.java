import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class ThreadMaker implements Runnable {

    //Making sure, when port is being changed, the port is not already in use.
    HashMap<Integer,Thread> portInUse;
    public static void main(String[] args) throws SocketException {
        ThreadMaker server = new ThreadMaker();
        server.run();
    }

    //Port 1350 will replace Port 69 as the base port for the TFTP implementation
    DatagramSocket socketServer = new DatagramSocket(1350);
    private final byte[] buf = new byte[256];

    public ThreadMaker() throws SocketException
    {
        portInUse = new HashMap<Integer, Thread>();
    }

    public void run() {
        System.out.print("==================SERVER=================\n");
        System.out.println("Server has started...");
        while (true)
        {
            try {
                //initialising the port for the new socket
                int port = 0;

                //Waiting for RRQ or WRQ from client
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socketServer.receive(packet);

                //When a new package arrives, there is a loop that checks if any of the threads in portInUse
                //is alive (not terminated) or not. If a port is not being used anymore, the port is yet again
                //available to the server
                for (Integer ongoingOperations : portInUse.keySet())
                {
                    if (!portInUse.get(ongoingOperations).isAlive())
                    {
                        portInUse.remove(port);
                    }
                }
                //Making sure port selected is not in use already
                boolean portFound = false;
                Random randomPort = new Random();

                //Makes sure the port is not already in use
                while(!portFound)
                {
                    port = randomPort.nextInt(49151) + 1024;

                    //port is used only if the port is not in the hashmap portInUse
                    if (!portInUse.containsKey(port))
                    {
                        //Creating thread and starting new Socket connection
                        Thread newUser = new Thread(new TFTP_UDP_Server(packet, new DatagramSocket(port)));
                        System.out.println("Packet Received from Port " + packet.getPort() + "\nTransferring Server Port from 4445 to " + port +"\n");
                        newUser.start();
                        portInUse.put(port,newUser);
                        portFound = true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
