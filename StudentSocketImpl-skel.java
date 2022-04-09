import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
    // protected InetAddress address; 
    // protected int port;
    // protected int localport;

  private Demultiplexer D; //For managing Sockets, and Socket's function calls
  private Timer tcpTimer; //For managing lost packets

  // I DECLARED THESE
  int ackNum; //Local AckNum, Added to Packets Before Sending
  int seqNum; //Local Sequence Num, For Duplciate Management and Added Before Sending Pkts
  TCPState curState; //Current State of the Socket in the TCP FSM
  int windowSize = 1; //Window Size of Packet, Arbitrarily set to 1
  byte[] data = null; //Data Sent in Packet, Arbitratily set to null
  

  enum TCPState { CLOSED, SYN_SENT, SYN_RECEIVED, LISTEN, ESTABLISHED, CLOSE_WAIT, FIN_WAIT_1, FIN_WAIT_2, CLOSING, LAST_ACK, TIME_WAIT}

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
  }


  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress remoteAddress, int remotePort) throws IOException{    
    //EVENT ClientSide: Connect() 

    localport = D.getNextAvailablePort(); 
    ackNum = 0;
    seqNum = 0;
    curState = TCPState.CLOSED;
    D.registerConnection(remoteAddress, localport, remotePort, this);

    //RESPONSE ClientSide: Send Initial Syn Message & Switch to SYN_SENT STATE
    sendAndWrapPacket(remoteAddress, remotePort, false, true, false, windowSize, data);
    change_state(curState, TCPState.SYN_SENT);
  }
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){
    System.out.println(p.toString());
    if(p.synFlag || p.finFlag) 
    {
      seqNum = p.ackNum;
      ackNum = p.seqNum+1;
    }
    switch(curState)
    {
      case LISTEN:
      //EVENT Server Side: Receive SYN Pckt 
      if(p.synFlag)
      {
        //RESPONSE Server Side: Send SYN + ACK, Switch To SYN_RCV
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, true, false, windowSize, data);
        try
        {
          D.unregisterListeningSocket(localport, this);
          D.registerConnection(p.sourceAddr, localport, p.sourcePort, this);
        }
        catch(Exception e)
        {
          e.printStackTrace();
        }
        change_state(curState, TCPState.SYN_RECEIVED);
      }
      break;
      
      case SYN_SENT:
      //EVENT Client Side: Receive SYN + ACK

      //RESPONSE Client Side: Send ACK, Change State to ESTABLISHED
      change_state(curState, TCPState.ESTABLISHED);

      break;

      case SYN_RECEIVED:
      //EVENT Server Side: Receive ACK

      //RESPONSE Server Side: Change State to ESTABLISHED
      break;

      case ESTABLISHED:
      //EVENT Server Side: Receive FIN

      //RESPONSE Server Side: Send ACK, switch State to CLOSE_WAIT

      break;

      case FIN_WAIT_1:
      //EVENT A Client Side: Receive FIN 

      //EVENT B Client Side: Reveive ACK

      //RESPONSE A Client Side: Send ACK, switch State to CLOSING

      //RESPONSE B Client Side: switch State to FIN_WAIT_2

      break;

      case CLOSE_WAIT: //IN CLOSING?

      break;

      case FIN_WAIT_2:
      //EVENT Cient Side: Receive FIN

      //RESPONSE Client Side: send ACK, switch State to TIME_WAIT
      break;

      case LAST_ACK:
      //EVENT Server Side: Receive ACK

      //RESPONSE Server Side: switch State to TIME_WAIT
      break;

      case CLOSING:
      //EVENT Client Side: Receive ACK

      //RESPONSE  Client Side: switch State to TIME_WAIT
      break;

      case TIME_WAIT: //Needed?
      break;
    }
    this.notifyAll();
  }
  
  /** 
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling 
   * ServerSocket.accept(), but this method belongs to the Socket object 
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
    curState = TCPState.CLOSED;

    //TCP Diagram EVENT Server Side: Accept Conenction, Switch to LISTENING State
    D.registerListeningSocket(localport, this);
    change_state(curState, TCPState.LISTEN);  

    //TCP Diagram RESPNOSE Server Side: None
  }

  
  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    // project 4 return appIS;
    return null;
    
  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    // project 4 return appOS;
    return null;
  }


  /**
   * Closes this socket. 
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
    switch(curState){
      case ESTABLISHED:
        //EVENT Client Side: close()
        //RESPONSE Client Side: Send FIN, switch State to FIN_WAIT_1
      case CLOSE_WAIT:
        //EVENT Server Side: close()
        //RESPONSE Server Side: send FIN, switch State to LAST_ACK
      break;
      
    }
  }

  /** 
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if(tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return 
   * information.
   */
  public synchronized void handleTimer(Object ref){

    // this must run only once the last timer (30 second timer) has expired
    tcpTimer.cancel();
    tcpTimer = null;
  }

  public void sendAndWrapPacket(InetAddress remoteAddress, int remotePort, boolean ackFlag, boolean synFlag, boolean finFlag, int windowSize, byte[] data)
  {
    TCPPacket packetToSend = new TCPPacket(localport, remotePort, ackNum, seqNum, ackFlag, synFlag, finFlag, windowSize, data);
    TCPWrapper.send(packetToSend, remoteAddress);
  }

  public void change_state(TCPState curState, TCPState state_change_to){
    this.curState = state_change_to;
    System.out.print("\n ACTION-STATE-CHANGED: " + curState + " to " + state_change_to + "\n");
}

}
