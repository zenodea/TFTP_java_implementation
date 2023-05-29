import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class TFTP_UDP_Server implements Runnable {
    private final DatagramPacket packet;
    private InetAddress clientAddress;
    private final DatagramSocket socket;
    private int clientPort;

    public TFTP_UDP_Server(DatagramPacket packet, DatagramSocket socket) throws SocketException
    {
        this.packet = packet;
        this.socket = socket;
    }

    public void run() {
        //get client address (In the case of this assignment, should be "localhost")
        clientAddress = packet.getAddress();

        //get client port
        clientPort = packet.getPort();

        //get filename from first packet received
        String fileName = findFileName(packet.getData());

        //If opcode is 01, then run RRQ()
        if(String.valueOf(packet.getData()[1]).equals("1"))
        {
            System.out.println("Selected function: Read Request\n");
            try {
                rrqPacket(fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //If opcode is 02, then run WRT()
        else if(String.valueOf(packet.getData()[1]).equals("2"))
        {
            System.out.println("Selected function: Write Request\n");
            try {
                wrtPacket(fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Closing Thread
        Thread.currentThread().interrupt();
    }

    /**
     *
     * @throws IOException
     */
    private void wrtPacket(String fileName) throws IOException {
        ackPacket(new byte[]{0,0});
        ArrayList<byte[]> fileReceived = new ArrayList<>();
        boolean running = true;
        byte[] buf = new byte[516];

        //Receiving the DATA packets
            while (running)
            {
                socket.setSoTimeout(10000);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, clientAddress, clientPort);
                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    break;
                }

                //Get blockNum (Got From https://stackoverflow.com/questions/4768933/read-two-bytes-into-an-integer will change)
                byte[] blockNumPCK = new byte[2];
                blockNumPCK[0] = packet.getData()[2];
                blockNumPCK[1] = packet.getData()[3];

                //Save the received data in the array list
                byte[] finalData = removeFirstFour(packet.getData());
                fileReceived.add(finalData);

                //checks if data is of length 512 bytes (516 including the first 4 bytes)
                if (packet.getLength() < 516)
                {
                    ackPacket(blockNumPCK);
                    running = false;
                }
                else
                {
                    ackPacket(blockNumPCK);
                }
            }

        //After receiving all the packets, the data is put together into a new file of the same name
        FileOutputStream fileCreation =  new FileOutputStream(fileName);
        for (byte[] bytes : fileReceived) fileCreation.write(bytes);
        fileCreation.close();

        System.out.println("File successfully transferred!\nClosing Connection with Port: " + packet.getPort());
    }

    /**
     * Method that allows the server to begin sending data packets to the client
     *
     * @throws IOException n
     * */
    private void rrqPacket(String fileName) throws IOException
    {

        //Get file into byte[]
        byte[] sendFile = new byte[0];

        //Check if file exists
        try
        {
            sendFile = Files.readAllBytes(Paths.get(fileName));
        }
        catch (IOException e)
        {
            errorPacket(new byte[]{0, 1},("filename: " + fileName + "   was not found.").getBytes(StandardCharsets.UTF_8));
        }

        //Opcode

        //Divide file byte[] into Arraylist of data of 512 byte
        ArrayList<byte[]> data_packets = div_buf(sendFile);

        //Begin sending DATA
        dataPacket(data_packets);

        System.out.println("File successfully transferred!\nClosing Connection with Port: " + packet.getPort());
    }


    /**
     * DATA
     * 
     * @param data_packets a
     * @throws IOException n
     */
    public void dataPacket(ArrayList<byte[]> data_packets) throws IOException {
        int blockNum = 1;
        for (byte[] data_packet : data_packets)
        {
            byte[] DATA_pck = new byte[4 + data_packet.length];

            //OPCODE
            DATA_pck[0] = 0;
            DATA_pck[1] = 3;

            //Block Number
            DATA_pck[2] = (byte) ((blockNum >>8) & 0xFF);
            DATA_pck[3] = (byte) (blockNum & 0xFF);
            blockNum++;

            //Copy the file byte[] into DATA_pck(which already includes opcode and Block#)
            System.arraycopy(data_packet, 0, DATA_pck, 4, data_packet.length);

            //Create Packet
            DatagramPacket packet = new DatagramPacket(DATA_pck,DATA_pck.length, clientAddress, clientPort);

            //Send Packet
            socket.send(packet);

            //Prepare to receive Acknowledgment

            //create empty array of 4 bytes for opcode and block# of Acknowledgment
            byte[] ack_pck = new byte[4];

            //Create empty packet and receive
            packet = new DatagramPacket(ack_pck,ack_pck.length);
            socket.receive(packet);
        }

    }
    /**
     * ACK package method (Used to construct an ACK package and send it)
     *
     * @throws IOException a
     */
    public void ackPacket(byte[] blockNum) throws IOException {
        //Create byte[] for ACK package
        byte[] ack_pck = new byte[4];

        //Prepare opcode
        ack_pck[0] = 0;
        ack_pck[1] = 4;

        //prepare block#
        ack_pck[2] = blockNum[0];
        ack_pck[3] = blockNum[1];

        //Create Packet and send
        DatagramPacket packet = new DatagramPacket(ack_pck,ack_pck.length,clientAddress,clientPort);
        socket.send(packet);
    }

    //ERROR Package (Opcode 05)
    /**
     * This method is used when an Error occurs. The packet will contain: The opcode of the packet, the errorCode and
     * the errorMessage.
     *
     * @throws IOException a
     */
    public void errorPacket(byte[] errorCode, byte[] errorMessage) throws IOException
    {
        //preparing Error Package elements
        byte[] opcode = {0,5};


        //Create errorPCK and arraycopy all of its elements
        byte[] errorPCK = new byte[opcode.length + errorCode.length + errorMessage.length];
        System.arraycopy(opcode,0,errorPCK,0,opcode.length);
        System.arraycopy(errorCode,0,errorPCK,opcode.length,errorCode.length);
        System.arraycopy(errorMessage,0,errorPCK,opcode.length+errorCode.length,errorMessage.length);

        //Create and send packet
        DatagramPacket packet = new DatagramPacket(errorPCK,errorPCK.length,clientAddress,clientPort);
        socket.send(packet);

        //Close Thread if error
        Thread.currentThread().interrupt();
    }

    /**
     * This method receives a byte[] (of the file), and returns an ArrayList of byte[] of length 512 (unless it's the
     * last one)
     *
     * @param data byte[] of the File
     *
     * @return returns an ArrayList of byte[] of length 512 (unless the last one is less than 512 bytes)
     */
    public ArrayList<byte[]> div_buf(byte[] data)  {
        int lengthByte = data.length;
        ArrayList<byte[]> dividedBuf = new ArrayList<>();
        byte[] newDataPCK;
        for(int i = 0; i < data.length/512 + 1; i++)
        {
            int newLength;
            if (lengthByte < 512)
            {
                newLength = lengthByte;
            }
            else
            {
                newLength = 512;
                lengthByte -= 512;
            }
            newDataPCK = new byte[newLength];
            System.arraycopy(data, (i * 512), newDataPCK, 0, newDataPCK.length);
            dividedBuf.add(newDataPCK);
        }
        return dividedBuf;
    }

    /**
     * findFileName is a function that returns the filename of a WTQ\RRQ package.
     *
     * @param data data of the WTQ/RRQ package
     *
     * @return filename
     */
    public String findFileName(byte[] data)
    {
        int i = 2;
        while (data[i] != '\0')
        {
           i++;
        }
        byte[] final_byte = new byte[i-2];
        System.arraycopy(data, 2, final_byte, 0, i-2);
        return new String(final_byte, StandardCharsets.UTF_8);
    }

    /**
     * This method simply removes the first 4 bytes in a byte array. This method is only used for DATA packages.
     *
     * @param data data of the DATA package
     *
     * @return just the data of the DATA package (not the opcode)
     */
    public byte[] removeFirstFour(byte[] data)
    {
        byte[] final_data = new byte[data.length-4];
        System.arraycopy(data,4,final_data,0,data.length-4);
        return final_data;
    }


}