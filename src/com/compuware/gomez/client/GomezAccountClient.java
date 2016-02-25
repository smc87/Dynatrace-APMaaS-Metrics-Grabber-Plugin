package com.compuware.gomez.client;

import com.compuware.gomez.account.api.client.GomezAccount;

import java.util.logging.Level;
import java.util.logging.Logger;



import com.gomeznetworks.webservices.account.AccountServiceResponse;
import com.gomeznetworks.webservices.account.ArrayOfMonitorInstanceRef;
import com.gomeznetworks.webservices.account.Monitor;
import com.gomeznetworks.webservices.account.Retrieve;
import com.gomeznetworks.webservices.account.Retrieve.Request.AccountRequest;
import com.gomeznetworks.webservices.account.Retrieve.Request.AccountRequest.ProductRequest;
import com.gomeznetworks.webservices.account.Retrieve.Request.AccountRequest.ProductRequest.ActiveXFProductRequest;
import com.gomeznetworks.webservices.account.Retrieve.Request.AccountRequest.ProductRequest.ActiveXFProductRequest.ActiveXFTestRequest;
import com.gomeznetworks.webservices.account.Retrieve.Request.AccountRequest.ProductRequest.ActiveXFProductRequest.ActiveXFTestRequest.LastMileTestRequest;
import com.gomeznetworks.webservices.account.Retrieve.Request.AccountRequest.ProductRequest.ActiveXFProductRequest.ActiveXFTestRequest.LastMileTestRequest.LastMileTransactionTestFilter;
import com.gomeznetworks.webservices.account.Retrieve.Request.AccountRequest.ProductRequest.ActiveXFProductRequest.ActiveXFTestRequest.LastMileTestRequest.LastMileTransactionTestFilter.LastMileMonitorInstanceFilter;
import com.gomeznetworks.webservices.account.Retrieve.Request.User;
import com.gomeznetworks.webservices.account.StatusType;


/**
 *
 * @author cwfr-lizac
 */
public class GomezAccountClient {
	
	
	private final static Logger log = Logger.getLogger(com.compuware.APMaaSMetricsMonitor.class.getName());


    /**
     * @param args the command line arguments
     */
    public static int[] getLMMonitorInstances(String account, String password, String inputMonitorID, boolean debug) {
    	try {
    	
    	
        Retrieve.Request request=new Retrieve.Request();
        
        // account service : retrieve last mile monitor instance IDs

        User user = new User();
        user.setUsername(account);
        user.setPassword(password);
    

        LastMileTransactionTestFilter lmtf = new LastMileTransactionTestFilter();
        LastMileMonitorInstanceFilter lmmif = new LastMileMonitorInstanceFilter();
        
        //lmtf.getType().add("LMTX");
        lmmif.getStatus().add("ACTIVE");
        lmtf.setLastMileMonitorInstanceFilter(lmmif);
        
        LastMileTestRequest lmtr = new LastMileTestRequest();
        lmtr.getLastMileTransactionTestFilter().add(lmtf);
        
        ActiveXFTestRequest atr = new ActiveXFTestRequest();
        atr.setLastMileTestRequest(lmtr);
        ActiveXFProductRequest apr = new ActiveXFProductRequest();
        apr.setActiveXFTestRequest(atr);

        ProductRequest pr = new ProductRequest();
        pr.setActiveXFProductRequest(apr);
        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setProductRequest(pr);
    
        request.setUser(user);
        request.setAccountRequest(accountRequest);

        AccountServiceResponse response = GomezAccount.retrieve(request);
        if( ! response.getStatusType().equals(StatusType.SUCCESS)){
            if (debug) log.info(response.getMessage());
            System.exit(-1);
        };


        
        int size = response.getGPNData().getMonitors().getMonitor().size();
       int[] monitorIds = new int[size];
       
        String[] monitorNames = new String[size];
        String[] monitorStatus = new String[size];

    //    List<MonitorInstanceRef> monitorInstances = new ArrayList<MonitorInstanceRef>();

        for (int i = 0; i < size; i++) {
        	Monitor monitor = response.getGPNData().getMonitors().getMonitor().get(i);
        	monitorStatus[i] = monitor.getStatus();
            monitorIds[i] = monitor.getId().intValue();
            monitorNames[i] = monitor.getDescription();
            int inputMonitor = Integer.parseInt(inputMonitorID);
            if (monitorIds[i] == inputMonitor) {
            try {
            int size2 = 0;
            ArrayOfMonitorInstanceRef monitorInstances = monitor.getMonitorInstances();
            if (monitorInstances.getMonitorInstanceRef() != null) {
            if (debug) log.info("Monitor Name: " + monitorNames[i]);
            if (debug) log.info("Monitor Status: " + monitorStatus[i]);
            if (debug) log.info("Monitor Id: " + monitorIds[i]); 
            size2 = monitorInstances.getMonitorInstanceRef().size(); 
            if (debug) log.info("Amount Of Monitor Instances: " + size2);
            int[] instanceIDs = new int[size2];
            for (int ii = 0; ii < size2; ii++) {      
            instanceIDs[ii] = monitorInstances.getMonitorInstanceRef().get(ii).getId();
            if (debug) log.info("Monitor Instances ID: " + instanceIDs[ii]); 
            }
            return instanceIDs;
            }
       	    } catch (Throwable ex) {
       	    	//Throwing away exceptions caused by monitors that do not have instances - shouldnt be a problem now due to only executing code with possible nullpointers when monitorID's match.
    	    	//if (debug) log.info("Uncaught exception checking monitor instances - " + ex.getMessage());
    	        //ex.printStackTrace(System.err);
    	    }
            
            }


           
        }     
        
        if (debug) log.info("Finished New (old) API Call");
               

    	    } catch (Throwable ex) {
    	    	if (debug) log.info("Uncaught exception geting monitor details - " + ex.getMessage());
    	        //ex.printStackTrace(System.err);
    	    }
		return null;
    	
}
}