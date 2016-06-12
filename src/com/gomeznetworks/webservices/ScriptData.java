package com.gomeznetworks.webservices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.axis.AxisFault;

import com.compuware.APMaaSMetricsMonitor;
import com.gomeznetworks.jaxb.data.GpnResponseData;
import com.gomeznetworks.jaxb.data.GpnResponseData.TXTEST;
import com.gomeznetworks.jaxb.save.Config;

public class ScriptData {

	private static final Logger log = Logger.getLogger(APMaaSMetricsMonitor.class.getName());

	//private variables
	private String scriptName;
	private String gomezUserName;
	private String gomezPassword;
	private String gomezType;
	private String gomezGroup;
	private String gomezOrder;
	private boolean debug;

	//public variables
	public double availability;
	public double missingData;
	public long responseTime;
	public double dnsTime;
	public double connectTime;
	public double firstByteTime;
	public double sslTime;

	//constructor
	public ScriptData (String scriptName, String gomezUserName, String gomezPassword, String gomezGroup, String gomezOrder, boolean debug){
		this.scriptName = scriptName;
		this.gomezUserName = gomezUserName;
		this.gomezPassword = gomezPassword;
		this.gomezGroup = gomezGroup;
		this.gomezOrder = gomezOrder;
//		this.gomezType = gomezType;
		this.debug = debug;
	}

