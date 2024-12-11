
/*
 * The agent monitors the applets using the /health route
 * If the health route returns unhealthy then the applet will
 * kill and then relaunch the application.
 * 
 * If the applet is not responding or has its process terminated
 * then the agent will kill and relaunch.
 * 
 * Restriction:
 * Only 1 Applet to Server pair.
 * 
 * This applet checks if its running on localhost.
 */

import libs.config.*;
import java.io.*;
import java.lang.ProcessBuilder;
import java.util.NoSuchElementException;

public class AppletController {

    public static ConfigState state = new ConfigState();
    public AppletController(){}

    public boolean launchApplet(String appletName, boolean block){
        String applet = appletName.trim();
        String applicationDirectory = null;
        switch (applet) {
            case "TestLib":
            case "LoadBalancerServer":
                applicationDirectory = ConfigState.proxyServerExecutionPath;
                break;
            case "URLShortner":
                applicationDirectory = ConfigState.KVStoreServerExecutionPath;
                break;
            default:
                System.err.println("Critical Error: Unknown applet '" + applet + "'.");
                break;
        }

        if(applicationDirectory == null){
            return false;
        }
        return _launchAppletByDirectory(applicationDirectory,block);
    }


    private boolean _launchAppletByDirectory(String applicationDirectory, boolean block) {
        ProcessBuilder processBuilder = new ProcessBuilder("make", "agentRun");
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(new File(applicationDirectory));

        try {
            Process process = processBuilder.start();
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Applet Output: " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading applet output:");
                    e.printStackTrace();
                }
            }).start();

            if (block) {
                int exitCode = process.waitFor();
                return exitCode == 0;
            }
            return true;

        } catch (IOException e) {
            System.err.println("IOException during applet launch:");
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            System.err.println("Applet launch interrupted:");
            e.printStackTrace();
            Thread.currentThread().interrupt();
            return false;
        }
    }
    public boolean terminateApplet(String appletName, Boolean forceKill) {
        int processId = isProcessAlive(appletName);
        if (processId < 0) {
            // Process is not running
            return true;
        }
        return killProcess(processId, forceKill);
    }

    /*
     * Checks whether a process is alive or not on the local device.
     * The process name is determined through the java file when executed.
     * 
     * We inspect the pid to ensure if the process is executing.
     *  
     * Returns -1 if the process pid does not exist via jps query.
     * Otherwise it returns the pid of the running applet. 
     */
    public int isProcessAlive(String applet){
        ProcessBuilder processBuilder = new ProcessBuilder("jps");
        Process process;
        String lineData;
        String[] jpsQueryData;
        int currentProcessId = -1;
        try {
            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((lineData = reader.readLine()) != null) {
                jpsQueryData = extractJPSQueryProcessLine(lineData);
                if (jpsQueryData != null && applet.equals(jpsQueryData[1])) {
                    try {
                        currentProcessId = Integer.parseInt(jpsQueryData[0]); // Correct index for PID
                        break;
                    } catch (NumberFormatException e) {
                        System.err.println("Failed to parse PID: " + jpsQueryData[0]);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            System.err.println("IOException during process alive check:");
            e.printStackTrace();
        }
        return currentProcessId;
    }

    /*
     * Attempts to kill the process elegantly by sending 
     * a SIGTERM Signal
     * This lets the process finish handling its requests.
     */
    private Boolean killProcess(int pid, Boolean forceKill) {
        ProcessHandle processHandle = ProcessHandle.of(pid).orElse(null);
        if (processHandle == null) {
            System.err.println("No such PID found: " + pid);
            return false;
        }
        boolean destroyStatus = forceKill ? processHandle.destroyForcibly() : processHandle.destroy();
        if (destroyStatus) {
            System.out.println("Process with PID " + pid + " terminated successfully.");
        } else {
            System.err.println("Failed to terminate process with PID " + pid + ".");
        }
        return destroyStatus;
    }


    private String[] extractJPSQueryProcessLine(String outputLine) {
        if (outputLine == null) {
            return null;
        }

        String[] columns = outputLine.trim().split("\\s+", 2);
        if (columns.length < 2) {
            return null;
        }

        return columns;
    }
}
