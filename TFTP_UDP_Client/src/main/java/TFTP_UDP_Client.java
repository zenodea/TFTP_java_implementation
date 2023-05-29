import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TFTP_UDP_Client {
    private DatagramSocket socket;
    private final InetAddress address;
    private int serverPort;
    DatagramPacket packet;
    private int blockNum = 1;

    public static void main(String[] args) throws IOException{
        System.out.println("==================CLIENT=================\n");

        //Creating new object
        TFTP_UDP_Client client = new TFTP_UDP_Client();

        //Scanner used to get user input (whether reading or writing a file)
        Scanner userInput = new Scanner(System.in);
        System.out.print("Choose 1 to Read a file\n\nChoose 2 to Write the file\n\nAnswer: ");
        String answer = userInput.nextLine();
        if(Objects.equals(answer, "1"))
        {
            System.out.print("\nWhat is the name of the file to read(Please Include Extension): ");
            userInput = new Scanner(System.in);
            String fileName = userInput.nextLine();
            client.rrqPacket(fileName);
        }
        else if(Objects.equals(answer,"2"))
        {
            System.out.print("\nWhat is the name of the file to write on server(Please Include Extension): ");
            Scanner filenameScanner = new Scanner(System.in);
            String fileName = filenameScanner.nextLine();
            client.wrqPacket(fileName);
        }
        System.out.println("Operation Finished!\nTerminating Connection...");
        client.close();
    }

    /**
     * Initializer of the TFTP UDP CLIENT
     *
     * @throws UnknownHostException Needed for address = InetAddress.getByName("localhost");
     */
    public TFTP_UDP_Client() throws UnknownHostException {
        Random randomPort = new Random();

        //Generating random int (as stated in the rfc)
        boolean socketCreated = false;

        while(!socketCreated)
        {
            //New Socket with int port and localhost used as server address
            try
            {
                int port = randomPort.nextInt(49151) + 1024;
                socket = new DatagramSocket(port);
                socketCreated = true;
            }
            catch (SocketException e)
            {
                System.err.print("Port already in use, trying again!");
            }
        }
        address = InetAddress.getByName("localhost");
    }

    /**
     * initialConnectionProtocol() is used to send the first packet to the server. Since both rrq and wrq use the same
     * structure, except for a different opcode, the method can be freely used for both requests.
     *
     * @param opcode_String the opcode of the request, in String format
     * @param fileName The name of the file wanted to read\write
     *
     * @throws IOException Exception used for DatagramPacket packet and socket.send(packet)
     */
    private void initialConnectionProtocol(byte[] opcode_String, String fileName) throws IOException {
        ArrayList<byte[]> finalData = new ArrayList<>();

        //Get total packet length
        int packetLength = fileName.getBytes(StandardCharsets.UTF_8).length +
                           "octet".getBytes(StandardCharsets.UTF_8).length  +
                           4;

        //Adding Elements to array List
        finalData.add(opcode_String);
        finalData.add(fileName.getBytes(StandardCharsets.UTF_8));
        finalData.add(new byte[]{0});
        finalData.add("octet".getBytes(StandardCharsets.UTF_8));
        finalData.add(new byte[]{0});

        //Creating final byte[] for package with all its elements
        byte[] final_pck = putTogetherByteArr(finalData,packetLength);

        //Creating and Sending packet to server
        DatagramPacket packet = new DatagramPacket(final_pck,final_pck.length,address,1350);
        socket.send(packet);
    }

    //Read Request

    /**
     * Method that prepares the client for a read request transmission.
     *
     * @param fileName Name of the file wanted to read from the server.
     * @throws IOException Exceptions for Files and Socket Exception
     */
    private void rrqPacket(String fileName) throws IOException {
        System.out.println("\n============Progress Bar============");

        //Using method initialConnectionProtocol to send the read request packet to the server
        initialConnectionProtocol(new byte[] {0,1},fileName);

        //Getting data
        ArrayList<byte[]> file_received = new ArrayList<>();
        byte[] buf = new byte[516];
        int count = 1;
        boolean running = true;
        while(running)
        {
            //Creating new DatagramPacket
            DatagramPacket packet = new DatagramPacket(buf,buf.length);

            //timeoutTimer begins for socket to receive packet
            try {
                socket.receive(packet);

            } catch (IOException e) {
                break;
            }

            //Check if packet is error
            if(isError(packet))
            {
                System.exit(0);
            }

            //Remove the first four bytes
            byte[] received_pck = removeFirstFour(packet.getData());

            //add data bytes to byte Array
            file_received.add(received_pck);

            //Send ACK
            ackPACKET(packet);

            //checks if data is of length 512 bytes
            if (packet.getLength() < 516)
            {
                System.out.print("\r             " + (count/1000000) + "/" + (count/1000000) + " MB");
                running = false;
            }
            else
            {
                System.out.print("\r             " + (count/1000000) + "/??? MB");
                count += packet.getData().length;
            }
        }

        //Creating File from received packet bytes
        FileOutputStream fileCreation =  new FileOutputStream(fileName);
        for (byte[] bytes : file_received) fileCreation.write(bytes);
        fileCreation.close();

        System.out.println("\n\n" + fileName + " has been successfully transferred!\n");

        //Close Socket
        socket.close();
    }

    // data pck

    /**
     * DATA() This method is used to obtain DATA from the server during a read request.
     *
     * @throws IOException Exception used for DatagramPacket packet and socket.receive()
     */
    public void dataPACKET(ArrayList<byte[]> arrayListPackets, int msg_byte) throws IOException {
        //count
        int count = 1;

        //Data Packet
        for (byte[] data_packet : arrayListPackets)
        {
            byte[] DATA_pck = new byte[4 + data_packet.length];

            //OPCODE
            DATA_pck[0] = 0;
            DATA_pck[1] = 3;


            //BLOCK-NUMBER
            DATA_pck[2] = (byte) ((blockNum>>8)&0xff);
            DATA_pck[3] = (byte) (blockNum&0xff);
            blockNum++;

            //DATA_pck receives all the information needed to send data package
            System.arraycopy(data_packet, 0, DATA_pck, 4, data_packet.length);

            //Creating DatagramPacket and sending Packet
            DatagramPacket packet = new DatagramPacket(DATA_pck,DATA_pck.length,address,serverPort);
            socket.send(packet);
            if (arrayListPackets.get(arrayListPackets.size()-1) == data_packet)
            {
                System.out.print("\r            " + (count/1000000) + "/" + msg_byte/1000000 + " MB");
                System.out.println("\n\nFile Successfully Sent!");
                ackReceive();
                socket.close();
            }
            else
            {
                System.out.print("\r            " + (count/1000000) + "/" + msg_byte/1000000 + " MB");
                count += data_packet.length;
                ackReceive();
            }
        }

    }


    //write request
    /**
     * WRQ() is a method used for the WRQ packet. This method divides the file into many byte[] of max-size 512 bytes.
     * Then it sends them singularly, waiting for ACK in-between DATA packets.
     *
     * @param fileName name of the file wanted to be written on the server
     * @throws IOException FileNotFound and SocketConnection
     */
    public void wrqPacket(String fileName) throws IOException {
        //Make first WRT request
        initialConnectionProtocol(new byte[] {0,2},fileName);

        System.out.println("\n============Progress Bar============");
        //Receive ack before starting
        ackReceive();

        //Preparing general byte[]
        byte[] msg_byte = new byte[0];

        //try to find file and transform it into byte[]
        try
        {
            msg_byte = Files.readAllBytes(Paths.get(fileName));
        }
        //If file does not exist, program stops running
        catch (IOException e)
        {
            System.out.println("File: " + fileName + " does not Exist!" );
            System.exit(0);
        }

        ArrayList<byte[]> arrayListPackets = dividedArray512(msg_byte);

        dataPACKET(arrayListPackets,msg_byte.length);
    }

    /**
     * ACK() is used to send an acknowledgment to the server.
     *
     * @param packet needed to obtain the address and port num of the server
     *
     * @throws IOException Exception used for packetToSend
     */
    private void ackPACKET(DatagramPacket packet) throws IOException {

        //Creating byte Array for package to send
        byte[] ack_pck = new byte[4];
        //opcode
        ack_pck[0] = 0;
        ack_pck[1] = 4;

        //Block Number
        ack_pck[2] = packet.getData()[2];
        ack_pck[3] = packet.getData()[3];

        //Creating package to send
        DatagramPacket packetToSend = new DatagramPacket(ack_pck,ack_pck.length,packet.getAddress(),packet.getPort());

        //Sending package
        socket.send(packetToSend);
    }

        /**
         * Method that prepares client to receive an ACK from the server (During a WRQ)
         * @throws IOException Used for the socket
         */
    public void ackReceive() throws IOException {
        byte[] ack_pck = new byte[4];
        packet = new DatagramPacket(ack_pck,ack_pck.length);
        socket.receive(packet);
        serverPort = packet.getPort();
    }

    //UTILITIES
    /**
     * Method isError() checks if the packet receive has opcode 05
     *
     * @param packet packet received
     * @return True if opcode is 5, False if not
     */
    public boolean isError(DatagramPacket packet)
    {
        byte[] data = packet.getData();
        String data_String = new String(data,StandardCharsets.UTF_8);
        if (data[1] == 5)
        {
            System.out.println(data_String.replaceAll("\0", "").substring(2));
            return true;
        }
        else return false;
    }

    /**
     * putTogetherByteArr is a utility method. It returns a byte[], of length int totalLength which is composed of
     * all the elements of the data ArrayList<byte[]>.
     *
     * @param data ArrayList for all the byte[] wanted to be put in
     * @param totalLength Length of all byte[] in the ArrayList
     *
     * @return All elements of data into one single byte[]
     */
    public byte[] putTogetherByteArr(ArrayList<byte[]> data, int totalLength)
    {
        byte[] finalByteArr = new byte[totalLength];
        int count = 0;
        for (byte[] datum : data)
        {
            for (byte b : datum)
            {
                finalByteArr[count] = b;
                count++;
            }
        }
        return finalByteArr;
    }

    /**
     * removeFirstFour(), as the name suggests, removes the first four element of a byte[]. Useful to remove bytes before
     * creating the new document.
     *
     * @param data Used for data packages
     *
     * @return returns data without the first four bytes (opcode and block#)
     */
    public byte[] removeFirstFour(byte[] data)
    {
        byte[] final_data = new byte[data.length-4];
        System.arraycopy(data,4,final_data,0,data.length-4);
        return final_data;
    }

    /**
     * Used to close the socket via main
     */
    public void close()
    {
        socket.close();
    }

    /**
     * This method is used to divide the byte[] of the file into smaller byte[] of size 512 byte each
     *
     * @return 2d byte array of size [file_Byte/2 + 1][512]
     */
    public ArrayList<byte[]> dividedArray512(byte[] buf)  {
        int lengthByte = buf.length;
        ArrayList<byte[]> dividedBuf = new ArrayList<>();
        byte[] newDataPCK;
        for(int i = 0; i < buf.length/512 + 1; i++)
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
            System.arraycopy(buf, (i * 512), newDataPCK, 0, newDataPCK.length);
            dividedBuf.add(newDataPCK);
        }
        return dividedBuf;
    }
}
