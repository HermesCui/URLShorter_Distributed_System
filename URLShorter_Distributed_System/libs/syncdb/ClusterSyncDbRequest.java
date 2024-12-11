package libs.syncdb;

import libs.com.SystemMessageSender;
import libs.config.ConfigState;
import libs.msg.ClusterSyncDBMessage;
import libs.msg.MessageState;
import java.util.ArrayList;
import java.util.List;


public class ClusterSyncDbRequest {
    /*
     * Sends a request of all rows that this host should have.
     * The replica is an arbitary selected random node. 
     * replicaStart and replicaEnd is the range of keys to look for.
    //  */
    // public ArrayList<String> fetchDataFromReplica(String replicaHostName, String replicaStart, String replicaEnd, int cursor) {
    //     ClusterSyncDBMessage fetchMsgPayload = new ClusterSyncDBMessage();
    //     fetchMsgPayload.rowData = new ArrayList<>();
    //     fetchMsgPayload.hasData = true;
    //     fetchMsgPayload.hostStart = replicaStart;
    //     fetchMsgPayload.hostEnd = replicaEnd;
    //     fetchMsgPayload.window = 1000; //fetch 1000 rows.
    //     fetchMsgPayload.curr = cursor;

    //     //Date of the transaction.
    //     fetchMsgPayload.epocDateAsOf = (int) (System.currentTimeMillis() / 1000);

    //     // Wrap the payload in a MessageState
    //     MessageState fetchMsg = new MessageState(
    //             MessageState.MsgCtx.CLUSTER_SYNC_READ_REQUEST,
    //             ConfigState.hostName, 
    //             replicaHostName,  
    //             fetchMsgPayload
    //     );

    //     // Send the fetch request to the replica
    //     MessageState response = SystemMessageSender.issueRequest(
    //             replicaHostName,
    //             ConfigState.kvStorePeer2PeerInternalFacingPort,
    //             fetchMsg,
    //             "retrieve",
    //             "GET"
    //     );

    //     ArrayList<String> fetchedRows = new ArrayList<>();
    //     if (response != null && response.hasPayload && response.payloadData instanceof ClusterSyncDBMessage) {
    //         ClusterSyncDBMessage responsePayload = (ClusterSyncDBMessage) response.payloadData;
    //         fetchedRows.addAll(responsePayload.rowData);
    //         System.out.println("MigrationTask: Successfully fetched " + fetchedRows.size() + " rows from " + replicaHostName);
    //     } else {
    //         System.err.println("MigrationTask: Failed to fetch data from " + replicaHostName);
    //     }
    //     return fetchedRows;
    // }

    public static void todo(){
        return;
    }
}
