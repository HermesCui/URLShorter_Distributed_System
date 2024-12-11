package libs.config;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;

public class ConfigState 
{      
    public static String appletName = "GENERIC";
    public static String hostName = null; 
    public static String hostAddress = null; 

    public static AppletType appletType; 


    /** PORTS for client facing applications 
     * Some hosts are running multiple applets so 
     * we'd like to direct them based on their ports.
     * 
     * Ideally we'd like to have 1 host per applet, but
     * we have a small port count.
    */
     public static int kvStorePublicFacingPort = 45601;
     public static int kvStorePeer2PeerInternalFacingPort = 45602;
    
     //Maintain contract for load balancer port.
     public static int loadBalancerPublicFacingPort = 8080;
     public static int loadBalancerPeer2PeerInternalFacingPort = 45614;


     public static int monitorSystemPublicFacingPort = 45625;
     public static int monitorPeer2PeerInternalFacingPort = 45626;

     /*
      * The agent shall have no such port. 
      */
    

     /*
      * Number of webworkers to allocate for handling http requests
      * We can tune it based on responsibility for example, a load balancer might 
      * want to have a high number of web workers to response to multiple gossip/info requests
      * Likewise a kvStorePeer2Peer internal work might just want to have 4 webworkers (1 neighbour + 2 randoms)
      */
     public static int kvStorePeer2PeerInternalWebWorkerCount = 2;
     public static int loadBalancerPeer2PeerInternalWebWorkerCount = 2;
     public static int monitorPeer2PeerInternalWebWorkerCount  = 2;


     public static int loadBalancerWebWorkerThreadCount = 8;

     /**
      * Directory path.
      */
    public static String executingUserProfile = System.getProperty("user.name");
    public static String baseVirtualDataPath = "/virtual/" + executingUserProfile + "/URLShortner/";

    /**
     * The artifacts is where database / other assets
     * generated during runtime is stored.
     * 
     * This includes items such as logs / databases/ configurations
     */
    public static String baseArtifactDataPath = "/artifacts/";
    
    public static String appletLogBasePath = baseVirtualDataPath + "logs/data.txt";

    /**
     * Base executable source location.
     */
    public static String baseAppletExcutablePath = null;

    /**
     * Defines the default path for where the paths KVStore for the URLShortner is.
     */
    public static String kvStoreDbPathDir = baseVirtualDataPath + "db/";
    public static String kvStoreDbPath = baseVirtualDataPath + "db/KVStore.db";

    /**
     * Defines the default path for where the path for the Agent Meta Information Stored
     * The databyte stream is hashed and its corrosponding data is stored in
     * 
     * Agent.data.ck
     */
    public static String serverAgentDataPath = "/db/Agent.data";

    /*
     * Defines where the WAL logs are stored. 
     */
    public static String kvStoreWalPath = "/wal/";
    
    /**
     * Defines where the Incoming WAL logs are stored for later processing. 
     */
    public static String kvStoreDBWalShippingInboundPath = "/inbound-wal/";

    /**
     * Represents the time to wait before a host times out.
     * This can be due to the host not being online.
     */
    public static int hostReachableTimeOut = 500;

    /**
     * Defines the amount of time to wait till a node is considered
     * dead / removed.
     * 
     * In this case we set the time till a node is considered dead to be 
     * 3 minutes. 
     * 
     */
    public static int kvStoreClusterRemovalTimeout = 400;

    //not enough time to make it unique. make a generic one for all.
    public static int hostRemovalTimeout = 30; //30 sec for demo, 300 for acc // 5 minutes

    /*
     * The amount of seconds before a node is set to be
     * have a status of being suspected.
     */
    public static int nodeSuspectedFlagTimeout = 200;


    /*
     * Represents the time to wait for a host to sends
     * its application state data.
     */
    public static int hostFetchApplicationStateTimeOut = 500;

    /*
     * Represents the http port that a server should be
     * listening on for recieving data jobs.
     */
    public static int internalCommunicationChannelPort = 3737;


    
    /**
     * Represents the bound of workers for handling
     * requests for internal activities.
     * */
    public static int systemMessageSyncWorkerThreadCount = 10;
    public static int systemMessageASyncWorkerThreadCount = 10;


    /* KVStore Only Properties*/
    public static int kvStoreWebWorkerThreadCount = 25;
    public static int lbStoreWebWorkerThreadCount = 5;

    /*
     * Agent based values: 
     */
    public static int processHealthyCheckTimeOut = 120;

    

    public static int numberOfReplicatesToWriteSuccess = 14;



