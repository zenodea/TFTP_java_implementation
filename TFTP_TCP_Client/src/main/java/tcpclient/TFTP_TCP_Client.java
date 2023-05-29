package tcpclient;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;

public class TFTP_TCP_Client {
    Socket echoSocket;
    DataOutputStream out_packet;
    DataInputStream in_file;
    int port;

    public static void main(String[] args)
    {
        TFTP_TCP_Client client = new TFTP_TCP_Client(3425);
        client.startingSetUp();
    }

    public TFTP_TCP_Client(int port)
    {
        this.port = port;
    }

    /**
     *
     */
    public void startingSetUp()
    {
        System.out.println("======================Client======================");
        System.out.println("Write 1 to start a read request with the server");
        System.out.println("Write 2 to start a write request with the server");
        System.out.print("Answer = ");
        // Assign the first argument to the hostName String object
        String address = "127.0.0.1";

        try {
            //Create Socket
            echoSocket = new Socket(address, 10000);

            //Setting up the Data input and output stream
            out_packet = new DataOutputStream(echoSocket.getOutputStream());
            in_file = new DataInputStream(echoSocket.getInputStream());

            //Scanner for user input
            Scanner stdIn = new Scanner(System.in);
            String answer = stdIn.nextLine();

            //Starting Read Request
            if (Objects.equals(answer, "1"))
            {
                System.out.print("File Name: ");
                String filename = stdIn.nextLine();
                rrqPACKET(filename);
            }

            //Starting Write Request
            else if (Objects.equals(answer, "2"))
            {
                System.out.print("File Name: ");
                String filename = stdIn.nextLine();
                wrqPACKET(filename);
            }

            //Trying again
            else
            {
                System.out.println("Option does not exist!\nTry Again!");
                startingSetUp();
            }
            echoSocket.close();

        }
        //Catch for UnknownHostException
        catch (UnknownHostException e)
        {
            System.err.println(address + " not a Server");
            System.exit(1);
        }
        catch (IOException e)
        {
            System.err.println("\nUnable to connect to Server");
            System.exit(1);
        }
    }

    /**
     *
     * @param fileName filename of the file wanted to be written on the server
     * @throws IOException filename and socket exception
     */
    public void wrqPACKET(String fileName) throws IOException {
        //First Connection with Server
        byte[] firstPacket = new byte[2+1+1+"octet".getBytes(StandardCharsets.UTF_8).length+fileName.getBytes(StandardCharsets.UTF_8).length];

        //Inserting opcode onto the packet
        firstPacket[0] = 0;
        firstPacket[1] = 2;

        //Insert filename into packet
        System.arraycopy(fileName.getBytes(StandardCharsets.UTF_8), 0, firstPacket, 2, fileName.getBytes(StandardCharsets.UTF_8).length);

        //Insert 0 byte
        firstPacket[fileName.getBytes(StandardCharsets.UTF_8).length+2] = 0;

        //Insert mode
        System.arraycopy("octet".getBytes(StandardCharsets.UTF_8), 0, firstPacket, fileName.getBytes(StandardCharsets.UTF_8).length+2+1,"octet".getBytes(StandardCharsets.UTF_8).length);

        //Insert 0 byte
        firstPacket[firstPacket.length-1] = 0;

        //Send packet to the server
        out_packet.writeInt(firstPacket.length);
        out_packet.write(firstPacket);

        dataPACKET(fileName);
    }

    /**
     *
     * @param filename filename of the file wanted to be written on the server
     * @throws IOException exceptions for the socket
     */
    public void dataPACKET(String filename) throws IOException
    {
        //Creating a DataOutputStream to send data to the server (in byte[])
        DataOutputStream socketOutput = new DataOutputStream(echoSocket.getOutputStream());

        //Transform the desired file to send into a byte array
        byte[] file_byte = new byte[0];
        try {
            file_byte = Files.readAllBytes(Paths.get(filename));
        } catch (IOException e) {
            System.out.println("Filename does not exist!\nEnding connection!");
            System.exit(0);
        }

        ArrayList<byte[]> file_div = dividedArray512(file_byte);

        float count = 0;
        float finalCount = file_byte.length;
        System.out.println("\n===================Progress Bar===================");

        //For loop used to send all the data packets to the server
        for (byte[] bytes : file_div)
        {
            count += bytes.length;
            System.out.print("\r             " + (count/1000000) + "/" + (finalCount/1000000) + " MB");
            socketOutput.write(bytes,0,bytes.length);
        }

        System.out.println("\n\nWrite Request completed!\nShutting down connection...");
        socketOutput.flush();
        echoSocket.close();
    }

