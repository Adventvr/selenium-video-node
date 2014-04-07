package com.mooo.aimmac23.hub.proxy;

import java.util.logging.Logger;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.internal.HttpClientFactory;

import com.mooo.aimmac23.hub.HubVideoRegistry;

public class VideoProxy extends DefaultRemoteProxy {

    private static final Logger log = Logger.getLogger(VideoProxy.class.getName());

	private String serviceUrl;
	boolean isCurrentlyRecording = false;
	private HttpClient client;
	private HttpHost remoteHost;
	
	public VideoProxy(RegistrationRequest request, Registry registry) {
		super(transformRegistration(request), registry);
		
		serviceUrl = getRemoteHost() + "/extra/VideoRecordingControlServlet";
		
		remoteHost = new HttpHost(getRemoteHost().getHost(),
				getRemoteHost().getPort());
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        client = httpClientFactory.getHttpClient();
	}
	
	static RegistrationRequest transformRegistration(RegistrationRequest request) {
		int maxSessions = request.getConfigAsInt(RegistrationRequest.MAX_SESSION, 1);
		request.getConfiguration().put(RegistrationRequest.MAX_SESSION, 1);
		
		if(maxSessions != 1) {
			log.warning("Reducing " + RegistrationRequest.MAX_SESSION + " value to 1: Video node does not support concurrent sessions");
		}
		
		for(DesiredCapabilities caps : request.getCapabilities()) {
			Object maxInstances = caps.getCapability(RegistrationRequest.MAX_INSTANCES);
			caps.setCapability(RegistrationRequest.MAX_INSTANCES, "1");
			if(maxInstances != null && !"1".equals(maxInstances)) {
				log.warning("Reducing " + RegistrationRequest.MAX_INSTANCES + " for browser " + caps.getBrowserName() + 
						" to 1: Video node does not support concurrent sessions");
			}
		}
			
		return request;
	}
	
	@Override
	public void beforeSession(TestSession arg0) {
		super.beforeSession(arg0);
		
		BasicHttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest(
                "POST", serviceUrl + "?command=start");
		
        try {
			HttpResponse response = client.execute(remoteHost, r);
			if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				log.warning("Could not start video reporting: " + EntityUtils.toString(response.getEntity()));
				return;
			}
			
			isCurrentlyRecording = true;
		} catch (Exception e) {
			log.warning("Could not start video reporting due to exception: " + e.getMessage());
			e.printStackTrace();
			return;
		}
	}
	
	@Override
	public void afterSession(TestSession session) {
		super.afterSession(session);
		
		BasicHttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest(
                "POST", serviceUrl + "?command=stop");
		
        try {
			HttpResponse response = client.execute(remoteHost, r);
			if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				log.warning("Could not stop video reporting: " + EntityUtils.toString(response.getEntity()));
			}
			
			isCurrentlyRecording = false;
			
			// check that the session didn't explode on startup
			if(session.getExternalKey() != null) {
				JSONObject json = new JSONObject(EntityUtils.toString(response.getEntity()));
				String fileKey = json.getString("filekey");
				HubVideoRegistry.copyVideoToHub(session.getExternalKey(), fileKey, getRemoteHost());
				

			}
		} catch (Exception e) {
			log.warning("Could not stop video reporting due to exception: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
