package libs.data;

import java.io.File;

import libs.config.ConfigState;

public class StorageDataUtil {
        public static void validateDirectory(String directoryPath){
            File dir = new File(directoryPath);
            if(!dir.exists()){
                dir.mkdirs(); 
            }
        }
}
