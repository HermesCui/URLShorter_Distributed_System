package libs.p2p;

import libs.cluster.ClusterGossip;
import libs.cluster.PeerInfo;
import libs.cluster.PeerSelector;
import libs.com.ConsistentHashing;
import libs.com.SystemMessageSender;
import libs.config.AppletType;
import libs.config.ConfigState;
import libs.msg.ClusterState;
import libs.msg.MessageState;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Map.Entry;

public class P2PClusterSyncRunnable implements Runnable {

    private final AtomicReference<ClusterState> clusterStateRef;
    private final ConsistentHashing consistentHashing;

    public P2PClusterSyncRunnable(AtomicReference<ClusterState> clusterStateRef) {
        this.clusterStateRef = clusterStateRef;
        this.consistentHashing = ConsistentHashing.getInstance();
    }

    @Override
    public void run() {
        try {
            ClusterState currentState = clusterStateRef.get();

            List<PeerInfo> peersToGossip = PeerSelector.selectPeersForGossip(currentState);
            if (peersToGossip.isEmpty()) {
                System.out.println("[P2PClusterSyncRunnable] No peers available for gossip.");
                return;
            }

            for (PeerInfo peerInfo : peersToGossip) {
                String peerHost = peerInfo.hostName;
                AppletType peerAppletType = peerInfo.appletType;

                int peerP2PPort = getP2PPortForAppletType(peerAppletType);

                try {
                    MessageState gossipRequest = new MessageState(
                            MessageState.MsgCtx.CLUSTER_GOSSIP_REQUEST,
                            currentState.currentHost,
                            peerHost,
                            currentState
                    );

                    MessageState gossipResponse = SystemMessageSender.issueRequest(
                            peerHost,
                            peerP2PPort,
                            gossipRequest,
                            "gossip",
                            "PUT"
                    );

                    // If a valid response is received, merge the cluster states
                    if (gossipResponse != null && gossipResponse.payloadData instanceof ClusterState) {
                        ClusterState peerClusterState = (ClusterState) gossipResponse.payloadData;
                        ClusterGossip.mergeClusterGossip(clusterStateRef, peerClusterState);

                        ClusterGossip.updateHeartbeat(clusterStateRef, peerHost, peerAppletType);

                        ClusterGossip.removeAnomalyNode(clusterStateRef, peerHost);

                        System.out.println("[P2PClusterSyncRunnable] Successfully merged cluster state from peer: " + peerHost);
                    } else {
                        System.err.println("[P2PClusterSyncRunnable] No valid response from peer: " + peerHost);
                        ClusterGossip.addAnomalyNode(clusterStateRef, peerHost);
                    }
                } catch (Exception e) {
                    System.err.println("[P2PClusterSyncRunnable] Error communicating with peer: " + peerHost);
                    e.printStackTrace();
                    ClusterGossip.addAnomalyNode(clusterStateRef, peerHost);
                }
            }
            
            ClusterGossip.recomputeOfflineHosts(clusterStateRef);
            updateConsistentHashRing();
            printClusterState();

        } catch (Exception e) {
            System.err.println("[P2PClusterSyncRunnable] Error during cluster synchronization:");
            e.printStackTrace();
        }
    }

    private int getP2PPortForAppletType(AppletType appletType) {
        switch (appletType) {
            case KVSTORE:
                return ConfigState.kvStorePeer2PeerInternalFacingPort;
            case LOADBALANCER:
                return ConfigState.loadBalancerPeer2PeerInternalFacingPort;
            case MONITOR:
                return ConfigState.monitorPeer2PeerInternalFacingPort;
            default:
                throw new IllegalArgumentException("Unknown applet type: " + appletType);
        }
    }

    private void updateConsistentHashRing() {
        ClusterState state = clusterStateRef.get();
        consistentHashing.clear();
        for (String host : state.kvStoreHostHeartBeat.keySet()) {
            if (!state.offlineHosts.contains(host) && !state.anomalyNode.containsKey(host)) {
                consistentHashing.addNode(host);
                System.out.println("[P2PClusterSyncRunnable] Added host to hash ring: " + host);
            }
        }
    }

    /** Prints out the current state of the ClusterState, including all nodes and their heartbeat times. */
    private void printClusterState() {
        ClusterState state = clusterStateRef.get();
        System.out.println("[P2PClusterSyncRunnable] Current Cluster State:");

        System.out.println("KVStore Hosts:");
        for (Map.Entry<String, Integer> entry : state.kvStoreHostHeartBeat.entrySet()) {
            String status = state.offlineHosts.contains(entry.getKey()) ? "OFFLINE" :
                    (state.anomalyNode.containsKey(entry.getKey()) ? "ANOMALY" : "ONLINE");
            System.out.printf(" - %s: Heartbeat=%d, Status=%s%n", entry.getKey(), entry.getValue(), status);
        }

        System.out.println("LoadBalancer Hosts:");
        for (Map.Entry<String, Integer> entry : state.loadBalancerHostHeartBeat.entrySet()) {
            String status = state.offlineHosts.contains(entry.getKey()) ? "OFFLINE" :
                    (state.anomalyNode.containsKey(entry.getKey()) ? "ANOMALY" : "ONLINE");
            System.out.printf(" - %s: Heartbeat=%d, Status=%s%n", entry.getKey(), entry.getValue(), status);
        }

        System.out.println("Monitor Hosts:");
        for (Map.Entry<String, Integer> entry : state.monitorHostHeartBeat.entrySet()) {
            String status = state.offlineHosts.contains(entry.getKey()) ? "OFFLINE" :
                    (state.anomalyNode.containsKey(entry.getKey()) ? "ANOMALY" : "ONLINE");
            System.out.printf(" - %s: Heartbeat=%d, Status=%s%n", entry.getKey(), entry.getValue(), status);
        }

        System.out.println("Offline Hosts:");
        for (String host : state.offlineHosts) {
            System.out.println(" - " + host);
        }

        System.out.println("Anomaly Hosts:");
        for (Map.Entry<String, Integer> entry : state.anomalyNode.entrySet()) {
            System.out.printf(" - %s: Last Anomaly Timestamp=%d%n", entry.getKey(), entry.getValue());
        }
    }
}
