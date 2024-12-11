// libs/syncdb/ClusterSyncDb.java

package libs.p2p;

import libs.com.ConsistentHashing;
import libs.config.ConfigState;
import libs.msg.ClusterState;
import libs.msg.ClusterSyncDBMessage;
import libs.msg.MessageState;
import libs.com.SystemMessageSender;
import libs.syncdb.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs in the background finding and syncing itself with its clusters.
 */
public class SyncDbRunnable implements Runnable {

    private final ConsistentHashing consistentHashing;
    private final URLShortnerDB database;
    private final String localHostName;

    // Synchronization interval in seconds
    private final int syncIntervalSeconds;

    public SyncDbRunnable() {
        this.consistentHashing = ConsistentHashing.getInstance();
        this.database = URLShortnerDB.getInstance();
        this.localHostName = ConfigState.hostName; 
        this.syncIntervalSeconds = ConfigState.getSyncIntervalSeconds();
    }



    @Override
    public void run() {
        while (true) {
            try {
                fetchMissingRowDataFromReplicas();
                TimeUnit.SECONDS.sleep(syncIntervalSeconds);
            } catch (InterruptedException e) {
                System.err.println("ClusterSyncDb: Synchronization thread interrupted.");
                Thread.currentThread().interrupt();
                break; 
            } catch (Exception e) {
                System.err.println("ClusterSyncDb: Unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    private boolean fetchMissingRowDataFromReplicas() {
        try {
            Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
            Iterator<String> successorIterator = consistentHashing.getSuccessorNodes(localHostName);
            List<String> successors = new ArrayList<>();
            while (successorIterator.hasNext()) {
                String successor = successorIterator.next();
                if (!successor.equals(localHostName)) {
                    successors.add(successor);
                }
            }
            
            boolean error = false;
            String hostStart = consistentHashing.getPredecessorNodes(localHostName).next();
            String hostEnd = successors.getLast();
    

            for (String successorNode : successors) {
                int curr = 0;
                boolean hasMoreData = true;
                
                ClusterSyncDBMessage requestMsg = new ClusterSyncDBMessage();
                requestMsg.hostStart = hostStart;
                requestMsg.hostEnd = hostEnd;
                requestMsg.window = 200;
                requestMsg.curr = curr;
                requestMsg.epocDateAsOf = currentTime;
                requestMsg.hasData = false; 
                requestMsg.rowData = null;

                while (hasMoreData) {
 
                    MessageState msgState = new MessageState(
                            MessageState.MsgCtx.CLUSTER_SYNC_READ_REQUEST,
                            localHostName,
                            successorNode,
                            requestMsg
                    );
    
 
                    MessageState responseMsg = SystemMessageSender.issueRequest(successorNode, 
                    ConfigState.kvStorePeer2PeerInternalFacingPort, msgState, "db_read", "GET");
                    
                    if (responseMsg != null && responseMsg.payloadData instanceof ClusterSyncDBMessage) {
                        ClusterSyncDBMessage responsePayload = (ClusterSyncDBMessage) responseMsg.payloadData;
                        System.out.println("[SaveMode]Fetching message metadata: ");
                        if (responsePayload.rowData != null && !responsePayload.rowData.isEmpty()) {
                            boolean success = database.saveBatch(responsePayload.rowData);
                            if (!success) {
                                System.err.println("SyncDbRunnable: Failed to save data from successor node " + successorNode);
                                return false;
                            }
                            requestMsg.curr = requestMsg.curr +=1 ;
                        } else {
                            System.out.println("[SaveMode] No data is available..");
                            hasMoreData = false;
                        }
                    } else {
                        // Invalid response or error
                        System.err.println("SyncDbRunnable: Invalid response from successor node " + successorNode);
                        hasMoreData = false;
                        error = true;
                    }
                }
            }
            return error;
        } catch (Exception e) {
            System.err.println("SyncDbRunnable: Error fetching missing row data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    

}
