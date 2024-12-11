package libs.msg;

import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;

/**
 * A simple application debug message
 * for development and testing.
 */
public class SimpleMessage implements Serializable
{
    private String message;
    private String srcHost;

    public SimpleMessage(String message, String host){
        this.message = message;
        this.srcHost = host;
    }
}
