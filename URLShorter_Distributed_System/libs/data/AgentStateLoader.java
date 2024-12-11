package libs.data;

import libs.config.*;
import libs.msg.AgentState;
import libs.com.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;

public class AgentStateLoader {
    

    private static ConfigState config = new ConfigState();
    private static AgentState agentState = new AgentState();

    /*
     * Loads the agent state through the seed config.
     * Returns true if the agentData has been populated
     * via identified means.
     * 
     * Otherwise the agent will use application defaults.
     * Unless a message is recieved to change state.
     */
    public static boolean loadAgentData(){
        loadAgentDataFromConfig();
        return true;
    }

    /*
     * Loads an agent data from config.
     */
    public static AgentState loadAgentDataFromConfig(){
        String seedFilePath = config.seedConfigPath;
        String currentHostName = HostResolver.getCurrentHostName();

        try (BufferedReader br = new BufferedReader(new FileReader(seedFilePath))) {
            String line;
            String[] parts;
            while ((line = br.readLine()) != null) {
                parts = line.trim().split("\\s+");
                if(parts[0].equals(currentHostName)){
                    setAgentDataState(parts);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return agentState;
    }

    /*
     * Loads an agent data from disk if not present.
     */
    public static synchronized boolean loadAgentDataFromDisk(){
        return true;
    }


     /*
     * Load agent from data montior
     */
    public static synchronized boolean loadAgentDataFromPeers(){
        return true;
    }

    /*
     * Load agent from data montior
     */
    public static synchronized boolean loadAgentDataFromMonitor(){
        return true;
    }


    public static boolean setAgentDataState(String[] parts) {
        // Check the services associated with the host
        for (int i = 1; i < parts.length; i++) {
            String service = parts[i];
            agentState.offerList.add(service);
            switch (service) {
                case "LoadBalancerServer":
                    agentState.loadBalancerServiceOffered = true;
                    break;
                case "URLShortner":
                    agentState.kvStoreServiceOffered = true;
                    break;
                case "MonitorServer":
                    agentState.monitorServiceOffered = true;
                    break;
                default:
                    System.err.println("Unknown service: " + service);
                    break;
            }
        }
        return true;
    }
}

