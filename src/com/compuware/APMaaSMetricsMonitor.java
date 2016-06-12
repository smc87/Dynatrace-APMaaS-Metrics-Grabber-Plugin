
 /**
  * This template file was generated by dynaTrace client.
  * The dynaTrace community portal can be found here: http://community.dynatrace.com/
  * For information how to publish a plugin please visit http://community.dynatrace.com/plugins/contribute/
  **/

package com.compuware;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
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

		
		boolean debug = env.getConfigBoolean("debug");
		
		
		
		
		//Check/create for lockfile, pause until lockfile is gone
		File lockFile = new File("connection.lock");
		Random rand = new Random();
		log.info("Waiting For Lock File...");
		//increase randomness
		Random rand2 = new Random();
		while (lockFile.exists()) {
			
			long dateDiff = new Date().getTime() - lockFile.lastModified();
			// 2*60*1000 = 2 minutes
			if (dateDiff >= 2*60*1000 && dateDiff <= 3000*60*1000) {
				lockFile.delete();
				log.warning("Deleted old lockFile - 2 minutes old!");
				log.warning("lockfile modified at: " + lockFile.lastModified());
				log.warning("current time: " + new Date().getTime());
				log.warning("datediff: " + dateDiff);
			}
			int sleepTime = 100 + rand2.nextInt(950);
			if (debug) log.info("Connection Locked - Sleeping for " + sleepTime + " milliseconds");
			Thread.sleep(sleepTime);
		}
		//FIXME - Convert to fileLocks
		//Small extra randomisation to try and make sure we are the only one with a lock
		int sleepyTime = 5 + rand.nextInt(450);
		Thread.sleep(sleepyTime);
		while (lockFile.exists()) {
			
			
			long dateDiff = new Date().getTime() - lockFile.lastModified();
			if (dateDiff >= 2*60*1000 && dateDiff <= 3000*60*1000) {
				lockFile.delete();
				log.warning("2nd check Deleted old lockFile - 2 minutes old!");
				log.warning("2nd check lockfile modified at: " + lockFile.lastModified());
				log.warning("2nd check current time: " + new Date().getTime());
				log.warning("2nd check datediff: " + dateDiff);
			}
			int sleepTime = 100 + rand.nextInt(950);
			if (debug) log.info("Connection Locked - Sleeping for " + sleepTime + " milliseconds");
			Thread.sleep(sleepTime);
		}
		if (!(lockFile.exists())) {
		lockFile.createNewFile();
		//add script ID to lockfile. then check lockfile before calling out from scriptdata.java 
		FileWriter lockWriter = new FileWriter(lockFile, true);
		/* Remove this in favour of \r\n and only reading the first line with isMyLock()
		if (lockFile.exists()) {
			
		    RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
		    raf.setLength(0);
		    raf.close();
		}
		*/
		lockWriter.write(scriptName+"\r\n");		
		lockWriter.close();
		} else {
			log.warning("Unexpected log file - exited");
			return new Status(Status.StatusCode.ErrorInternal, "unexpected lockFile Existed");
		}
		log.info("Finished Waiting For Lock File");
		
		//SetUP Simple Proxy
		boolean proxyOn = env.getConfigBoolean("proxyOn");
		String proxyHost = env.getConfigString("proxyHost");
		String proxyPort = env.getConfigString("proxyPort");
		String nonProxyHosts = env.getConfigString("nonProxyHosts");
		boolean proxyAuth = env.getConfigBoolean("proxyAuth");
		String proxyUsername = env.getConfigString("proxyUsername");
		String proxyPassword = env.getConfigPassword("proxyPassword");
		
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
				if (isMyLock(scriptName)) {
					if (debug) log.info("Its My Lock");
					lockFile.delete();
				} else {
					log.warning("Attempted lockfile deletion - NOT MY LOCK");
				}
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
			if (isMyLock(scriptName)) {
				if (debug) log.info("Its My Lock");
				lockFile.delete();
			} else {
				log.warning("Attempted lockfile deletion - NOT MY LOCK");
			}
			log.info("Completed Collection Run");
			return new Status(Status.StatusCode.Success);
		}
		//make sure we have the right transaction to delete the lock file.
		if (isMyLock(scriptName)) {
			if (debug) log.info("Its My Lock");
			lockFile.delete();
		} else {
			log.warning("Attempted lockfile deletion - NOT MY LOCK");
		}
		
		log.info("Completed Collection Run - No Data");
		return new Status(Status.StatusCode.ErrorInternal, "Possible Normal Failure - We fail when there is no new data to avoid a data point being created. Enable debug for verbose logs.");
	}



	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		// empty
	}
	
	private boolean isMyLock(String scriptName) throws IOException {
		BufferedReader	readIn = new BufferedReader(new FileReader("connection.lock"));
		String str;
		String lockedScriptName = null;
		str = readIn.readLine();
		lockedScriptName = str;
		readIn.close();
		log.info("Calling Script Name: " + scriptName);
		log.info("Locked Script Name: " + lockedScriptName);
		if (lockedScriptName.equals(scriptName)) {
		log.info("MyLock is: True");
			return true;
		} else {
		log.info("MyLock is: False");
		return false;
		}
	}
	
}
