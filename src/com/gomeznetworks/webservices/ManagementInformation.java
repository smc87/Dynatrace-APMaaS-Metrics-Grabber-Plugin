package com.gomeznetworks.webservices;

import java.io.File;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.compuware.APMaaSMetricsMonitor;
import com.compuware.gomez.client.GomezAccountClient;



public class ManagementInformation {

	private static final Logger log = Logger.getLogger(APMaaSMetricsMonitor.class.getName());

	private String account;
	private String password;
	private String script;
	private boolean debug;

	public ManagementInformation (String accountName, String accountPassword, String scriptName, boolean debug){
		this.account = accountName;
		this.password = accountPassword;
		this.script = scriptName;
		this.debug = debug;
	}
	
	

	public boolean createConfigXml(){
		GpnAccountManagementServiceSoapProxy managementSoapService = new GpnAccountManagementServiceSoapProxy();
		CAccountConfigPackage accountPackage = new CAccountConfigPackage();
		boolean found = false;

		try {
			accountPackage = managementSoapService.getAccountConfigPackage(account, password);
			CMonitorData[] xml = accountPackage.getMonitorSet();

			if (!accountPackage.getStatus().getEStatus().toString().equals("STATUS_SUCCESS")){
				log.warning("There was an error creating the scripts XML file!");
				log.warning(accountPackage.getStatus().getEStatus().toString());
				log.warning(accountPackage.getStatus().getSErrorMessage());
				return false;
			}
			
			for(int x = 0; x < xml.length; x = x+1){
				if (xml[x].getDesc().toString().trim().toLowerCase().equals(script.toLowerCase().trim()) && xml[x].getStatus().equals("ACTIVE")){
					//found the script
					if (debug) log.info("Found Script name: " + script);

					DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

					// root elements
					Document doc = docBuilder.newDocument();
					Element rootElement = doc.createElement("config");
					doc.appendChild(rootElement);

					// script name elements
					Element scriptname = doc.createElement("scriptName");
					scriptname.appendChild(doc.createTextNode(xml[x].getDesc().trim()));
					rootElement.appendChild(scriptname);
					
					//monitorinstances for last mile
					if (debug) log.info(xml[x].get_class());
					if (xml[x].get_class().toLowerCase().contains("pp_")) {
					
					
					int[] monitorIds = GomezAccountClient.getLMMonitorInstances(account, password, xml[x].getMid(), debug);
					
					int size = monitorIds.length;
				    if(debug) log.info("Amount of monitor ID's returned: " + size);
			        for (int i = 0; i < size; i++) {
			        	String ID = Integer.toString(monitorIds[i]);
			        	if (debug) log.info("Outside the new (old) API call and i have this Instance: " + ID);
						Element scriptId = doc.createElement("scriptId");
						scriptId.appendChild(doc.createTextNode(ID));
						rootElement.appendChild(scriptId);
			        
			        }

					} else {
					
						// script id elements
						Element scriptId = doc.createElement("scriptId");
						scriptId.appendChild(doc.createTextNode(xml[x].getMid()));
						rootElement.appendChild(scriptId);
					
					}


					// transaction type elements
					Element transactionType = doc.createElement("transactionType");
					transactionType.appendChild(doc.createTextNode(xml[x].get_class()));
					rootElement.appendChild(transactionType);


					
					
					
					// runtime elements
					// creating the last run time to be one hour back from GMT
					DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
					Calendar cal = Calendar.getInstance();
					cal.add(Calendar.HOUR, -1);
					Date date = cal.getTime();
					String runTime = dateFormat.format(date).toString();

					Element runtime = doc.createElement("runtime");
					runtime.appendChild(doc.createTextNode(runTime));
					rootElement.appendChild(runtime);

					// write the content into xml file
					TransformerFactory transformerFactory = TransformerFactory.newInstance();
					Transformer transformer = transformerFactory.newTransformer();
					DOMSource source = new DOMSource(doc);
					StreamResult result = new StreamResult(new File(xml[x].getDesc().trim() + ".xml"));

					transformer.transform(source, result);
					
					found = true;
					break;
				}
			}
			
			if (!found) {
				log.log(Level.WARNING, "Unabled to find an active script called " + script );
			}

		} catch (RemoteException e) {
			log.log(Level.SEVERE, "RemoteException", e);
		} catch (ParserConfigurationException e) {
			log.log(Level.SEVERE, "ParserConfigurationException", e);
		} catch (TransformerException e) {
			log.log(Level.SEVERE, "TransformerException", e);
		}

		return found;
	}
}
