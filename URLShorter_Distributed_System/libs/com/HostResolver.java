/*
 * This package contains the set of utilities required for
 * resolving a host and its status.
*/


package libs.com;

import libs.config.ConfigState;
import libs.msg.*;
import java.net.*;
import java.io.*;



public class HostResolver 
{

    /**
     * This defines the set of applets available.
     */
    static enum APPLETS {
        LOAD_BALANCE,
        KVSTORE,
        MONITOR,
        AGENT
    }

    static ConfigState config =  new ConfigState();

    /**
     * Resolves the hosts health and obtain a ApplicationState 
     * for a specific hostName/Agent.
     * 
     * This will query the correct API for a hostData via the /state endpoint
     * 
     */
    public static ApplicationState fetchHostData(String hostName, APPLETS applet) {
        return null; 
    }

    /**
     * Checks whether a host is reachable by sending a ping.
     */
    static public Boolean isHostReachable(String hostName){
        Boolean isReachable = false;
        try {
            isReachable = InetAddress.getByName(hostName).isReachable(ConfigState.hostReachableTimeOut);
        } catch (IOException e) {
            isReachable = false;
            e.printStackTrace();
        }
        return isReachable;
    }

    static public String getCurrentHostName(){
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
        }
        return "UNKNOWN_HOST";
    }

}

