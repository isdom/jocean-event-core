/**
 * 
 */
package org.jocean.syncfsm.api;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author isdom
 *
 */
public class BizStep implements Cloneable, EventHandler {

	private static final Logger LOG = 
        	LoggerFactory.getLogger(BizStep.class);
    
	public static String uniqueEvent(final String eventPrefix) {
		return ((eventPrefix != null ) ? eventPrefix : "")
				+ UUID.randomUUID().toString();
	}
	
    @Override
	protected BizStep clone() {
		try {
			final BizStep cloned = (BizStep)super.clone();
	    	cloned._isFrozen = false;
	    	
	    	return	cloned;
		} catch (CloneNotSupportedException e) {
			LOG.error("failed to clone: {}", ExceptionUtils.exception2detail(e));
		}
    	return null;
	}

	public BizStep(final String name) {
    	this._name = name;
    }
    
	public BizStep rename(final String name) {
		if ( !name.equals(this._name) ) {
			if ( !this._isFrozen ) {
				this._name = name;
				return	this;
			}
			else {
				return	this.clone().rename(name);
			}
		}
		else {
			return	this;
		}
	}
	
    public BizStep handler(final EventInvoker eventInvoker) {
    	if ( null == eventInvoker ) {
	    	LOG.warn("add handler failed, invoker is null.");
    		return	this;
    	}
    	
    	if ( !this._isFrozen ) {
    		final String bindedEvent = eventInvoker.getBindedEvent();
    		if ( null != bindedEvent ) {
				_handlers.put(bindedEvent, eventInvoker);
				return 	this;
    		}
    		else {
    	    	LOG.warn("add handler failed for {}, no binded event.", eventInvoker);
    			return	this;
    		}
    	}
    	else {
    		return	this.clone().handler(eventInvoker);
    	}
    }

    public BizStep freeze() {
        this._isFrozen = true;
    	return	this;
    }
    
    private final Map<String, EventInvoker> _handlers = 
    		new HashMap<String, EventInvoker>();

    private volatile String _name;
    
    private boolean _isFrozen = false;

    //	implements EventHandler
	@Override
	public String getName() {
		return _name;
	}

	@Override
	public Pair<EventHandler, Boolean> process(final String event, final Object[] args) {
		try {
			EventInvoker eventInvoker = this._handlers.get(event);
			
			if ( null != eventInvoker ) {
				return Pair.of((EventHandler)eventInvoker.invoke(args), true);
			}
			else {
			    if ( LOG.isDebugEnabled() ) {
    				LOG.debug("BizStep [{}] don't except event {} , just ignore", 
    						this._name, event);
			    }
				//	do not change state
				return Pair.of((EventHandler)this, false);
			}
		}
		catch (Exception e) {
			LOG.error("exception when process event {}, detail:{}", 
					event, ExceptionUtils.exception2detail(e));
			return Pair.of((EventHandler)this, false);
		}
	}
}
