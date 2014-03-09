/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public abstract class AbstractFlow 
	implements EventNameAware, 
		EventHandlerAware, 
		EventReceiverAware, 
		EndReasonSource {

	public EventInvoker selfInvoker(final String methodName) {
		return DefaultInvoker.of(this, methodName);
	}
	
	@Override
	public void setEventHandler(final EventHandler handler) {
		this._handler = handler;
	}

	@Override
	public void setEventName(final String event) {
		this._event = event;
	}
	
	@Override
	public void setEventReceiver(final EventReceiver receiver) {
		_receiver = receiver;
	}
	
	@Override
	public Object getEndReason() {
		return _endreason;
	}

	protected EventReceiver	selfEventReceiver() {
		return	this._receiver;
	}
	
	protected String	currentEvent() {
		return	this._event;
	}
	
	protected EventHandler	currentEventHandler() {
		return	this._handler;
	}

	protected void 	setEndReason(final Object endreason) {
		this._endreason = endreason;
	}
	
	private String			_event;
	private EventHandler 	_handler;
	private Object 			_endreason;
	private EventReceiver	_receiver;
}
