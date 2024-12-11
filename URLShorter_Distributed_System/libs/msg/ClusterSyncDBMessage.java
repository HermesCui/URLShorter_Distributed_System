package libs.msg;

import libs.config.ConfigState;
import java.io.Serializable;
import java.util.*;
import java.sql.Timestamp;

public class ClusterSyncDBMessage implements Serializable{
    
    //The start and end range on the consistent hash.
    //Keys in this range get mapped to hostEnd
    public String hostStart;
    public String hostEnd; 

    public int window; /*The number of keys to send over*/
    public int curr; /*The current offset multipler. window*curr = rows to start reading from*/
    public Timestamp epocDateAsOf; /*We are interested only in the set of keys that are before this date*/
    /*Array of rowData of size less than or equal to window */
    public boolean hasData;
    // array of rows, where each row is comma seperated.
    public ArrayList<String> rowData;
}
