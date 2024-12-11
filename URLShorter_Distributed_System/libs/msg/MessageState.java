package libs.msg;

import java.io.*;

import libs.config.ConfigState;

/*
/**
 * Defines the message context. This is used to multiplex the message
 * to the different subsystems available.
 */

 
/**
 * Defines the Message Format for cross java application
 * communication.
 */
public class MessageState implements Serializable
{
  public static enum MsgCtx {
    CLUSTER_GOSSIP_REQUEST,
    CLUSTER_GOSSIP_RESPONSE,
    CLUSTER_SYNC_READ_REQUEST,
    CLUSTER_SYNC_WRITE_SUCCESS,
  }

  /**
   * This defines the object type this message encodes.
   * This is used by the reciever to correctly recast the object
   * to the original type.
   */
  public static enum ObjectCtx {
    APPLICATION_STATE,
    CLUSTER_STATE,
  }


  public static enum AdminSystemCmd {
    SIGTERM, // Graceful shutdown
    SIGTSTP, // Graceful pause
    RESYNC,  // Perform a restart
  }

    public MsgCtx ctx;
    public boolean hasPayload; 
    public Object payloadData;
    public String srcHost;
    public String dstHost;

    public MessageState(MsgCtx ctx, String srcHost, String dstHost, Object data){
      this.ctx = ctx;
      this.hasPayload = true;
      this.payloadData = data;
      this.srcHost = srcHost;
      this.dstHost = dstHost;
    }

    @Override
    public String toString() {
        return "" + srcHost + " "+ dstHost;
    }
}
