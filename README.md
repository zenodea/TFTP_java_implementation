# TFTP Java Implementation

A Java implementation of the Trivial File Transfer Protocol (TFTP) supporting both UDP and TCP transports.

## Overview

This project implements TFTP as specified by [RFC 1350](https://www.ietf.org/rfc/rfc1350.txt) with both client and server applications. The implementation includes both UDP-based (standard TFTP) and TCP-based variants.

## Features

- **UDP Implementation**: Standard TFTP protocol over UDP
- **TCP Implementation**: Custom TFTP variant over TCP
- **File Operations**: Read (RRQ) and Write (WRQ) requests
- **Error Handling**: Basic error responses for file not found
- **Octet Mode**: Binary file transfer support

## Project Structure

```
src/
├── client-udp/     # UDP client implementation
├── client-tcp/     # TCP client implementation  
├── server-udp/     # UDP server implementation
└── server-tcp/     # TCP server implementation
```

## Limitations

- Supports octet mode only
- Error handling limited to file not found scenarios
- No support for data duplication error handling
- Single-threaded server operations

## Usage

### UDP Server
```bash
java -cp src/server-udp TFTP_UDP_Server
```

### UDP Client
```bash
java -cp src/client-udp TFTP_UDP_Client
```

### TCP Server
```bash
java -cp src/server-tcp TFTP_TCP_Server
```

### TCP Client
```bash
java -cp src/client-tcp TFTP_TCP_Client
```

## Protocol Details

The implementation follows RFC 1350 specifications:
- Port 1350 for initial connections
- 512-byte data blocks
- Acknowledgment-based flow control
- Standard TFTP opcodes (RRQ, WRQ, DATA, ACK, ERROR)
