import java.net.*;
import java.beans.Customizer;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
    // protected InetAddress address; 
    // protected int port;
    // protected int localport;

  private Demultiplexer D; //For managing Sockets, and Socket's function calls
  private Timer tcpTimer; //For managing lost packets
  private Timer TIME_WAIT_Timer;

  // I DECLARED THESE
  int ackNum; //Local AckNum, Added to Packets Before Sending
  int seqNum; //Local Sequence Num, For Duplciate Management and Added Before Sending Pkts
  TCPState curState; //Current State of the Socket in the TCP FSM
  int windowSize = 1; //Window Size of Packet, Arbitrarily set to 1
  byte[] data = null; //Data Sent in Packet, Arbitratily set to null

  int resend_counter;
  

  enum TCPState { CLOSED, SYN_SENT, SYN_RECEIVED, LISTEN, ESTABLISHED, CLOSE_WAIT, FIN_WAIT_1, FIN_WAIT_2, CLOSING, LAST_ACK, TIME_WAIT}

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
    //Initial the other server socket to prevent null error while closing
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

    //RESPONSE Client Side: Send Initial Syn Message & Switch to SYN_SENT STATE
    sendAndWrapPacket(remoteAddress, remotePort, false, true, false, windowSize, data);
    change_state(TCPState.SYN_SENT);
    while(curState != TCPState.CLOSE_WAIT)
      {
      try
      {
        synchronized(this){
          wait();
        }
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
        //RESPONSE Server Side: Send SYN + ACK, Switch To SYN_RCV, Register Connection
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
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, true, false, windowSize, data);
        change_state(TCPState.SYN_RECEIVED);
      }
      break;
      
      case SYN_SENT:
      //EVENT Client Side: Receive SYN + ACK

      if(p.synFlag && p.ackFlag)
      {
      //RESPONSE Client Side: Send ACK, Change State to ESTABLISHED
      if(tcpTimer != null) {cancel_reset_timer();}
      // cancel_reset_timer();
      sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      change_state(TCPState.ESTABLISHED);
      }
      break;

      case SYN_RECEIVED:
      //EVENT Server Side: Receive ACK
      if(p.ackFlag)
      {
        //RESPONSE Server Side: Change State to ESTABLISHED
        if(tcpTimer != null) {cancel_reset_timer();}
        // cancel_reset_timer();

        change_state(TCPState.ESTABLISHED);
        this.notifyAll();
      }
      break;

      case ESTABLISHED:
      //EVENT Client Side: Receive FIN
      if(p.finFlag)
      {
        //RESPONSE Client Side: Send ACK, switch State to CLOSE_WAIT
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
        change_state(TCPState.CLOSE_WAIT);
        this.notifyAll();
      }

      //EVENT Client Side: Receive Dup SYNACK (ACK (SynSen) was Lost)
      if(p.ackFlag && p.synFlag)
      {
        //RESPONSE Clinet Side: Resend ACK
        if(tcpTimer != null) {cancel_reset_timer();}
        // cancel_reset_timer();

        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      }      
      break;

      

      case FIN_WAIT_1:
      //EVENT Sever Side: Receive FIN 
      if(p.finFlag)
      {
        if(tcpTimer != null) {cancel_reset_timer();}

        //RESPONSE Server Side: Send ACK, switch State to CLOSING
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
        change_state(TCPState.CLOSING);

        //For Same Edge Case but on Server Side
        createTimerTask(30000, null);
      }

      //EVENT B Server Side: Reveive ACK
      if(p.ackFlag)
      {
        //RESPONSE B Server Side: switch State to FIN_WAIT_2
        if(tcpTimer != null) {cancel_reset_timer();}

        if(p.synFlag)
        {
          sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);

          sendAndWrapPacket(p.sourceAddr, p.sourcePort, false, false, true, windowSize, data);
        }

        // cancel_reset_timer();
        change_state(TCPState.FIN_WAIT_2);

        //For Same Edge Case but on Server Side
        createTimerTask(30000, null);
        
      }
      break;

      case FIN_WAIT_2:
      //EVENT Server Side: Receive FIN
      if(p.finFlag)
      {
        //RESPONSE Server Side: send ACK, switch State to TIME_WAIT
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
        change_state(TCPState.TIME_WAIT);

        System.out.println("WAITING");

        //Cancel Necessary Cause of Edge Case 
        if(tcpTimer != null) {cancel_reset_timer();}
        createTimerTask(30000, null);
      }
      break;

      case LAST_ACK:
      //EVENT Client Side: Receive ACK
      if(p.ackFlag)
      {
        //RESPONSE Client Side: switch State to TIME_WAIT Start End Closing Timer
        if(tcpTimer != null) {cancel_reset_timer();}
        // cancel_reset_timer();

        change_state(TCPState.TIME_WAIT);

        System.out.println("WAITING");
        createTimerTask(30000, null);
      }

      //EVENT Client Side: Receive Duplicate FIN (ACK (CW) is lost, and close() happens)
      if(p.finFlag)
      {
        //Response Client Side: Resend ACK
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      }
      break;

      case CLOSING:
      //EVENT Server Side: Receive ACK
      if(p.ackFlag)
      {
        //RESPONSE  Sevrer Side: switch State to TIME_WAIT
        if(tcpTimer != null) {cancel_reset_timer();}
        // cancel_reset_timer();
        change_state(TCPState.TIME_WAIT);
        
      }

      //EVENT Server Side: Receive Duplicate Fin (Ack Sent (FW1) was Lost)
      else if(p.finFlag)
      {
        //RESPONSE Server Side: Resend Ack 
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      }
      break;

      case CLOSE_WAIT:
      //Event Client Side: Receive Duplicate FIN, (Ack Sent (Established) was Lost)
      if(p.finFlag)
      {
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      }
      break;

      case TIME_WAIT:
      //EVENT  Server Side: Receive Duplicate Fin (Ack Sent (FW2) was lost)
      if(p.finFlag)
      {
        //RESPONSE Server Side: Resend Ack
        sendAndWrapPacket(p.sourceAddr, p.sourcePort, true, false, false, windowSize, data);
      }
      break;    
    }
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
    while(curState != TCPState.ESTABLISHED)
    {
      try
      {
        synchronized(this){
          wait();
        }
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
      //EVENT Server Side: close()
      case ESTABLISHED:
        //RESPONSE Server Side: Send FIN, switch State to FIN_WAIT_1
        sendAndWrapPacket(address, port, false, false, true, windowSize, data);
        change_state(TCPState.FIN_WAIT_1);
        break;

      //EVENT Client Side: close()
      case CLOSE_WAIT:
        //RESPONSE Client Side: send FIN, switch State to LAST_ACK

        //For Edge Case: Sever Side Goes to Closed, before Clinet is in Time_Wait
        resend_counter = 0; 

        sendAndWrapPacket(address, port, false, false, true, windowSize, data);
        change_state(TCPState.LAST_ACK);
      break;
    }

    //Keep Process Alive in Background Thread
    try{
     ClosingThread thread_for_closing = new ClosingThread(this);
     thread_for_closing.run();
    }catch(Exception e){
      e.printStackTrace();
    }
    return;
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
    System.out.println("TIMER EXPIRED");
    cancel_reset_timer();


    resend_counter++;

    /**Edge Case for when server side closes before client hits time_wait
     * Assume that if no response is received after 3 attempts 
     * the other side has already closed
     * */
    if(curState == TCPState.LAST_ACK && resend_counter > 2){ //Set Arbitrarily to 3 ACKS
      change_state(TCPState.TIME_WAIT);
    }

    // this must run only once the last time(r (30 second timer) has expired
    if(curState == TCPState.TIME_WAIT || ((curState == TCPState.FIN_WAIT_2 || curState == TCPState.CLOSING) && resend_counter > 2)){
      change_state(TCPState.CLOSED);
      try{
        D.unregisterConnection(address, localport, port, this);
      }catch(Exception e){
        e.printStackTrace();
      }
      notifyAll();
    }
    else{
     System.out.println(ref.toString());
     resendPacket((TCPPacket)ref);
    }

  }

  public void sendAndWrapPacket(InetAddress remoteAddress, int remotePort, boolean ackFlag, boolean synFlag, boolean finFlag, int windowSize, byte[] data)
  {
    TCPPacket packetToSend = new TCPPacket(localport, remotePort, seqNum, ackNum, ackFlag, synFlag, finFlag, windowSize, data);
    TCPWrapper.send(packetToSend, remoteAddress);

    /*Only Want to Make a Resend Timer for Fins and Sins, 
    Resending Acks is handled in Receive Pack */
    if(finFlag || synFlag)
    {
       createTimerTask(20000, packetToSend);
    }
   
  }

  public void resendPacket(TCPPacket p)
  {
    System.out.println("PACKET IS BEING RESENT");
    TCPWrapper.send(p, address);
    createTimerTask(5000, p);
  }

  public void change_state(TCPState state_change_to){
    System.out.println("ACTION-STATE-CHANGED: " + curState + " to " + state_change_to );
    curState = state_change_to;
  }

  public void cancel_reset_timer(){
    tcpTimer.cancel();
    tcpTimer = null;
  }

  public TCPState getCurrentState(){
    return curState;
  }

  class ClosingThread implements Runnable{
      StudentSocketImpl socket;

      ClosingThread(StudentSocketImpl my_socket){
        socket = my_socket;
      }

      public void run(){
        while(socket.getCurrentState() != TCPState.CLOSED)
        {
          try{
              socket.wait();
          }
          catch(Exception e)
          {
            e.printStackTrace();
          }
        }

      }

  }


}
