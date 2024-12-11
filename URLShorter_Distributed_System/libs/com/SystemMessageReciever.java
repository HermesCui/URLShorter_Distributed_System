package libs.com;

import java.util.concurrent.*;

import libs.msg.MessageState;

/*
 * This defines a protocol for handling recieved messages
 * from other hosts.
 * 
 * This shall be a singleton and run for the entirety of the background in another thread.
 */
public class SystemMessageReciever 
{
    /* Set of tasks to be handled.*/
    private static final ConcurrentLinkedQueue<MessageState> tasks = new ConcurrentLinkedQueue<>();

    // 
}
