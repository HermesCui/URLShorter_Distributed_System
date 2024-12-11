package libs.db;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.locks.ReentrantLock;

public class WalWriter {
    private final File logFile;
    private final ReentrantLock lock = new ReentrantLock();

    public WalWriter(String filePath) {
        this.logFile = new File(filePath);
    }

    public void write(WalMessage message) throws IOException {
        lock.lock();
        try (FileOutputStream fos = new FileOutputStream(logFile, true);
             FileChannel channel = fos.getChannel();
             FileLock fileLock = channel.lock()) {
            
            String logEntry = message.toString() + System.lineSeparator();
            byte[] bytes = logEntry.getBytes();
            fos.write(bytes);
            fos.flush();
        } finally {
            lock.unlock();
        }
    }
}