
 /**
  * This template file was generated by dynaTrace client.
  * The dynaTrace community portal can be found here: http://community.dynatrace.com/
  * For information how to publish a plugin please visit http://community.dynatrace.com/plugins/contribute/
  **/

package com.compuware;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Collection;
import java.util.Date;
import java.util.Random;
import java.util.logging.Logger;

import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.Status;
import com.gomeznetworks.webservices.ManagementInformation;
import com.gomeznetworks.webservices.ScriptData;


public class APMaaSMetricsMonitor implements Monitor {

	private static final Logger log = Logger.getLogger(APMaaSMetricsMonitor.class.getName());

	// measure constants
	private static final String METRIC_GROUP = "Script Metrics";
	private static final String MSR_AVAILABILITY = "availability";
	private static final String MSR_RESPONSE_TIME = "responseTime";
	private static final String MSR_MISSING_DATA = "missingData";

	// Extra parameters that might be useful in a later release
	public static final String gomezType = "BROWSERTX";
	public static final String gomezGroup = "ALL";
	public static final String gomezOrder = "TIME";

	// by default re-create the script every two hours
	private static final long MAX_AGE_XML_SCRIPT = 2*60*60*1000;
	
	
	//Proxy Setup
	


	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		// Not using the setup method
		return new Status(Status.StatusCode.Success);
	}
	

	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		// The plugin starts here
		
		// Grab the values the user entered
		String scriptName = env.getConfigString("scriptName");
		String gomezUserName = env.getConfigString("gomezUserName");
		String gomezPassword = env.getConfigPassword("gomezPassword");

		boolean proxyOn = env.getConfigBoolean("proxyOn");
		String proxyHost = env.getConfigString("proxyHost");
		String proxyPort = env.getConfigString("proxyPort");
		String nonProxyHosts = env.getConfigString("nonProxyHosts");
		boolean proxyAuth = env.getConfigBoolean("proxyAuth");
		String proxyUsername = env.getConfigString("proxyUsername");
		String proxyPassword = env.getConfigPassword("proxyPassword");
		
		boolean debug = env.getConfigBoolean("debug");
		
	
		//Check/create for lockfile, pause until lockfile is gone
		File lockFile = new File("connection.lock");
		
		//Check that lock file hasnt been sat there for ages. 
				long dateDiff = new Date().getTime() - lockFile.lastModified();
		// 4*60*1000 = 2 minutes
		if (dateDiff >= 4*60*1000 && dateDiff <= 3000*60*1000) {
			lockFile.delete();
			log.warning("Deleted old lockFile - 4 minutes old!");
			log.warning("lockfile modified at: " + lockFile.lastModified());
			log.warning("current time: " + new Date().getTime());
			log.warning("datediff: " + dateDiff);
		}
		
		//try lock
		if (!getLock(scriptName)) {
			log.warning("Unable to acquire lock");
			return new Status(Status.StatusCode.ErrorInternal, "Could not acquire connection lock");			
		}

		
		log.info("Finished Waiting For Lock File");
		
		//SetUP Simple Proxy

		
		if (proxyOn) {					
		System.setProperty("http.proxyHost", proxyHost);
	    System.setProperty("http.proxyPort", proxyPort);
	    System.setProperty("https.proxyHost", proxyHost);
	    System.setProperty("https.proxyPort", proxyPort);
	    System.setProperty("http.nonProxyHosts", nonProxyHosts);
	    System.setProperty("https.nonProxyHosts", nonProxyHosts);	    
	    if (proxyAuth) {
	    final String authUser = proxyUsername;
	    final String authPassword = proxyPassword;
	    Authenticator.setDefault(
	       new Authenticator() {
	          public PasswordAuthentication getPasswordAuthentication() {
	             return new PasswordAuthentication(
	                   authUser, authPassword.toCharArray());
	          }
	       }
	    );
	    System.setProperty("http.proxyUser", authUser);
	    System.setProperty("http.proxyPassword", authPassword);
	    System.setProperty("https.proxyUser", authUser);
	    System.setProperty("https.proxyPassword", authPassword);
	    }
		}	
	

		
		//checks to see if configuration xml exists
		if (debug) log.info("Checking the collector for " + scriptName + ".xml");
		File f = new File(scriptName + ".xml");
		//if (debug && f.exists()) f.delete();
		if(! f.exists() || f.lastModified() < (System.currentTimeMillis() - MAX_AGE_XML_SCRIPT)){
			//creates the script data XML
			ManagementInformation manage = new ManagementInformation(gomezUserName, gomezPassword, scriptName, debug);

			if (! manage.createConfigXml()){
				if (debug) log.info("an error has occured");
				releaseLock(scriptName);
				return new Status(Status.StatusCode.ErrorInternal, "An Error Occurred. Could not create XML File " + f);
			}

			if (debug) log.info("File saved!");
			if (debug) log.info("Filelocation: " + f.getAbsolutePath());
		} else {
			if (debug) log.info("No need to create a new file!");
			if (debug) log.info(f.getAbsolutePath());
	
		
		}
		
		if (!(isMyLock(scriptName))) {
			return new Status(Status.StatusCode.ErrorInternal, "Exit to prevent duplicate session.");
		}
		
		//creating a script object
		ScriptData script = new ScriptData(scriptName, gomezUserName, gomezPassword, gomezGroup, gomezOrder, debug);
		if (debug) log.info("Script created correctly");
		//returns true if everything is successful
		if(script.getSummaryMetrics()){
			if (debug) log.info ("We got summary data from Gomez");
			//We got summary data back!  Now to assign the values to a measure.
			Collection<MonitorMeasure> measures;
		
			if (script.missingData != 1) {
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_AVAILABILITY)) != null) {
				if (debug) log.info("Processing AVAILABILITY data");
				for (MonitorMeasure measure : measures)
					measure.setValue(script.availability);
			}


			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_RESPONSE_TIME)) != null) {
				if (debug) log.info("Processing ResponseTime data");
				for (MonitorMeasure measure : measures)
					measure.setValue(script.responseTime);
			}						
			} else {
				if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_MISSING_DATA)) != null) {
					if (debug) log.info("Processing missingData data");
					for (MonitorMeasure measure : measures)
						measure.setValue(script.missingData);
				}	
			}
			releaseLock(scriptName);
			log.info("Completed Collection Run");
			return new Status(Status.StatusCode.Success);
		}
		//make sure we have the right transaction to delete the lock file.
		releaseLock(scriptName);		
		log.info("Completed Collection Run - No Data");
		return new Status(Status.StatusCode.ErrorInternal, "Normal Failure - We fail when there is no new data to avoid a data point being created. Enable debug for verbose logs.");
	}


	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		// empty
	}
	
	private boolean getLock(String scriptName) throws IOException, InterruptedException {
	File lockFile = new File("connection.lock");
	//syncro file locking for thread safeness
	int retry = 0;
	synchronized (this) {
		while (retry < 45) {
		log.info("ATTEMPTING LOCK");
		if (!lockFile.exists()) {
		lockFile.createNewFile();
		FileWriter lockWriter = new FileWriter(lockFile, true);
		lockWriter.write(scriptName+"\r\n");		
		lockWriter.close();
		if (isMyLock(scriptName)) {
		log.info("LOCK AQUIRED");
		return true;
		}
		}
		retry++;
		Random rand = new Random();
		int sleepTime = 950 + rand.nextInt(1650);
		Thread.sleep(sleepTime);
		} //End Of Loop
		}  
	log.info("Unable to aquire lock");
	return false;
	}
	
	
	private boolean releaseLock(String scriptName) throws IOException, InterruptedException {
		File lockFile = new File("connection.lock");
		int retry = 0;
		synchronized (this) {
			while (retry < 5) {
		if (isMyLock(scriptName)) {
			lockFile.delete();
			log.info("Lock released");
			return true;
		}
		retry++;
		Random rand = new Random();
		int sleepTime = 950 + rand.nextInt(1650);
		Thread.sleep(sleepTime);
		}
		}
		return false;
		
	}
	
	
	private boolean isMyLock(String scriptName) throws IOException {
		BufferedReader	readIn = new BufferedReader(new FileReader("connection.lock"));
		String str;
		String lockedScriptName = null;
		str = readIn.readLine();
		lockedScriptName = str;
		readIn.close();
		log.fine("Calling Script Name: " + scriptName);
		log.fine("Locked Script Name: " + lockedScriptName);
		if (lockedScriptName.equals(scriptName)) {
		log.fine("MyLock is: True");
			return true;
		} else {
		log.fine("MyLock is: False");
		return false;
		}
	}
	
}
