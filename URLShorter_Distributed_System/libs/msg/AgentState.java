package libs.msg;

import java.util.ArrayList;

public class AgentState {
    public static boolean kvStoreServiceOffered = true; //Set to default.
    public static boolean loadBalancerServiceOffered = false;
    public static boolean monitorServiceOffered = false;


    public static ArrayList<String> offerList = new ArrayList<String>();

    public static String getAgentState() {
        return "AgentState {" +
                "KV Store Service Offered: " + kvStoreServiceOffered +
                ", Load Balancer Service Offered: " + loadBalancerServiceOffered +
                ", Monitor Service Offered: " + monitorServiceOffered +
                '}';
    }
}
