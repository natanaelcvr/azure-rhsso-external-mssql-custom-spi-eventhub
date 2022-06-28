package com.keycloack;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

public class CustomEventListenerProvider implements EventListenerProvider{

	public KeycloakSession session;

	public CustomEventListenerProvider(KeycloakSession keycloakSession){
		session = keycloakSession;
	}
	
	@Override
	public void onEvent(Event event) {

		Producer producer = new Producer();
		producer.publishEvent(event, session);
	}

	@Override
	public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {

		Producer producer = new Producer();
		producer.publishAdminEvent(adminEvent, session);
	}

	@Override
	public void close() {

	}

}
