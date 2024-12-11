package libs.data;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import libs.config.ConfigState;

/*
 * A persistent object store to 
 * read / offload certain configurations that 
 * some subsystems require. 
 * 
 * ie: Agent Data for knowing what applications are to 
 * be hosted on this instance.
 */

public class ArtifactLoader 
{
    /*
     * Stores the two pairs of files in the specificed dataPath.
     * 1. The desearlized object stored in path
     * 2. Checksum file /path.ck
     */
    private static ConfigState configState = new ConfigState();

    /*
     * If an existing object exists, then its overwritten.
     */
    public static boolean artifactSerialize(String name, Object artifact)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        String artifactDataPath = Paths.get(ConfigState.baseArtifactDataPath,"/"+ name).toString();
        String checksumPath =  Paths.get(ConfigState.baseArtifactDataPath,"/"+ name + ".ck").toString();

        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(artifact);
            oos.flush(); 
            byte[] serializedObjectBytes = bos.toByteArray();
            String checksum = createArtifactChecksum(serializedObjectBytes);

            try (FileOutputStream fos = new FileOutputStream(artifactDataPath, false)) {
                fos.write(serializedObjectBytes);
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(checksumPath))) {
                writer.write(checksum);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Data has failed to be searlized");
            return false;
        }
        return true;
    }

    /*
     * Returns null if the file does not exist or is corrupted.
     * Otherwise returns the Object stored.
     */
    public static Object artifactDeserialize(String name)
    {
        byte[] serializedObjectBytes;
        String expectedChecksum;
        String actualChecksum;
        boolean isValid = false;
        Object artifact = null;
        String artifactDataPath = Paths.get(ConfigState.baseArtifactDataPath,"/"+ name).toString();
        String checksumPath =  Paths.get(ConfigState.baseArtifactDataPath,"/"+ name + ".ck").toString();

        validateArtifactDirectory();

        try {
            serializedObjectBytes = Files.readAllBytes(new File(artifactDataPath).toPath());
            expectedChecksum = new String(Files.readAllBytes(new File(checksumPath).toPath())).trim();
            actualChecksum = createArtifactChecksum(serializedObjectBytes);
            isValid = expectedChecksum.equals(actualChecksum);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedObjectBytes)){
                ObjectInputStream ois = new ObjectInputStream(bis);
                artifact = ois.readObject();
            }
        } catch (IOException e) {
            System.err.println("Error reading serialized object file: " + e.getMessage());
            isValid = false;
        } catch (ClassNotFoundException e){
            System.out.println("Class not found.. Did you compile the app with necessary files?");
            isValid = false;
        }

        return isValid ? artifact : null;
    }


    public static String createArtifactChecksum(byte[] byteStream)
    {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "FAILED_TO_GENERATE_HASH_STREAM";
        }

        byte[] hashBytes = digest.digest(byteStream);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static void validateArtifactDirectory(){
        File artifactFile = new File(ConfigState.baseArtifactDataPath);
        File parentDir = artifactFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs(); 
        }
    }
}
