package org.jocean.syncfsm.api;

import org.jocean.idiom.Pair;

public interface EventHandler {
	
	//	get event handler's name
	public String getName();
	
	//	process event(with args) and return next event handler
	public Pair<EventHandler,Boolean> process( final String event, final Object[] args );
}
