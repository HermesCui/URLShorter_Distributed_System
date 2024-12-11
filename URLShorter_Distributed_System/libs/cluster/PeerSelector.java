package libs.cluster;

import libs.config.AppletType;
import libs.msg.ClusterState;

import java.util.*;
import java.util.stream.Collectors;

public class PeerSelector {

    private static final Random RANDOM = new Random();

    public static List<PeerInfo> selectPeersForGossip(ClusterState state) {
        Set<PeerInfo> selectedPeers = new HashSet<>();

        selectedPeers.addAll(selectRandomPeersFromMap(state.monitorHostHeartBeat.keySet(), AppletType.MONITOR, state.currentHost));
        selectedPeers.addAll(selectRandomPeersFromMap(state.kvStoreHostHeartBeat.keySet(), AppletType.KVSTORE, state.currentHost));
        selectedPeers.addAll(selectRandomPeersFromMap(state.loadBalancerHostHeartBeat.keySet(), AppletType.LOADBALANCER, state.currentHost));

        return new ArrayList<>(selectedPeers);
    }

    private static Set<PeerInfo> selectRandomPeersFromMap(Set<String> hosts, AppletType appletType, String currentHost) {
        if (hosts == null || hosts.isEmpty()) {
            return Collections.emptySet();
        }

        List<String> availablePeers = hosts.stream()
                .filter(host -> !host.equals(currentHost))
                .collect(Collectors.toList());

        if (availablePeers.isEmpty()) {
            return Collections.emptySet();
        }

        int numberOfPeersToSelect = RANDOM.nextInt(3) + 1;
        if (availablePeers.size() <= numberOfPeersToSelect) {
            return availablePeers.stream()
                    .map(host -> new PeerInfo(host, appletType))
                    .collect(Collectors.toSet());
        }

        Set<PeerInfo> selectedPeers = new HashSet<>();
        while (selectedPeers.size() < numberOfPeersToSelect) {
            int index = RANDOM.nextInt(availablePeers.size());
            String selectedHost = availablePeers.get(index);
            selectedPeers.add(new PeerInfo(selectedHost, appletType));
        }

        return selectedPeers;
    }
}
