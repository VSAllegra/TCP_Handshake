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
    curState = TCPState.CLOSED;
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
    address = remoteAddress; 
    port = remotePort;
    ackNum = 0;
    seqNum = 0;
    curState = TCPState.CLOSED;
    D.registerConnection(remoteAddress, localport, remotePort, this);

    //RESPONSE ClientSide: Send Initial Syn Message & Switch to SYN_SENT STATE
    sendAndWrapPacket(remoteAddress, remotePort, false, true, false, windowSize, data);
    change_state(TCPState.SYN_SENT);
    while(curState != TCPState.ESTABLISHED)
      {
      try
      {
        System.out.println("Waiting For Notify");
        wait();
        System.out.println("Cur State Wait " + curState);
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
  }
  return;
    }
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){
    // System.out.println("PACKET FLAGS:");
    // System.out.println(p.ackFlag + " " + p.synFlag + " " + p.finFlag);
    // System.out.println(p.toString());
    System.out.println("ABOUT TO NOTIFY");
    this.notifyAll();
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
        address = p.sourceAddr;
        port = p.sourcePort;
        try
        {
          D.unregisterListeningSocket(localport, this);
          D.registerConnection(p.sourceAddr, localport, p.sourcePort, this);
        }
        catch(Exception e)
        {
          e.printStackTrace();
        }
        change_state(TCPState.SYN_RECEIVED);
      }
      break;
      
      case SYN_SENT:
      //EVENT Client Side: Receive SYN + ACK

      //RESPONSE Client Side: Send ACK, Change State to ESTABLISHED
      if(p.synFlag && p.ackFlag)
      {
      cancel_reset_timer();
      sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      change_state(TCPState.ESTABLISHED);
      }

      break;

      case SYN_RECEIVED:
      //EVENT Server Side: Receive ACK
      if(p.ackFlag)
      {
        cancel_reset_timer();
        change_state(TCPState.ESTABLISHED);
        
      }

      //RESPONSE Server Side: Change State to ESTABLISHED
      break;

      case ESTABLISHED:
      //EVENT Client Side: Receive FIN
      //RESPONSE Client Side: Send ACK, switch State to CLOSE_WAIT
      if(p.ackFlag && p.synFlag)
      {
        cancel_reset_timer();
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
        
      }
      if(p.finFlag)
      {
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
        change_state(TCPState.CLOSE_WAIT);
      }

      break;

      case FIN_WAIT_1:
      //EVENT A Sever Side: Receive FIN 
      //EVENT B Server Side: Reveive ACK
      //RESPONSE A Server Side: Send ACK, switch State to CLOSING
      //RESPONSE B Server Side: switch State to FIN_WAIT_2
      if(p.finFlag)
      {
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
        change_state(TCPState.CLOSING);
      }
      if(p.ackFlag)
      {
        cancel_reset_timer();
        change_state(TCPState.FIN_WAIT_2);
        
      }

      break;

 

      case FIN_WAIT_2:
      //EVENT Server Side: Receive FIN
      //RESPONSE Server Side: send ACK, switch State to TIME_WAIT
      if(p.finFlag)
      {
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
        change_state(TCPState.TIME_WAIT);
      }
      break;

      case LAST_ACK:
      //EVENT Client Side: Receive ACK
      //RESPONSE Client Side: switch State to TIME_WAIT
      if(p.ackFlag)
      {
        cancel_reset_timer();
        change_state(TCPState.TIME_WAIT);
        
      }
      break;

      case CLOSING:
      //EVENT Server Side: Receive ACK
      if(p.ackFlag)
      {
        cancel_reset_timer();
        change_state(TCPState.TIME_WAIT);
        
      }

      case CLOSE_WAIT:
      if(p.finFlag)
      {
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      }


      //RESPONSE  Sevrer Side: switch State to TIME_WAIT
      break;

      
    }
    // this.notifyAll();
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
    change_state(TCPState.LISTEN);
    while(curState != TCPState.SYN_RECEIVED && curState != TCPState.ESTABLISHED)
    {
      try
      {
        System.out.println("Waiting For Notify");
        wait();
        System.out.println("Cur State Wait " + curState);
      }
      catch(Exception e)
      {
        e.printStackTrace();
      } 
    }
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
    System.out.println("CLOSING WAS CALLED! State is: " +  curState);
    switch(curState){
      case ESTABLISHED:
        //EVENT Server Side: close()
        //RESPONSE Server Side: Send FIN, switch State to FIN_WAIT_1
        sendAndWrapPacket(address, port, false, false, true, windowSize, data);
        change_state(TCPState.FIN_WAIT_1);
        break;
      case CLOSE_WAIT:
        //EVENT Client Side: close()
        //RESPONSE Client Side: send FIN, switch State to LAST_ACK
        sendAndWrapPacket(address, port, false, false, true, windowSize, data);
        change_state(TCPState.LAST_ACK);
      break;
    }
    while(true)
    {
      try{
        wait();
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }
  }

  /** 
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if(tcpTimer == null){
      tcpTimer = new Timer(false);
    }
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
    if(curState == TCPState.TIME_WAIT){
      change_state(TCPState.CLOSED);
      try{
        D.unregisterConnection(address, localport, port, this);
      }catch(Exception e){
        e.printStackTrace();
      }
    }
    else{
     resendPacket((TCPPacket)ref);
    }

  }

  public void sendAndWrapPacket(InetAddress remoteAddress, int remotePort, boolean ackFlag, boolean synFlag, boolean finFlag, int windowSize, byte[] data)
  {
    TCPPacket packetToSend = new TCPPacket(localport, remotePort, seqNum, ackNum, ackFlag, synFlag, finFlag, windowSize, data);
    TCPWrapper.send(packetToSend, remoteAddress);
    if(finFlag || synFlag)
    {
       createTimerTask(20000, packetToSend);
    }
   
  }

  public void resendPacket(TCPPacket p)
  {
    TCPWrapper.send(p, p.sourceAddr);
    createTimerTask(20000, p);
  }

  public void change_state(TCPState state_change_to){
    System.out.println("ACTION-STATE-CHANGED: " + curState + " to " + state_change_to );
    curState = state_change_to;
  }

  public void cancel_reset_timer(){
    tcpTimer.cancel();
    tcpTimer = null;
  }


}
