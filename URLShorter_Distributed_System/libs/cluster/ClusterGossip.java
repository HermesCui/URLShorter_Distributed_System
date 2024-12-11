package libs.cluster;

import libs.config.AppletType;
import libs.config.ConfigState;

import libs.data.ClusterStateLoader;
import libs.msg.ClusterState;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ClusterGossip {


    public static void mergeClusterGossip(AtomicReference<ClusterState> hostStateRef, ClusterState peerState) {
        ClusterState updatedHostState;
        ClusterState currentHostState;
        do {
            currentHostState = hostStateRef.get();
            updatedHostState = currentHostState.mergeWith(peerState);

            // Remove any nodes from anomalyNode if their heartbeat is updated
            Map<String, Integer> updatedAnomalyNode = new HashMap<>(updatedHostState.anomalyNode);
            for (String host : peerState.monitorHostHeartBeat.keySet()) {
                if (peerState.monitorHostHeartBeat.get(host) != null) {
                    updatedAnomalyNode.remove(host);
                }
            }
            for (String host : peerState.kvStoreHostHeartBeat.keySet()) {
                if (peerState.kvStoreHostHeartBeat.get(host) != null) {
                    updatedAnomalyNode.remove(host);
                }
            }
            for (String host : peerState.loadBalancerHostHeartBeat.keySet()) {
                if (peerState.loadBalancerHostHeartBeat.get(host) != null) {
                    updatedAnomalyNode.remove(host);
                }
            }

            // Create a new ClusterState with updated anomalyNode
            updatedHostState = new ClusterState(
                    updatedHostState.monitorHostHeartBeat,
                    updatedHostState.kvStoreHostHeartBeat,
                    updatedHostState.loadBalancerHostHeartBeat,
                    updatedHostState.offlineHosts,
                    updatedHostState.currentHost,
                    updatedHostState.currentHostAppletType,
                    updatedAnomalyNode
            );

        } while (!hostStateRef.compareAndSet(currentHostState, updatedHostState));
    }

   
    /**
     * Add a node to anomalyNode due to unresponsiveness.
     */
    public static void addAnomalyNode(AtomicReference<ClusterState> clusterStateRef, String peerHost) {
        ClusterState updatedHostState;
        ClusterState currentHostState;
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        do {
            currentHostState = clusterStateRef.get();

            // Create mutable copy of anomalyNode
            Map<String, Integer> anomalyNode = new HashMap<>(currentHostState.anomalyNode);

            // Add or update the anomaly entry with the current timestamp
            anomalyNode.put(peerHost, (int) currentTimeSeconds);

            // Create new ClusterState
            updatedHostState = new ClusterState(
                    currentHostState.monitorHostHeartBeat,
                    currentHostState.kvStoreHostHeartBeat,
                    currentHostState.loadBalancerHostHeartBeat,
                    currentHostState.offlineHosts,
                    currentHostState.currentHost,
                    currentHostState.currentHostAppletType,
                    anomalyNode
            );
        } while (!clusterStateRef.compareAndSet(currentHostState, updatedHostState));
    }

    /**
     * Remove a node from anomalyNode if it becomes responsive.
     */
    public static void removeAnomalyNode(AtomicReference<ClusterState> clusterStateRef, String peerHost) {
        ClusterState updatedHostState;
        ClusterState currentHostState;
        do {
            currentHostState = clusterStateRef.get();

            // Create mutable copy of anomalyNode
            Map<String, Integer> anomalyNode = new HashMap<>(currentHostState.anomalyNode);

            // Remove the node from anomalyNode
            if (anomalyNode.remove(peerHost) == null) {
                return;
            }
            updatedHostState = new ClusterState(
                    currentHostState.monitorHostHeartBeat,
                    currentHostState.kvStoreHostHeartBeat,
                    currentHostState.loadBalancerHostHeartBeat,
                    currentHostState.offlineHosts,
                    currentHostState.currentHost,
                    currentHostState.currentHostAppletType,
                    anomalyNode
            );
        } while (!clusterStateRef.compareAndSet(currentHostState, updatedHostState));
    }


    public static void recomputeOfflineHosts(AtomicReference<ClusterState> stateRef) {
        ClusterState updatedState;
        ClusterState currentState;
        do {
            currentState = stateRef.get();
            updatedState = recomputeOfflineHostsAndRemoveStaleHosts(currentState);
        } while (!stateRef.compareAndSet(currentState, updatedState));
    }

    private static ClusterState recomputeOfflineHostsAndRemoveStaleHosts(ClusterState currentState) {
        long currentTime = System.currentTimeMillis() / 1000;
        int hostRemovalTimeout = ConfigState.hostRemovalTimeout; // Timeout in seconds

        // Create mutable copies of the heartbeat maps
        Map<String, Integer> monitorHeartBeat = new HashMap<>(currentState.monitorHostHeartBeat);
        Map<String, Integer> kvStoreHeartBeat = new HashMap<>(currentState.kvStoreHostHeartBeat);
        Map<String, Integer> loadBalancerHeartBeat = new HashMap<>(currentState.loadBalancerHostHeartBeat);
        Set<String> offlineHosts = new HashSet<>(currentState.offlineHosts);
        Map<String, Integer> anomalyNode = new HashMap<>(currentState.anomalyNode);

        // Set to collect offline hosts
        Set<String> newlyOfflineHosts = new HashSet<>();

        // Process and clean heartbeat maps
        newlyOfflineHosts.addAll(processAndCleanHeartbeatMap(monitorHeartBeat, currentTime, hostRemovalTimeout, anomalyNode));
        newlyOfflineHosts.addAll(processAndCleanHeartbeatMap(kvStoreHeartBeat, currentTime, hostRemovalTimeout, anomalyNode));
        newlyOfflineHosts.addAll(processAndCleanHeartbeatMap(loadBalancerHeartBeat, currentTime, hostRemovalTimeout, anomalyNode));

        // Update offlineHosts
        offlineHosts.addAll(newlyOfflineHosts);

        // Check if any heartbeat map is empty and reload it with seed data if needed
        boolean seedReloaded = reloadSeedDataIfNeeded(monitorHeartBeat, kvStoreHeartBeat, loadBalancerHeartBeat);

        if (seedReloaded) {
            System.out.println("[ClusterGossip] Heartbeat map was empty after cleanup. Reloaded seed data.");
        }

        // Return a new ClusterState with updated data
        return new ClusterState(
                monitorHeartBeat,
                kvStoreHeartBeat,
                loadBalancerHeartBeat,
                offlineHosts,
                currentState.currentHost,
                currentState.currentHostAppletType,
                anomalyNode
        );
    }

    private static Set<String> processAndCleanHeartbeatMap(Map<String, Integer> heartbeatMap, long currentTime, int hostRemovalTimeout, Map<String, Integer> anomalyNode) {
        Iterator<Map.Entry<String, Integer>> iterator = heartbeatMap.entrySet().iterator();
        Set<String> offlineHosts = new HashSet<>();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            String host = entry.getKey();
            int lastHeartbeat = entry.getValue();
            if (currentTime - lastHeartbeat > hostRemovalTimeout) {
                offlineHosts.add(host);
                iterator.remove(); // Remove host from heartbeat map
                System.out.println("[ClusterGossip] Host '" + host + "' has been offline for too long. Removing from cluster state.");

                // Add to anomalyNode as the node is unresponsive
                anomalyNode.put(host, (int) currentTime);
                System.out.println("[ClusterGossip] Host '" + host + "' added to AnomalyNode due to unresponsiveness.");
            }
        }
        return offlineHosts;
    }

    private static boolean reloadSeedDataIfNeeded(Map<String, Integer> monitorHeartBeat, Map<String, Integer> kvStoreHeartBeat, Map<String, Integer> loadBalancerHeartBeat) {
        boolean seedReloaded = false;
        if (monitorHeartBeat.isEmpty()) {
            monitorHeartBeat.putAll(ClusterStateLoader.loadSeedMonitorHeartBeat());
            seedReloaded = true;
        }
        if (kvStoreHeartBeat.isEmpty()) {
            kvStoreHeartBeat.putAll(ClusterStateLoader.loadSeedKvStoreHeartBeat());
            seedReloaded = true;
        }
        if (loadBalancerHeartBeat.isEmpty()) {
            loadBalancerHeartBeat.putAll(ClusterStateLoader.loadSeedLoadBalancerHeartBeat());
            seedReloaded = true;
        }
        return seedReloaded;
    }
    public static void updateHeartbeat(AtomicReference<ClusterState> clusterStateRef, String peerHost, AppletType peerAppletType) {
        ClusterState updatedHostState;
        ClusterState currentHostState;
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        do {
            currentHostState = clusterStateRef.get();

            // Create mutable copies of the heartbeat maps
            Map<String, Integer> monitorHeartBeat = new HashMap<>(currentHostState.monitorHostHeartBeat);
            Map<String, Integer> kvStoreHeartBeat = new HashMap<>(currentHostState.kvStoreHostHeartBeat);
            Map<String, Integer> loadBalancerHeartBeat = new HashMap<>(currentHostState.loadBalancerHostHeartBeat);
            Set<String> offlineHosts = new HashSet<>(currentHostState.offlineHosts);
            Map<String, Integer> anomalyNode = new HashMap<>(currentHostState.anomalyNode);

            // Update the appropriate heartbeat map
            switch (peerAppletType) {
                case MONITOR:
                    monitorHeartBeat.put(peerHost, (int) currentTimeSeconds);
                    break;
                case KVSTORE:
                    kvStoreHeartBeat.put(peerHost, (int) currentTimeSeconds);
                    break;
                case LOADBALANCER:
                    loadBalancerHeartBeat.put(peerHost, (int) currentTimeSeconds);
                    break;
                default:
                    // Handle unknown applet types if necessary
                    break;
            }

            // Remove from offline hosts if present
            offlineHosts.remove(peerHost);

            // Remove from anomalyNode as the node has responded
            anomalyNode.remove(peerHost);

            // Create new ClusterState
            updatedHostState = new ClusterState(
                    monitorHeartBeat,
                    kvStoreHeartBeat,
                    loadBalancerHeartBeat,
                    offlineHosts,
                    currentHostState.currentHost,
                    currentHostState.currentHostAppletType,
                    anomalyNode
            );
        } while (!clusterStateRef.compareAndSet(currentHostState, updatedHostState));
    }
}
