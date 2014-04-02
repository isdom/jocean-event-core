/**
 * 
 */
package org.jocean.syncfsm.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jocean.idiom.COWCompositeSupport;
import org.jocean.idiom.ExectionLoop;
import org.jocean.idiom.Visitor;

/**
 * @author isdom
 *
 */
public abstract class AbstractFlow<FLOW>
	implements EventNameAware, 
		EventHandlerAware, 
		FlowLifecycleAware, 
		EndReasonSource,
		ExectionLoopAware
		{

	public <INTF> INTF getInterfaceAdapter(final Class<INTF> intfCls) {
		@SuppressWarnings("unchecked")
		INTF ret = (INTF)this._adapters.get(intfCls);
		
		if ( null != ret ) {
			return ret;
		}
		return createAndSaveInterfaceAdapter(intfCls);
	}
	
	@SuppressWarnings("unchecked")
	private <INTF> INTF createAndSaveInterfaceAdapter(final Class<INTF> intfCls) {
		final INTF intf = SyncFSMUtils.buildInterfaceAdapter(intfCls, this._receiver);
		final Object oldIntf = this._adapters.putIfAbsent(intfCls, intf);
		if ( null != oldIntf ) {
			return (INTF)oldIntf;
		}
		else {
			return intf;
		}
	}
	
	@SuppressWarnings("unchecked")
	public FLOW addFlowLifecycleListener(final FlowLifecycleListener<FLOW> lifecycle) {
		this._lifecycleSupport.addComponent(lifecycle);
		return (FLOW)this;
	}
	
	@SuppressWarnings("unchecked")
	public FLOW removeFlowLifecycleListener(final FlowLifecycleListener<FLOW> lifecycle) {
		this._lifecycleSupport.removeComponent(lifecycle);
		return (FLOW)this;
	}
	
	public EventInvoker selfInvoker(final String methodName) {
		return DefaultInvoker.of(this, methodName);
	}
	
	@Override
	public void setEventHandler(final EventHandler handler) throws Exception {
		this._handler = handler;
	}

	@Override
	public void setEventName(final String event) throws Exception {
		this._event = event;
	}
	
	@Override
	public void afterEventReceiverCreated(final EventReceiver receiver) throws Exception {
		this._receiver = receiver;
		this._lifecycleSupport.foreachComponent(new Visitor<FlowLifecycleListener<FLOW>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void visit(final FlowLifecycleListener<FLOW> lifecycle) throws Exception {
				lifecycle.afterEventReceiverCreated((FLOW)AbstractFlow.this, receiver);
			}});
	}
	
	@Override
	public void afterFlowDestroy() throws Exception {
		this._lifecycleSupport.foreachComponent(new Visitor<FlowLifecycleListener<FLOW>>() {
			@SuppressWarnings("unchecked")
			@Override
			public void visit(final FlowLifecycleListener<FLOW> lifecycle) throws Exception {
				lifecycle.afterFlowDestroy((FLOW)AbstractFlow.this);
			}});
	}
	
    @Override
    public void setExectionLoop(final ExectionLoop exectionLoop) {
        this._exectionLoop = exectionLoop;
    }
    
	@Override
	public Object getEndReason() throws Exception {
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
	
	protected ExectionLoop selfExectionLoop() {
	    return this._exectionLoop;
	}
	
	private String			_event;
	private EventHandler 	_handler;
	private Object 			_endreason;
	private EventReceiver	_receiver;
	private ExectionLoop   _exectionLoop;
	
	private final COWCompositeSupport<FlowLifecycleListener<FLOW>> _lifecycleSupport
		= new COWCompositeSupport<FlowLifecycleListener<FLOW>>();
	private final ConcurrentMap<Class<?>, Object> _adapters = 
			new ConcurrentHashMap<Class<?>, Object>();
}
