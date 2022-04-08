import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
    // protected InetAddress address;
    // protected int port;
    // protected int localport;

  private Demultiplexer D;
  private Timer tcpTimer;

  // I DECLARED THESE
  int ackNum;
  int seqNum;
  TCPState curState;
  int windowSize = 1;
  byte[] data = null;
  

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
    localport = D.getNextAvailablePort();
    ackNum = 0;
    seqNum = 0;
    curState = TCPState.CLOSED;
    D.registerConnection(remoteAddress, localport, remotePort, this);
    sendAndWrapPacket(remoteAddress, remotePort, false, true, false, windowSize, data);
    change_state(curState, TCPState.SYN_SENT);
  }
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){
    System.out.println(p.toString());
    
    if(!p.ackFlag)
    {
      seqNum = p.ackNum;
      ackNum = p.seqNum+1;
    }
    switch(curState)
    {
      case LISTEN:
      if(p.synFlag)
      {
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, true, true, windowSize, data);
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
      change_state(curState, TCPState.ESTABLISHED);

      break;

      case ESTABLISHED:
      break;

      case FIN_WAIT_1:
      break;

      case CLOSE_WAIT:
      break;

      case FIN_WAIT_2:
      break;

      case LAST_ACK:
      break;

      case CLOSING:
      break;

      case TIME_WAIT:
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
    D.registerListeningSocket(localport, this);
    change_state(curState, TCPState.LISTEN);
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
