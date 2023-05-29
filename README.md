### TFTP-Java-Implementation

### Implemetation of the Trivial File Transfer Protocol (TFTP)
- Implementation of TFTP as specified by [RFC 1350](https://www.ietf.org/rfc/rfc1350.txt).
- Client and Server applications.
- Built upon UDP.
- The following restrictions have been put into place:
  - Support for octet mode only. 
  - Support only for error handling when the server is unable to satisfy the request because the file cannot be found.
  - No support for error handling when data duplication occurs. 

### Implementation of TFTP on top of TCP
