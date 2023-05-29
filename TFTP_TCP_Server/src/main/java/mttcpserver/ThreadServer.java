package mttcpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ThreadServer {

    public static void main(String[] args) throws IOException {

        // the port number that the server socket will be bound to
        int portNumber = 10000;

        // The TCP ServerSocket object (master socket)
        ServerSocket masterSocket;
        Socket slaveSocket;

        masterSocket = new ServerSocket(portNumber);


        System.out.println("Server Started...");
        while (true) {
            slaveSocket = masterSocket.accept();
            System.out.println("Accepted TCP connection from: " + slaveSocket.getInetAddress() + ", " + slaveSocket.getPort() + "...");
            System.out.println("Instantiating and starting new MTTCPServerThread object to handle the connection...");
            new TFTP_TCP_Server(slaveSocket).start();
        }
    }
}
