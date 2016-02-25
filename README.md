## APMaaS Metrics Grabber Plugin

The APMaaS Metrics Grabber plugin utilized the web services available from Compuware's APMaaS offering to return the exact metrics for every APMaaS test run from a specified backbone test script. At this time,
only backbone tests are supported.

The plugin uses the APMaaS WSDL Interface to query the following metrics:

  1. Availability 

  2. Response Time 
  
 Each run of the monitor will collect information for a single APMaaS journey. The monitor will fail if there is no new data to be collected. This is expected behaviour. You should set your polling frequency around 1-2minutes to make sure you sync the data as quick as possible. 


Find further information in the [dynaTrace community]