    /*
     * Sets the configuation state globally before entering 
     * the bootstrap state.
     */
    public static String proxyServerExecutionPath = "/proxyServer";
    public static String KVStoreServerExecutionPath = "/serverSqlite";
    public static String serverOrchestratorExecutionPath = "/serverOrchestrator";
    public static String agentServerExecutionPath = "/serverAgent";
    public static String testDevelopmentAgentExecutionPath = "/testlibs";

    /* Path to the application folder from java argument*/
    private static int staticApplicationCodeArgumentIndex = 0; 
    private static int staticApplicationConfigArgumentIndex = 1; 
    /*
     * Path to the config directory relative to the apps directory.
     */
    public static String baseConfigPath = null;
    public static String seedConfigPath = "/seed.data";

    public static void parseEnviromentVariables(String[] args, AppletType appletType) throws UnknownHostException{
        ConfigState.appletType = appletType; // Set the applet type
        ConfigState.hostName = InetAddress.getLocalHost().getHostName();
        ConfigState.hostAddress = InetAddress.getLocalHost().getHostAddress();

        /* Parse the enviroment variables */
        ConfigState.baseAppletExcutablePath = args[ConfigState.staticApplicationCodeArgumentIndex];
        ConfigState.baseConfigPath = args[ConfigState.staticApplicationConfigArgumentIndex];

        ConfigState.baseArtifactDataPath = Paths.get(
            ConfigState.baseVirtualDataPath, ConfigState.baseArtifactDataPath
        ).toString();
        // ConfigState.kvStoreDbPathDir = Paths.get(
        //     ConfigState.baseVirtualDataPath, ConfigState.kvStoreDbPathDir
        // ).toString();
        // ConfigState.kvStoreDbPath = Paths.get(
        //     ConfigState.baseVirtualDataPath, ConfigState.kvStoreDbPath
        // ).toString();
        ConfigState.serverAgentDataPath = Paths.get(
            ConfigState.baseVirtualDataPath, ConfigState.serverAgentDataPath
        ).toString();
        ConfigState.kvStoreWalPath = Paths.get(
            ConfigState.baseVirtualDataPath, ConfigState.kvStoreWalPath
        ).toString();
        ConfigState.kvStoreDBWalShippingInboundPath = Paths.get(
            ConfigState.baseVirtualDataPath, ConfigState.kvStoreDBWalShippingInboundPath
        ).toString();

        /* Set the applet execution directories */
        /* Points to the folder /apps */
        ConfigState.proxyServerExecutionPath = Paths.get(
            ConfigState.baseAppletExcutablePath, ConfigState.proxyServerExecutionPath
        ).toString();

        ConfigState.KVStoreServerExecutionPath = Paths.get(
            ConfigState.baseAppletExcutablePath, ConfigState.KVStoreServerExecutionPath
        ).toString();

        ConfigState.agentServerExecutionPath = Paths.get(
            ConfigState.baseAppletExcutablePath, ConfigState.agentServerExecutionPath
        ).toString();

        ConfigState.serverOrchestratorExecutionPath = Paths.get(
            ConfigState.baseAppletExcutablePath, ConfigState.serverOrchestratorExecutionPath
        ).toString();

        ConfigState.testDevelopmentAgentExecutionPath = Paths.get(
            ConfigState.baseAppletExcutablePath, ConfigState.testDevelopmentAgentExecutionPath
        ).toString();

        ConfigState.seedConfigPath = Paths.get(
            ConfigState.baseConfigPath, ConfigState.seedConfigPath
        ).toString();

    }

    public static int getP2PPort() {
        switch (appletType) {
            case KVSTORE:
                return kvStorePeer2PeerInternalFacingPort;
            case LOADBALANCER:
                return loadBalancerPeer2PeerInternalFacingPort;
            case MONITOR:
                return monitorPeer2PeerInternalFacingPort;
            default:
                throw new IllegalArgumentException("Unknown applet type: " + appletType);
        }
    }
    
    public static int getPeer2PeerInternalWebWorkerCount() {
        switch (appletType) {
            case KVSTORE:
                return kvStorePeer2PeerInternalWebWorkerCount;
            case LOADBALANCER:
                return loadBalancerPeer2PeerInternalWebWorkerCount;
            case MONITOR:
                return monitorPeer2PeerInternalWebWorkerCount;
            default:
                return 1; // Default worker thread count
        }
    }
    
    /*
     * for now just let it be 5 seconds since we running out of time.
     */
    public static int getSyncIntervalSeconds() {
        return 5;
    }

    public static int getDBSyncIntervalSeconds() {
        return 10;
    }

}
