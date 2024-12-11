package libs.data;

import libs.com.HostResolver;
import libs.config.AppletType;
import libs.config.ConfigState;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class ClusterStateLoader {

    /**
     * Loads the seed heartbeat map for Monitor hosts.
     */
    public static Map<String, Integer> loadSeedMonitorHeartBeat() {
        return loadSeedHeartBeatForAppletType(AppletType.MONITOR);
    }

    /**
     * Loads the seed heartbeat map for KVStore hosts.
     */
    public static Map<String, Integer> loadSeedKvStoreHeartBeat() {
        return loadSeedHeartBeatForAppletType(AppletType.KVSTORE);
    }

    /**
     * Loads the seed heartbeat map for LoadBalancer hosts.
     */
    public static Map<String, Integer> loadSeedLoadBalancerHeartBeat() {
        return loadSeedHeartBeatForAppletType(AppletType.LOADBALANCER);
    }

    private static Map<String, Integer> loadSeedHeartBeatForAppletType(AppletType appletType) {
        String seedFilePath = ConfigState.seedConfigPath;
        String currentHostName = HostResolver.getCurrentHostName();

        Map<String, Integer> heartBeatMap = new HashMap<>();
        int currentTime = (int) (System.currentTimeMillis() / 1000);

        try (BufferedReader br = new BufferedReader(new FileReader(seedFilePath))) {
            String line;
            String[] parts;
            while ((line = br.readLine()) != null) {
                parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue; // Skip invalid lines
                }

                String host = parts[0];

                // Skip the current host
                if (host.equals(currentHostName)) {
                    continue;
                }

                Set<AppletType> appletTypes = parseAppletTypes(Arrays.copyOfRange(parts, 1, parts.length));
                if (appletTypes.contains(appletType)) {
                    heartBeatMap.put(host, currentTime);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading seed file: " + seedFilePath);
            e.printStackTrace();
        }

        return heartBeatMap;
    }

    private static Set<AppletType> parseAppletTypes(String[] services) {
        Set<AppletType> appletTypes = new HashSet<>();
        for (String service : services) {
            switch (service) {
                case "LoadBalancerServer":
                    appletTypes.add(AppletType.LOADBALANCER);
                    break;
                case "URLShortner":
                    appletTypes.add(AppletType.KVSTORE);
                    break;
                case "MonitorServer":
                    appletTypes.add(AppletType.MONITOR);
                    break;
                default:
                    System.err.println("Unknown service: " + service);
                    break;
            }
        }
        return appletTypes;
    }
}
