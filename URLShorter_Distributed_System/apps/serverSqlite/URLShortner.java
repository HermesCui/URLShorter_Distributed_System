import libs.p2p.P2PClusterSyncRunnable;
import libs.p2p.P2PServerRunnable;
import libs.p2p.SyncDbRunnable;
import libs.com.ConsistentHashing;
import libs.com.HostResolver;
import libs.config.AppletType;
import libs.config.ConfigState;
import libs.data.ClusterStateLoader;
import libs.data.StorageDataUtil;
import libs.msg.ClusterState;
import java.util.*;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import libs.syncdb.*;

public class URLShortner {

    /* Create executor services for HTTP servers */
    private static final ExecutorService publicClientHttpServerExecutorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KVStorePublicClientServer");
        return thread;
    });

    private static final ExecutorService internalPeertoPeerHttpServerExecutorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KVStoreP2PInternalCommunicationServer");
        return thread;
    });

    public static void main(String[] args) {
        try {
            // Set the applet type for this application instance
            AppletType appletType = AppletType.KVSTORE;
            // Initialize the ConfigState with applet type
            ConfigState.parseEnviromentVariables(args, appletType);
            StorageDataUtil.validateDirectory(ConfigState.appletLogBasePath);
            StorageDataUtil.validateDirectory(ConfigState.kvStoreDbPathDir);

            System.out.println(ConfigState.appletLogBasePath);
            System.out.println(ConfigState.kvStoreDbPathDir);
            System.out.println(ConfigState.kvStoreDbPath);
            // PrintStream fileStream = new PrintStream(ConfigState.appletLogBasePath);
            // System.setOut(fileStream);
            // System.setErr(fileStream);

        } catch (UnknownHostException e) {
            System.err.println("URLShortner: Error occurred during environment variable parsing.");
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
            System.err.println("URLShortner: Main thread interrupted.");
            e.printStackTrace();
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }

    /**
     * Upon system entry we run the following services:
     * 1. Public facing client HTTP server.
     * 2. Internal facing service for communicating between different services.
     * 3. Start the P2P cluster synchronization task.
     */
    public static void entry() {
    // Load seed heartbeat data for each applet type
    ConsistentHashing hash = ConsistentHashing.getInstance();
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
        new HashMap<String, Integer>()  // visualizer sus node
    );

    AtomicReference<ClusterState> clusterStateRef = new AtomicReference<>(initialClusterState);

    KVServerRunnable kvServerRunnable = new KVServerRunnable();
    publicClientHttpServerExecutorService.submit(kvServerRunnable);

    int p2pPort = ConfigState.kvStorePeer2PeerInternalFacingPort;
    int workerThreadCount = ConfigState.getPeer2PeerInternalWebWorkerCount();
    P2PServerRunnable p2pServerRunnable = new P2PServerRunnable(p2pPort, workerThreadCount, clusterStateRef, ConfigState.appletType);
    internalPeertoPeerHttpServerExecutorService.submit(p2pServerRunnable);

    P2PClusterSyncRunnable syncRunnable = new P2PClusterSyncRunnable(clusterStateRef);
    ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "P2PClusterSyncRunnable");
        return thread;
    });
    int syncIntervalSeconds = ConfigState.getSyncIntervalSeconds();
    syncExecutor.scheduleAtFixedRate(syncRunnable, 0, syncIntervalSeconds, TimeUnit.SECONDS);


    //Create runnable for sync db content.
    SyncDbRunnable databaseSyncRunnable = new SyncDbRunnable();
    ScheduledExecutorService databaseSyncExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "P2PClusterSyncRunnable");
        return thread;
    });
    syncExecutor.scheduleAtFixedRate(databaseSyncRunnable, 0, ConfigState.getSyncIntervalSeconds(), TimeUnit.SECONDS);

    System.out.println("URLShortner: Entry setup completed for applet: " + ConfigState.appletType);
    }
}