    /**
     *
     * @param fileName filename of the file wanted to be retrieved from the server
     * @throws IOException exceptions for sockets
     */
    private void rrqPACKET(String fileName) throws IOException {
        //First Connection with Server
        byte[] firstPacket = new byte[2+1+1+"octet".getBytes(StandardCharsets.UTF_8).length+fileName.getBytes(StandardCharsets.UTF_8).length];
        firstPacket[0] = 0;
        firstPacket[1] = 1;

        //Insert filename into packet
        System.arraycopy(fileName.getBytes(StandardCharsets.UTF_8), 0, firstPacket, 2, fileName.getBytes(StandardCharsets.UTF_8).length);

        //Insert 0 byte
        firstPacket[fileName.getBytes(StandardCharsets.UTF_8).length+2] = 0;

        //Insert mode
        System.arraycopy("octet".getBytes(StandardCharsets.UTF_8), 0, firstPacket, fileName.getBytes(StandardCharsets.UTF_8).length+2+1,"octet".getBytes(StandardCharsets.UTF_8).length);
        firstPacket[firstPacket.length-1] = 0;

        //Send the size of the packet to the server
        out_packet.writeInt(firstPacket.length);
        //Send the first packet to the Server
        out_packet.write(firstPacket);

        //Creating a new buffer
        byte[] buf = new byte[514];
        FileOutputStream fileCreation =  new FileOutputStream(fileName);

        float count = 0;
        System.out.println("\n==================Progress Bar==================");

        //While read inputstream is more or equal 0 (AKA not empty)
        while(in_file.read(buf) >= 0)
        {
            if (isError(buf))
            {
                System.out.println("File does not exist on Server!\nShutting Down...");
                System.exit(0);
            }

            buf = removeFirstTwo(buf);

            //Writing read input stream into the newly created file
            fileCreation.write(buf);

            //adding to count
            count += buf.length;
            //updating progress bar with new count
            System.out.print("\r             " + (count/1000000) + "/??? MB");

            //Clearing buffer
            buf = new byte[514];
        }
        System.out.print("\r             " + (count/1000000) + "/" + (count/1000000) + " MB");
        fileCreation.close();
        System.out.println("\n\nRead Request completed!\nShutting down...");
        echoSocket.close();
    }

    /**
     * This method is used to divide the byte[] of the file into smaller byte[] of size 512 byte each
     *
     * @return 2d byte array of size [file_Byte/2 + 1][512]
     */
    public ArrayList<byte[]> dividedArray512(byte[] buf)  {
        int lengthFileByte = buf.length;
        ArrayList<byte[]> dividedBuf = new ArrayList<>();
        byte[] newDataPCK;
        for(int i = 0; i < buf.length/512 + 1; i++)
        {
            int newLength;
            if (lengthFileByte < 512)
            {
                newLength = lengthFileByte;
            }
            else
            {
                newLength = 512;
                lengthFileByte -= 512;
            }
            newDataPCK = new byte[newLength+2];
            newDataPCK[0] = 0;
            newDataPCK[1] = 3;
            System.arraycopy(buf, (i * 512), newDataPCK, 2, newDataPCK.length-2);
            dividedBuf.add(newDataPCK);
        }
        return dividedBuf;
    }
    /**
     * Method isError() checks if the packet receive has opcode 05
     *
     * @param packet packet received
     * @return True if opcode is 5, False if not
     */
    public boolean isError(byte[] packet)
    {
        String data_String = new String(packet,StandardCharsets.UTF_8);
        if (packet[1] == 5)
        {
            System.out.println(data_String.replaceAll("\0", "").substring(2));
            return true;
        }
        else return false;
    }

    /**
     * removeFirstTwo(), as the name suggests, removes the first two element of a byte[] (AKA opcode). Useful to remove bytes before
     * creating the new document.
     *
     * @param data Used for data packages
     *
     * @return returns data without the first two bytes (opcode and block#)
     */
    public byte[] removeFirstTwo(byte[] data)
    {
        byte[] final_data = new byte[data.length-2];
        System.arraycopy(data,2,final_data,0,data.length-2);
        return final_data;
    }
}

