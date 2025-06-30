package mttcpserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class TFTP_TCP_Server extends Thread {

    private final Socket slaveSocket;

    public TFTP_TCP_Server(Socket socket) {
        super("MTTCPServerThread");
        this.slaveSocket = socket;
    }

    @Override
    public void run() {
        try {
            DataInputStream socketInput = new DataInputStream(slaveSocket.getInputStream());
            boolean modeSelection = false;
            while (!modeSelection)
            {
                int packetLenght = socketInput.readInt();
                byte[] packet = new byte[packetLenght];
                socketInput.readFully(packet,0,packet.length);
                if (packet[1] == 1)
                {
                    modeSelection = true;
                    rrqPACKET(packet);
                }
                else if (packet[1] == 2)
                {
                    modeSelection = true;
                    wrqPACKET(packet);
                }
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    /**
     * rrqPACKET() gets the filename from the received packet and begins the exchange of DATA packets. If there is an
     * Error (such as the file not existing), an Error packet will be sent and the connection will terminate.
     *
     * @param packet first packet received from the client
     * @throws IOException filename and socket
     */
    public void rrqPACKET(byte[] packet) throws IOException
    {
        String filename = getFilename(packet);
        dataPACKET(filename);
        slaveSocket.close();
    }

    /**
     * getFilename() simply gets the filename in the first packet received from the client
     *
     * @param packet first packet received from the client
     * @return filename (in String) wanted to be sent or wanted to be saved on the server
     */
    private String getFilename(byte[] packet) {
        ArrayList<Byte> filename_byte = new ArrayList<>();
        for (int i = 2; i < packet.length; i++)
        {
            if (packet[i] == 0)
            {
                break;
            }
            else filename_byte.add(packet[i]);
        }
        return new String(ArrayListToByte(filename_byte), StandardCharsets.UTF_8);
    }

    /**
     * dataPACKET()
     *
     * @param filename filename of the file wanted to be sent to the client
     * @throws IOException exception for the file and socket
     */
    public void dataPACKET(String filename) throws IOException
    {
        DataOutputStream socketOutput = new DataOutputStream(slaveSocket.getOutputStream());
        byte[] file_byte = new byte[0];
        try
        {
            file_byte = Files.readAllBytes(Paths.get(filename));
        }
        catch (IOException e)
        {
            errorPACKET("file " + filename + " does not exist!");
        }
        ArrayList<byte[]> file_div = dividedArray512(file_byte);
        for (byte[] bytes : file_div)
        {
            socketOutput.write(bytes,0,bytes.length);
        }
    }

    private void errorPACKET(String errorMessage) throws IOException {
        byte[] errorMessage_Byte = errorMessage.getBytes(StandardCharsets.UTF_8);
        byte[] errorPCK = new byte[5 + errorMessage_Byte.length];

        //opcode
        errorPCK[0] = 0;
        errorPCK[1] = 5;

        //Error Code
        errorPCK[2] = 0;
        errorPCK[3] = 1;

        //Error Message
        System.arraycopy(errorMessage_Byte, 0, errorPCK, 4, errorMessage_Byte.length);

        //Zero byte
        errorPCK[errorPCK.length-1] = 0;

        //Creating output stream and sending package
        DataOutputStream socketOutput = new DataOutputStream(slaveSocket.getOutputStream());
        socketOutput.write(errorPCK);
        System.out.println("Connection with " + slaveSocket.getPort() + " has ended due to error!");
        slaveSocket.close();
    }

    /**
     * wrqPACKET() is used to begin a write request between the client and the server
     *
     * @param packet first packet received from the client
     * @throws IOException filename and socket
     */
    public void wrqPACKET(byte[] packet) throws IOException
    {
        DataInputStream in_file = new DataInputStream(slaveSocket.getInputStream());
        String filename = getFilename(packet);
        byte[] buf = new byte[514];
        FileOutputStream fileCreation =  new FileOutputStream(filename);
        //While read inputstream is more or equal 0 (AKA not empty)
        while(in_file.read(buf) >= 0)
        {
            buf = removeFirstTwo(buf);
            fileCreation.write(buf);
            buf = new byte[514];
        }
        fileCreation.close();
    }

    /**
     * arrayListToByte() gets an ArrayList<Byte> and returns the equivalent byte[]
     *
     * @param data data wanted to transform into a byte[]
     * @return ArrayList<Byte> data into byte[]
     */
    public byte[] ArrayListToByte(ArrayList<Byte> data)
    {
        byte[] dataByte = new byte[data.size()];
        for (int i = 0; i < data.size(); i++)
        {
            dataByte[i] = data.get(i);
        }
        return dataByte;
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
            newDataPCK = new byte[newLength+2];
            newDataPCK[0] = 0;
            newDataPCK[1] = 3;
            System.arraycopy(buf, (i * 512), newDataPCK, 2, newLength);
            dividedBuf.add(newDataPCK);
        }
        return dividedBuf;
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