	public boolean getSummaryMetrics(){
		try {
			//This is the the container that stores the received message.
			CGpnUniversalXmlDocResponse response;

			//This is the container for the actual XML Doc object, the raw XML can be obtained by calling get_any() method, which returns an array.
			CGpnUniversalXmlDocResponseXmlDocument xmlDoc;

			//JAXB magic Stuff.
			JAXBContext jaxbContext = JAXBContext.newInstance(com.gomeznetworks.jaxb.data.ObjectFactory.class);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			JAXBContext jaxbContext2 = JAXBContext.newInstance(com.gomeznetworks.jaxb.save.ObjectFactory.class);
			Unmarshaller unmarshaller2 = jaxbContext2.createUnmarshaller();

			//Pull in the config file
			Config config = (Config) unmarshaller2.unmarshal(new File(scriptName + ".xml"));

			//format time the current time in GMT
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			Date date = new Date();
			String runTime = dateFormat.format(date).toString();

			//Start to pull data
			GpnDataExportServiceSoapProxy soapProxy = new GpnDataExportServiceSoapProxy();

			// set script id
			int[] iMonitorIdSet = {Integer.parseInt(config.getScriptId())};

			// set site id (an empty array means all sites)
			int[] iSiteIdSet = {};
			
			//Configure Gomeztype - PP vs BrowserTX
			String transType = config.getTransactionType();
			if (transType.toLowerCase().contains("pp_")) {
			gomezType = "PRIVATEPEER";
			} else {
			gomezType = "BROWSERTX";
			}
			
			int maxFail = 3;
			boolean sessionObjectSuccess = false;
			Random rand = new Random();

			//Gets the sessionToken
			InitFeedResponse sessionObject = soapProxy.openDataFeed3(gomezUserName, gomezPassword, iMonitorIdSet, iSiteIdSet, gomezType, gomezGroup, "5", config.getRuntime(), runTime, gomezOrder, "TESTTIME_RELATIVE");

			//Checks for success and loops on failure
			for(int x = 0; x < maxFail; x++) {
				//checks to see if we get a valid response with the session token
				if (!(sessionObject.getStatus().getEStatus().getValue().toString() == "STATUS_SUCCESS")){
					if (debug) log.info("Unable to get session tokens because " + sessionObject.getStatus().getSErrorMessage());
					if (sessionObject.getStatus().getSErrorMessage().contains("Max number of concurrent sessions exceeded")) {
						
						//sleep
						int sleepTime = 200 + rand.nextInt(550);
						log.info("Got max Sessions Error. Sleeping for " + sleepTime + " milliseconds");
						Thread.sleep(sleepTime);
					
						//Gets the sessionToken
						sessionObject = soapProxy.openDataFeed3(gomezUserName, gomezPassword, iMonitorIdSet, iSiteIdSet, gomezType, gomezGroup, "5", config.getRuntime(), runTime, gomezOrder, "TESTTIME_RELATIVE");
						sessionObjectSuccess = false;
					} else {
						sessionObjectSuccess = false;
						break;
					}
					sessionObjectSuccess = false;
				} else {
					sessionObjectSuccess = true;
					break;
				}
			}

			//gracefully fail if the open data feed fails for any reason
			if(!sessionObjectSuccess) {
				if (debug) log.info("Username: " + gomezUserName);
				//if (debug) log.info("Password: " + gomezPassword);
				if (debug) log.info("Monitor ID: " + Arrays.toString(iMonitorIdSet));
				if (debug) log.info("Site ID: " + Arrays.toString(iSiteIdSet));
				if (debug) log.info("Gomez Type: " + gomezType);
				if (debug) log.info("Gomez Group: " + gomezGroup);
				if (debug) log.info("Start Time: " + config.getRuntime());
				if (debug) log.info("Current Time: " + runTime);
				if (debug) log.info("Script Name: " + scriptName);
				if (debug) log.info("Script Type: " + config.getTransactionType());

				
				
				//We always want to show these messages
				log.warning("Failed to open data feed (successobject not True): " + sessionObject.getStatus().getEStatus().getValue());
				log.warning("Failed to open data feed (successobject not True): " + sessionObject.getStatus().getSErrorMessage());
				//Try to close the connection anyway
				//FIXME - Seems around here that sessions are always double generated
				String sessionToken = sessionObject.getSessionToken();
				soapProxy.closeDataFeed(sessionToken);
				log.warning("Attempted to close session (successobject not True): " + sessionToken);
				return sessionObjectSuccess;
			}
			

			String sessionToken = sessionObject.getSessionToken();

			if (debug) log.info(sessionToken);
			//save session info to lock file
			File myLockFile = new File("connection.lock");
			FileWriter lockWriter = new FileWriter(myLockFile, true); 
			lockWriter.append("\r\n");
			lockWriter.append(sessionToken);
			lockWriter.close();
			//Assign the actual response to the empty response container object.
			response = soapProxy.getResponseData(sessionToken);
			
			if (response.getStatus().getEStatus().getValue() != "STATUS_SUCCESS"){
				log.warning("Failed to get response data (Estatus was not STATUS_SUCCESS): " + response.getStatus().getEStatus().getValue());
				log.warning("Failed to get response data (Estatus was not STATUS_SUCCESS): " + response.getStatus().getSErrorMessage());
				//close stray session
				log.warning("Attempted to close stray session (Estatus was not STATUS_SUCCESS): " + sessionToken);
				soapProxy.closeDataFeed(sessionToken);
				return false;
			} 

			//Close the data feed now, because we are now done with it.
			soapProxy.closeDataFeed(sessionToken);
			


			//Assign the XMLDoc contents of this message to the empty container.
			xmlDoc = response.getXmlDocument();

			//Print out the raw XML as a test.
			if (debug) log.finer(xmlDoc.get_any()[0].toString());

			//Unmarshal the raw XML into the GpnResponseData, I automatically generated this class using the GpnResponseData.xsd and xjc
			//I generated the GpnResponseData.xsd by copying and pasting the raw XML from the response message into a reverse engineering tool.
			GpnResponseData responseData = (GpnResponseData) unmarshaller.unmarshal(xmlDoc.get_any()[0]);
			
			if (debug) log.info(scriptName + " between " + config.getRuntime() + " to " + runTime);
			///////////////////////////////////////////
			//There's no error but there's also no data
			///////////////////////////////////////////
			String transactionFile = (scriptName + "-processedTransactions.txt");
			
			
			ArrayList<String> processedList = new ArrayList<String>();
			File myFile = new File(transactionFile);
			if(!myFile.exists()) {
			    myFile.createNewFile();
			} 
	        BufferedReader in = new BufferedReader(new FileReader(transactionFile));
	        String str;
	        while((str = in.readLine()) != null){
	            processedList.add(str);
	        }
	        in.close();
	        	        
	        if (debug) log.info("File ArrayDepth :" + processedList.size());
			//Remove Old Entries
	        if (processedList.size()>30) {
	        	processedList.remove(0);
	        if (debug) log.info("Removed an Old Processed Transaction");
	        }
	                
	        
			Iterator<TXTEST> transactions = responseData.getTXTEST().iterator();
			while (transactions.hasNext()) {		
 				//There are transactions returned from gomez API's at this point.
				//Only want to do age check if there are no new tranactions
			TXTEST transaction = transactions.next();
			
			if (processedList.contains(transaction.getTtime())) {
				//lets not process any transactions we have already done
				if (debug) log.info("Skipped Trans: " + transaction.toString());
			} else {
			//Transaction has not already been processed
			//add transaction to processed list
			processedList.add(transaction.getTtime());
	        if (debug) log.info("After Add ArrayDepth :" + processedList.size());
			if (debug) log.info("Processed Trans: " + transaction.toString());
			//write processed list to file
			FileWriter writer = new FileWriter(transactionFile); 
			for(String processedTrans: processedList) {
			  writer.write(processedTrans);
			  writer.write("\r\n");
			}
			writer.close();
		    //set monitor result vars
			if (transaction.getTpt()<1||transaction.getTps()!=transaction.getTpt()) {
			availability = 0;
			} else {
			availability = 100;	
			}
			responseTime = transaction.getRtime();	
			

			//everything was successful!
		    //return - make sure we only process one datapoint at a time
			return true;
			}
			//still in the loop - no new transactions yet if not returned by now!
			}
			//Out of the loop and No valid transactions were returned - lets do a check on the last time a transaction was returned.
			//return availability issue if theres a problem
	        if (!processedRecently(myFile)) {
	        	if (debug) log.warning("no data was returned, and we have not processed a transaction in over 20 minutes");
	        	missingData = 1;	        	
	        	return true;
	        }
			return false; //no data - return false and wait for next update

			
		} catch (AxisFault e) {
			log.log(Level.SEVERE, "Failed because of a AxisFault exception!", e);
		} catch (RemoteException e) {
			log.log(Level.SEVERE, "Failed because of a Remote exception!", e);
		} catch (NullPointerException e) {
			log.log(Level.SEVERE, "Failed because of a NullPointer exception!", e);
		} catch (JAXBException e) {
			log.log(Level.SEVERE, "Failed because of a JAXB exception!", e);
		} catch (Exception e){
			log.log(Level.SEVERE, "Undefined exception!", e);
		}
		//FIXME - Hopefully this closes session after an exception is thrown.
		//Try to close any stray sessions caused by exceptions
		try {
			BufferedReader	in = new BufferedReader(new FileReader("connection.lock"));
			String str;
			String sessiontoken = null;
			while((str = in.readLine()) != null){
			sessiontoken = str;
			}
			in.close();
			//FIX ME - this doesnt always close the session! - Check for timeout and retry.
			log.info("Attempting to close stray session: " + sessiontoken);
			GpnDataExportServiceSoapProxy soapProxy = new GpnDataExportServiceSoapProxy();
			COpStatusData sessionObject = soapProxy.closeDataFeed(sessiontoken);
			int failCount = 0;
			while (sessionObject.getEStatus().getValue() != "STATUS_SUCCESS" && failCount < 3){
				log.warning("Closing Stray Session Failed");
				log.warning(sessionObject.getEStatus().getValue());
				try {
					log.warning("Sleep 30sec to try and avoid slow network response");
					Thread.sleep(30000);
					log.warning("Attempting to close session again");
					sessionObject = soapProxy.closeDataFeed(sessiontoken);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				failCount++;
			}
		} catch (FileNotFoundException e2) {
		
			if (debug) log.warning("No session file found after exception");	
		} catch (IOException e1) {
		
			if (debug) log.warning("No session file found after exception");
		}

		return false;
	}
	
private boolean processedRecently(File transactionFile) {
	if (gomezType.contains("PRIVATEPEER")) {
	long dateDiff = new Date().getTime() - transactionFile.lastModified();
	if (dateDiff >= 20*60*1000) {
		//Last transaction processed over 15minutes ago
		log.warning("Last transaction processed over 20minutes ago - No new data returned from API recently");
		return false;
	}
	if (debug) log.info("Some Transactions proccessed ok within the last 20minutes");
	return true;	
}
//Wasn't a PP Transaction
return true;
}




}