

public class AppletMonitor implements Runnable {
    private final String appletName;
    private static final AppletController controller = new AppletController();

    public AppletMonitor(String appletName) {
        this.appletName = appletName;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Check if the applet process is alive
                int processId = controller.isProcessAlive(appletName);
                System.out.println("[" + appletName + "] Current PID: " + processId);

                if (processId == -1) {
                    System.out.println("[" + appletName + "] is not running. Attempting to relaunch.");
                    boolean launched = controller.launchApplet(appletName, false);
                    if (launched) {
                        System.out.println("[" + appletName + "] relaunch initiated successfully.");
                    } else {
                        System.err.println("[" + appletName + "] failed to relaunch.");
                    }
                } else {
                    System.out.println("[" + appletName + "] is running with PID: " + processId);
                }

                // Sleep for a fixed interval before the next health check
                Thread.sleep(5000); // Check every 5 seconds (configurable)
            } catch (InterruptedException e) {
                System.err.println("[" + appletName + "] Monitoring interrupted.");
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } catch (Exception e) {
                System.err.println("[" + appletName + "] Unexpected error during monitoring:");
                e.printStackTrace();
            }
        }
        System.out.println("[" + appletName + "] Monitoring thread terminated.");
    }
}