package libs.msg;

import libs.com.HostResolver;
import libs.config.AppletType;
import libs.config.ConfigState;

import java.io.Serializable;
import java.util.*;

public final class ClusterState implements Serializable {

    public final Map<String, Integer> monitorHostHeartBeat;
    public final Map<String, Integer> kvStoreHostHeartBeat;
    public final Map<String, Integer> loadBalancerHostHeartBeat;

    public final Set<String> offlineHosts;
    
    public final String currentHost;
    public final AppletType currentHostAppletType;

    public final Map<String, Integer> anomalyNode;

    public ClusterState(Map<String, Integer> monitorHeartBeat,
                        Map<String, Integer> kvStoreHeartBeat,
                        Map<String, Integer> loadBalancerHeartBeat,
                        Set<String> offlineHosts,
                        String currentHost,
                        AppletType currentHostAppletType,
                        Map<String, Integer> anomalyNode) {
        this.monitorHostHeartBeat = Collections.unmodifiableMap(monitorHeartBeat);
        this.kvStoreHostHeartBeat = Collections.unmodifiableMap(kvStoreHeartBeat);
        this.loadBalancerHostHeartBeat = Collections.unmodifiableMap(loadBalancerHeartBeat);
        this.offlineHosts = Collections.unmodifiableSet(offlineHosts);
        this.currentHost = currentHost;
        this.currentHostAppletType = currentHostAppletType;
        this.anomalyNode = Collections.unmodifiableMap(anomalyNode);
    }

    public static ClusterState initialState() {
        String currentHost = HostResolver.getCurrentHostName();
        return new ClusterState(
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashSet<>(),
                currentHost,
                ConfigState.appletType,
                new HashMap<>() 
        );
    }

    // Method to merge with another ClusterState
    public ClusterState mergeWith(ClusterState peerState) {
        Map<String, Integer> newMonitorHeartBeat = mergeHeartbeats(this.monitorHostHeartBeat, peerState.monitorHostHeartBeat);
        Map<String, Integer> newKvStoreHeartBeat = mergeHeartbeats(this.kvStoreHostHeartBeat, peerState.kvStoreHostHeartBeat);
        Map<String, Integer> newLoadBalancerHeartBeat = mergeHeartbeats(this.loadBalancerHostHeartBeat, peerState.loadBalancerHostHeartBeat);

        Map<String, Integer> newAnomalyNode = new HashMap<>(this.anomalyNode);
        newAnomalyNode.putAll(peerState.anomalyNode);

        // Offline hosts will be recomputed separately
        return new ClusterState(
                newMonitorHeartBeat,
                newKvStoreHeartBeat,
                newLoadBalancerHeartBeat,
                this.offlineHosts,
                this.currentHost,
                ConfigState.appletType,
                newAnomalyNode
        );
    }

    // Helper method to merge heartbeat maps
    private Map<String, Integer> mergeHeartbeats(Map<String, Integer> local, Map<String, Integer> peer) {
        Map<String, Integer> merged = new HashMap<>(local);
        for (Map.Entry<String, Integer> entry : peer.entrySet()) {
            String host = entry.getKey();
            if (host.equals(this.currentHost)) {
                continue; // Skip current host
            }
            merged.merge(host, entry.getValue(), Math::max);
        }
        return merged;
    }

    // Method to recompute offline hosts
    public ClusterState recomputeOfflineHosts() {
        Set<String> newOfflineHosts = new HashSet<>();
        long currentTime = System.currentTimeMillis() / 1000;
        int heartbeatThreshold = ConfigState.processHealthyCheckTimeOut;

        checkHeartbeat(this.monitorHostHeartBeat, currentTime, heartbeatThreshold, newOfflineHosts);
        checkHeartbeat(this.kvStoreHostHeartBeat, currentTime, heartbeatThreshold, newOfflineHosts);
        checkHeartbeat(this.loadBalancerHostHeartBeat, currentTime, heartbeatThreshold, newOfflineHosts);

        return new ClusterState(
                this.monitorHostHeartBeat,
                this.kvStoreHostHeartBeat,
                this.loadBalancerHostHeartBeat,
                newOfflineHosts,
                this.currentHost,
                ConfigState.appletType,
                this.anomalyNode 
        );
    }

    private void checkHeartbeat(Map<String, Integer> heartbeats, long currentTime, int threshold, Set<String> offlineHosts) {
        for (Map.Entry<String, Integer> entry : heartbeats.entrySet()) {
            String host = entry.getKey();
            if (host.equals(this.currentHost)) {
                continue;
            }
            int lastHeartbeatTime = entry.getValue();
            if (currentTime - lastHeartbeatTime > threshold) {
                offlineHosts.add(host);
            }
        }
    }
}
