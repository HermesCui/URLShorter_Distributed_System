import libs.p2p.P2PClusterSyncRunnable;
import libs.p2p.P2PServerRunnable;
import libs.config.AppletType;
import libs.config.ConfigState;
import libs.msg.ClusterState;
import libs.data.ClusterStateLoader;
import libs.com.HostResolver;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class LoadBalancerServer {

    /* Create executor services for HTTP servers */
    private static final ExecutorService publicClientHttpServerExecutorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LoadBalancerPublicClientServer");
        return thread;
    });

    private static final ExecutorService internalPeertoPeerHttpServerExecutorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LoadBalancerP2PInternalCommunicationServer");
        return thread;
    });

    public static void main(String[] args) {
        try {
            // Set the applet type for this application instance
            AppletType appletType = AppletType.LOADBALANCER;

            // Initialize the ConfigState with applet type
            ConfigState.parseEnviromentVariables(args, appletType);
        } catch (UnknownHostException e) {
            System.err.println("LoadBalancer: Error occurred during environment variable parsing.");
            e.printStackTrace();
            return;
        }

        bootstrapper();
    }

    public static void bootstrapper() {
        /*
         * Inject bootloader code here.
         * Database sanity checks
         * WAL LOG fixing,
         * replication pulling before coming online.
         */
        entry();

        // Keep the main thread alive to prevent the application from exiting
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            System.err.println("LoadBalancer: Main thread interrupted.");
            e.printStackTrace();
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }

    public static void entry() {
        Map<String, Integer> monitorHeartBeat = ClusterStateLoader.loadSeedMonitorHeartBeat();
        Map<String, Integer> kvStoreHeartBeat = ClusterStateLoader.loadSeedKvStoreHeartBeat();
        Map<String, Integer> loadBalancerHeartBeat = ClusterStateLoader.loadSeedLoadBalancerHeartBeat();

        String currentHost = HostResolver.getCurrentHostName();
        
        ClusterState initialClusterState = new ClusterState(
            monitorHeartBeat,
            kvStoreHeartBeat,
            loadBalancerHeartBeat,
            new HashSet<String>(),
            currentHost,
            ConfigState.appletType,
            new HashMap<String, Integer>() 
        );

        // Initialize the AtomicReference with the initial state
        AtomicReference<ClusterState> clusterStateRef = new AtomicReference<>(initialClusterState);

        // Start the public client HTTP server
        LoadBalancerRunnable loadBalancerRunnable = new LoadBalancerRunnable(clusterStateRef);
        publicClientHttpServerExecutorService.submit(loadBalancerRunnable);

        // Start the P2P server
        int p2pPort = ConfigState.getP2PPort();
        int workerThreadCount = ConfigState.getPeer2PeerInternalWebWorkerCount();
        P2PServerRunnable p2pServerRunnable = new P2PServerRunnable(p2pPort, workerThreadCount, clusterStateRef, ConfigState.appletType);
        internalPeertoPeerHttpServerExecutorService.submit(p2pServerRunnable);

        // Start the P2P cluster synchronization task
        P2PClusterSyncRunnable syncRunnable = new P2PClusterSyncRunnable(clusterStateRef);
        ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "P2PClusterSyncRunnable");
            return thread;
        });
        int syncIntervalSeconds = ConfigState.getSyncIntervalSeconds();
        syncExecutor.scheduleAtFixedRate(syncRunnable, 0, syncIntervalSeconds, TimeUnit.SECONDS);

        System.out.println("LoadBalancer: Entry setup completed for applet: " + ConfigState.appletType);
    }
}
