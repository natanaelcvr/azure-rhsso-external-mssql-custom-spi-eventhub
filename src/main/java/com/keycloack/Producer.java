package com.keycloack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;

import org.jboss.logging.Logger;
import org.keycloak.common.util.StackUtil;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.sessions.AuthenticationSessionModel;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;

public class Producer {

  protected static final Logger logger = Logger.getLogger(Producer.class);
  private KeycloakSession session;

  public void publishEvent(Event event, KeycloakSession session){

		String eventType = "normalEvent";
    StringBuilder sb = new StringBuilder();

		sb.append("{");
		sb.append("\"type\":");
		sb.append("\""+event.getType()+"\"");
		sb.append(", \"realmId\":");
		sb.append("\""+event.getRealmId()+"\"");
		sb.append(", \"clientId\":");
		sb.append("\""+event.getClientId()+"\"");
		sb.append(", \"userId\":");
		sb.append("\""+event.getUserId()+"\"");
		sb.append(", \"ipAddress\":");
		sb.append("\""+event.getIpAddress()+"\"");

		if (event.getError() != null) {
			sb.append(", \"error\":");
			sb.append("\""+event.getError()+"\"");
		}

		if (event.getDetails() != null) {
			for (Map.Entry<String, String> e : event.getDetails().entrySet()) {
				sb.append(", ");
				sb.append("\""+e.getKey()+"\"");
				if (e.getValue() == null || e.getValue().indexOf(' ') == -1) {
					sb.append(":");
					sb.append("\""+e.getValue()+"\"");
				} else {
					sb.append(":");
					sb.append("\""+e.getValue()+"\"");
				}
			}
		}

		AuthenticationSessionModel authSession = session.getContext().getAuthenticationSession(); 
		if(authSession!=null) {
			sb.append(", \"authSessionParentId\":");
			sb.append("\""+authSession.getParentSession().getId()+"\"");
			sb.append(", \"authSessionTabId\":");
			sb.append("\""+authSession.getTabId()+"\"");
		}

		if(logger.isTraceEnabled()) {
			setKeycloakContext(sb);

			if (StackUtil.isShortStackTraceEnabled()) {
				sb.append(", \"stackTrace\":").append("\""+StackUtil.getShortStackTrace()+"\"");
			}
		}
		sb.append("}");

		String msgSTring = sb.toString();
		logger.info(msgSTring);

		send(msgSTring, eventType);
  }

  public void publishAdminEvent(AdminEvent adminEvent, KeycloakSession session){

		String eventType = "adminEvent";
    StringBuilder sb = new StringBuilder();

		sb.append("{");
		sb.append("\"operationType\":");
		sb.append("\""+adminEvent.getOperationType()+"\"");
		sb.append(", \"realmId\":");
		sb.append("\""+adminEvent.getAuthDetails().getRealmId()+"\"");
		sb.append(", \"clientId\":");
		sb.append("\""+adminEvent.getAuthDetails().getClientId()+"\"");
		sb.append(", \"userId\":");
		sb.append("\""+adminEvent.getAuthDetails().getUserId()+"\"");
		sb.append(", \"ipAddress\":");
		sb.append("\""+adminEvent.getAuthDetails().getIpAddress()+"\"");
		sb.append(", \"resourceType\":");
		sb.append("\""+adminEvent.getResourceTypeAsString()+"\"");
		sb.append(", \"resourcePath\":");
		sb.append("\""+adminEvent.getResourcePath()+"\"");

		if (adminEvent.getError() != null) {
			sb.append(", \"error\":");
			sb.append("\""+adminEvent.getError()+"\"");
		}

		if(logger.isTraceEnabled()) {
			setKeycloakContext(sb);
		}
		sb.append("}");
		
		String msgSTring = sb.toString();
		logger.info(msgSTring);

		send(msgSTring, eventType);
  }

  private void setKeycloakContext(StringBuilder sb) {
    KeycloakContext context = session.getContext();
    UriInfo uriInfo = context.getUri();
    HttpHeaders headers = context.getRequestHeaders();
    if (uriInfo != null) {
			sb.append(", \"requestUri\":");
			sb.append("\""+uriInfo.getRequestUri().toString()+"\"");
    }

    if (headers != null) {
			sb.append(", \"cookies:\"[");
			boolean f = true;
			for (Map.Entry<String, Cookie> e : headers.getCookies().entrySet()) {
				if (f) {
					f = false;
				} else {
					sb.append(", ");
				}
				sb.append("\""+e.getValue().toString()+"\"");
			}
			sb.append("]");
    }
  }

  private void send(String msg, String eventType) {

		/*
		Set 'AZURE_EVENT_HUB_CONNECTION_STRING' and 'AZURE_EVENT_HUB_NAME'
		as environment variables with connection informations
		[OPTIONAL] set 'AZURE_EVENT_HUB_ADMIN_NAME' if admin events should
		be sent to other Azure Event Hub
		*/
		String connectionString = System.getenv("AZURE_EVENT_HUB_CONNECTION_STRING");
		String eventHubName = System.getenv("AZURE_EVENT_HUB_NAME");
		String adminEventHubName = System.getenv("AZURE_EVENT_HUB_ADMIN_NAME");

		if(!eventType.equals("normalEvent")){
			if(adminEventHubName == null || adminEventHubName.isEmpty()){
				logger.warn("AZURE_EVENT_HUB_ADMIN_NAME environment variable is not setted. "+
				            "AZURE_EVENT_HUB_NAME will be used instead.");
			}else{
				eventHubName = adminEventHubName;
			}
		}

		try {
			if(connectionString == null || eventHubName == null){
				throw new NullPointerException();
			}
		} catch (NullPointerException e) {
				logger.error("Please set 'AZURE_EVENT_HUB_CONNECTION_STRING' and "+
				             "'AZURE_EVENT_HUB_NAME' environment variables with connection informations",e);
		}

		try (EventHubProducerClient producer = new EventHubClientBuilder()
				.connectionString(connectionString, eventHubName)
				.buildProducerClient()) {

			List<EventData> allEvents = Arrays.asList(new EventData(msg));
			EventDataBatch eventDataBatch = producer.createBatch();

			for (EventData eventData : allEvents) {
				
				if (!eventDataBatch.tryAdd(eventData)) {
					
					producer.send(eventDataBatch);
					eventDataBatch = producer.createBatch();

					if (!eventDataBatch.tryAdd(eventData)) {
						throw new IllegalArgumentException("Event is too large for an empty batch. Max size: "
								+ eventDataBatch.getMaxSizeInBytes());
					}
				}
			}
			
			if (eventDataBatch.getCount() > 0) {
				producer.send(eventDataBatch);
			}
		}
	}
}
