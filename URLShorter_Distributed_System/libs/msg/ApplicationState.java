package libs.msg;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import libs.config.ConfigState;

public class ApplicationState implements Serializable 
{
    private static ApplicationState global; // Singleton instance
    private static ConfigState config = new ConfigState();

    private String hostName;
    private String hostIp;
    private String applet;
    private Boolean ready;

    /*
     * If set to true then we know that this application is ready to handle requests.
     * 
     */
    private Boolean internalChannelReady;
    /**
     * We have two states.
     * Http server 
     */

    /*epoch time since application bootup*/
    public int epochTime = -1;

    private Boolean safeToAccessInstance = false;

    public ApplicationState(String hostName, String hostIp) {
        this.hostName = hostName;
        this.hostIp = hostIp;
        this.ready = false;
        this.internalChannelReady = false;
    }

    private ApplicationState() {
        try{
            this.hostName = InetAddress.getLocalHost().getHostName();
            this.hostIp = InetAddress.getLocalHost().getHostAddress();
        }catch(UnknownHostException ex){
            ex.printStackTrace();
            System.out.println("Critical error has querying system host information.");
        }
        this.ready = false;
        this.internalChannelReady = false;
        this.safeToAccessInstance = true;
    }

    public static ApplicationState getInstance() {
        if (global == null) {
            global = new ApplicationState(); // Initialize the instance lazily
        }
        return global;
    }

    public void setApplets(String applet) {
        this.applet = applet;
    }

    public void setReadyState(Boolean newState) {
        this.ready = newState;
    }

    public void setInternalChannelReady(Boolean newState){
        this.internalChannelReady = newState;
    }


    @Override
    public String toString() {
        return this.hostName + " " + this.hostIp + " " + ready.toString() + " " + applet;
    }
}