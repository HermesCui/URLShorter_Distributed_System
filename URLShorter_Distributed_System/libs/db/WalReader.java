package libs.db;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class WalReader {
    private final File logFile;

    public WalReader(String filePath) {
        this.logFile = new File(filePath);
    }

    public List<WalMessage> readAll() throws IOException {
        List<WalMessage> messages = new ArrayList<>();
        if (!logFile.exists()) {
            return messages;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                messages.add(WalMessage.fromString(line));
            }
        }
        return messages;
    }

    public void watchLogFile() throws IOException, InterruptedException {
        Path logFilePath = logFile.toPath().getParent();
        WatchService watchService = FileSystems.getDefault().newWatchService();
        logFilePath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        System.out.println("Watching for changes in log file: " + logFile.getName());
        while (true) {
            WatchKey key = watchService.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    Path changed = (Path) event.context();
                    if (changed.endsWith(logFile.getName())) {
                        System.out.println("Log file modified: " + logFile.getName());
                    }
                }
            }
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }
}