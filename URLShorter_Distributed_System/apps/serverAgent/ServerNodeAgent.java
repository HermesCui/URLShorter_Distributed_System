import libs.com.*;
import libs.config.AppletType;
import libs.config.ConfigState;
import libs.data.*;
import libs.data.AgentStateLoader;
import libs.msg.*;

import java.lang.*;
import java.net.UnknownHostException;
import java.util.concurrent.*;

public class ServerNodeAgent {	
	static AppletController appletController = new AppletController();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

	public static void main(String[] args) 
	{
		try {
			AppletType appletType = AppletType.AGENT;
			ConfigState.parseEnviromentVariables(args, appletType);
		} catch (UnknownHostException e) {
			System.out.println("Error during starting..");
			e.printStackTrace();
			return;
		}
		
		AgentStateLoader.loadAgentData();
		bootstrapApplication();
	}

	public static void bootstrapApplication(){
		System.out.println("\nBootup has been completed!\n");
		entry();
	}

	public static void entry(){
		System.out.println("Listing applets..");
		for (String applet : AgentState.offerList) {
			System.out.println(applet);
            executorService.submit(new AppletMonitor(applet));
        }
		while(true){}
	}
}