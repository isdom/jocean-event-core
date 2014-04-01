/**
 * 
 */
package org.jocean.syncfsm.container;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jocean.idiom.COWCompositeSupport;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.idiom.Visitor;
import org.jocean.syncfsm.api.EndReasonSource;
import org.jocean.syncfsm.api.EventHandler;
import org.jocean.syncfsm.api.EventHandlerAware;
import org.jocean.syncfsm.api.EventNameAware;
import org.jocean.syncfsm.api.EventReceiver;
import org.jocean.syncfsm.api.EventReceiverSource;
import org.jocean.syncfsm.api.ExectionLoop;
import org.jocean.syncfsm.api.ExectionLoopAware;
import org.jocean.syncfsm.api.FlowLifecycleAware;
import org.jocean.syncfsm.api.FlowSource;
import org.jocean.syncfsm.common.FlowStateChangeListener;
import org.jocean.syncfsm.common.FlowTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author isdom
 *
 */
public class FlowContainer {
	
    private static final Logger LOG = 
    	LoggerFactory.getLogger(FlowContainer.class);

    private static final AtomicInteger ALL_CONTAINER_COUNTER = new AtomicInteger(0);
    
	public FlowContainer(final String name) {
    	this.name = ( null != name ? name : super.toString() );	// ensure this.name is not null
    	this._id = ALL_CONTAINER_COUNTER.incrementAndGet();
    }

	public EventReceiverSource genEventReceiverSource() {
		return	new EventReceiverSource() {

			@Override
			public <FLOW> EventReceiver create(final FlowSource<FLOW> source) {
		        //  create new receiver
		        final FLOW flow = source.getFlow();
		        return  createEventReceiverOf(flow, source.getInitHandler(flow), source.getExectionLoop(flow));
			}

			@Override
			public <FLOW> EventReceiver create(
					final FLOW flow,
					final EventHandler initHandler,
                    final ExectionLoop exectionLoop
					) {
				return createEventReceiverOf(flow, initHandler, exectionLoop);
			}};
	}
	
	public FlowTracker genFlowTracker() {
		
		return	new FlowTracker() {
			@Override
			public void registerFlowStateChangeListener(
					final FlowStateChangeListener listener) {
				if ( null == listener ) {
					LOG.warn("registerFlowStateChangeListener: listener is null, just ignore");
				}
				else {
					if ( !_flowStateChangeListenerSupport.addComponent(listener) ) {
						LOG.warn("registerFlowStateChangeListener: listener {} has already registered", 
								listener);
					}
				}
			}
			
			@Override
			public void unregisterFlowStateChangeListener(
					final FlowStateChangeListener listener) {
				if ( null == listener ) {
					LOG.warn("unregisterFlowStateChangeListener: listener is null, just ignore");
				}
				else {
					_flowStateChangeListenerSupport.removeComponent(listener);
				}
			}
		};
	}

	private <FLOW> EventReceiver createEventReceiverOf(
	        final FLOW flow, 
	        final EventHandler initHandler,
            final ExectionLoop exectionLoop
	        ) {
		//	create new receiver
		final FlowContextImpl ctx = initFlowCtx(flow, initHandler, exectionLoop);
		
        if ( flow instanceof ExectionLoopAware ) {
            try {
                ((ExectionLoopAware)flow).setExectionLoop(exectionLoop);
            }
            catch (Exception e) {
                LOG.error("exception when invoke flow {}'s setExectionLoop, detail: {}",
                        flow, ExceptionUtils.exception2detail(e));
            }
        }
        
        final EventReceiver newReceiver = genEventReceiverWithCtx(ctx);
        
		if ( flow instanceof FlowLifecycleAware ) {
			try {
				((FlowLifecycleAware)flow).afterEventReceiverCreated(newReceiver);
			}
			catch (Exception e) {
				LOG.error("exception when invoke flow {}'s afterEventReceiverCreated, detail: {}",
						flow, ExceptionUtils.exception2detail(e));
			}
		}
		
		return	newReceiver;
	}
	
	/**
	 * @param ctx
	 * @return
	 */
	private EventReceiver genEventReceiverWithCtx(final FlowContextImpl ctx) {
		return	new EventReceiver() {

			@Override
			public boolean acceptEvent(final String event, final Object... args) throws Exception {
		        try {
		            return ctx.processEvent(event, args);
		        }
		        catch (final Exception e) {
		            LOG.error("exception when flow {}.processEvent, detail:{}, try end flow", 
		                    ctx.getFlow(), ExceptionUtils.exception2detail(e));
		            destroyFlowCtx(ctx);
		            throw e;
		        }
			}};
	}
	
	public String getName() {
		return this.name;
	}

	public int getId() {
		return	this._id;
	}

	public int getFlowTotalCount() {
		return this.totalFlowCount.get();
	}

	public long getDealHandledCount() {
		return dealHandledCount.get();
	}
	
	public long getDealCompletedCount() {
		return dealCompletedCount.get();
	}

	public long getDealBypassCount() {
		return dealBypassCount.get();
	}

	private FlowContextImpl initFlowCtx(
	        final Object flow, 
	        final EventHandler initHandler,
            final ExectionLoop exectionLoop 
	        ) {
		final FlowContextImpl newCtx = createFlowCtx(flow, initHandler, exectionLoop);
		
		this._flowContexts.add(newCtx);
		
		incDealHandledCount();
		
		// add new context
		totalFlowCount.incrementAndGet();
		
		return	newCtx;
	}
	
	private void destroyFlowCtx(final FlowContextImpl ctx) {
		if ( this._flowContexts.remove(ctx) ) {
			//	移除操作有效
			this.totalFlowCount.decrementAndGet();
		}
		
		if ( ctx.isFlowHasEndReason ) {
			// fetch end reason
			try {
				ctx.setEndReason( ((EndReasonSource)ctx.getFlow()).getEndReason() );
			}
			catch (Exception e) {
				LOG.error("exception when getEndReason: flow {}, detail: {}",
						ctx.getFlow(), ExceptionUtils.exception2detail(e));
			}
		}
		
		try {
			ctx.destroy();
		}
		catch (Exception e) {
			LOG.error("exception when [{}] destroyFlowCtx for flow {}, detail: {}", 
			        name, ctx.getFlow(), ExceptionUtils.exception2detail(e) );
		}
		
		incDealCompletedCount();
		
		if ( ctx.getFlow() instanceof FlowLifecycleAware ) {
			final FlowLifecycleAware flow = (FlowLifecycleAware)ctx.getFlow();
			try {
				flow.afterFlowDestroy();
			}
			catch (Exception e) {
				LOG.error("exception when invoke flow {}'s afterFlowDestroy, detail: {}",
						flow, ExceptionUtils.exception2detail(e));
			}
		}

		_flowStateChangeListenerSupport.foreachComponent(new Visitor<FlowStateChangeListener>() {

			@Override
			public void visit(final FlowStateChangeListener listener) throws Exception {
				listener.afterFlowDestroy(ctx);
			}});
	}

	private void setCurrentAcceptedEventFor(final FlowContextImpl ctx, final String event) {
		if ( ctx.isFlowEventNameAware ) {
			try {
				((EventNameAware)ctx.getFlow()).setEventName(event);
			}
			catch (Exception e) {
				LOG.error("exception when setEventName: event {} to flow {}, detail: {}",
					event, ctx.getFlow(), ExceptionUtils.exception2detail(e));
			}
		}
	}
	
	private boolean dispatchEventForCtx(final FlowContextImpl ctx, final String event, final Object[] args) {
		final EventHandler currentHandler = ctx.getCurrentHandler();
		if ( null == currentHandler ) {
			LOG.error("Internal Error: current handler is null, remove flow {}", ctx.getFlow());
			destroyFlowCtx(ctx);
			return	false;
		}
		
		setCurrentAcceptedEventFor(ctx, event);
		
		EventHandler nextHandler = null;
		boolean		eventHandled = false;

		try {
			Pair<EventHandler, Boolean> result = currentHandler.process(event, args);
			nextHandler = result.getFirst();
			eventHandled = result.getSecond();
		}
		catch (Exception e) {
			LOG.error("exception when {}.acceptEvent, detail:{}", ctx.getCurrentHandler(), 
					ExceptionUtils.exception2detail(e));
		}
		finally {
			setCurrentAcceptedEventFor(ctx, null);
		}
		
		if ( null == nextHandler ) {
			// handled and next handler is null
			if ( LOG.isDebugEnabled() ) {
				LOG.debug("flow ({}) will end normally.", ctx.getFlow());
			}
			
			destroyFlowCtx(ctx);
			return	eventHandled;
		}
		else if ( ctx.getCurrentHandler().equals( nextHandler ) ) {
			// no change
		}
		else {
			final EventHandler newHandler = nextHandler;
			
			//	fire listener to invoke beforeFlowDispatchTo
			this._flowStateChangeListenerSupport.foreachComponent(
 					new Visitor<FlowStateChangeListener>() {
				@Override
				public void visit(final FlowStateChangeListener listener)
						throws Exception {
					listener.beforeFlowDispatchTo(newHandler, ctx, event, args);
				}});
			
			setCurrentHandlerFor(ctx, newHandler);
		}
		
        try {
            ctx.endOfDispatchEvent();
        }
        catch (Exception e) {
            LOG.error("exception when flow ({}).endOfDispatchEvent, detail: {}, try end flow", 
                    ctx.getFlow(), ExceptionUtils.exception2detail(e));
            destroyFlowCtx(ctx);
        }
        
		return	eventHandled;
	}

	/**
	 * @param ctx
	 * @param newHandler
	 */
	private void setCurrentHandlerFor(final FlowContextImpl ctx,
			final EventHandler newHandler) {
		ctx.setCurrentHandler(newHandler);
		if ( ctx.isFlowEventHandlerAware ) {
			try {
				((EventHandlerAware)ctx.getFlow()).setEventHandler(newHandler);
			}
			catch (Exception e) {
				LOG.error("exception when setEventHandler: handler {} to flow {}, detail: {}",
					newHandler, ctx.getFlow(), ExceptionUtils.exception2detail(e));
			}
		}
	}

	private void incDealHandledCount() {
		dealHandledCount.incrementAndGet();
	}
	
	private void incDealCompletedCount() {
		dealCompletedCount.incrementAndGet();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.name + "-" + this._id;
	}

	private <FLOW> FlowContextImpl createFlowCtx(
		final FLOW flow,
		final EventHandler initHandler,
        final ExectionLoop exectionLoop
		) {
		final FlowContextImpl ctx = new FlowContextImpl(flow, exectionLoop, this._dispatcher);
		
		ctx.isFlowEventNameAware = (flow instanceof EventNameAware);
		ctx.isFlowEventHandlerAware = (flow instanceof EventHandlerAware);
		ctx.isFlowHasEndReason = (flow instanceof EndReasonSource);
		
		setCurrentHandlerFor(ctx, initHandler);
		
		return ctx;
	}
	
	private final FlowContextImpl.Dispatcher _dispatcher = new FlowContextImpl.Dispatcher() {

        @Override
        public void dispatchEvent(
                final FlowContextImpl ctx, 
                final String event,
                final Object[] args) {
            dispatchEventForCtx(ctx, event, args);
        }};
        
	private	final Set<FlowContextImpl> _flowContexts = 
			new ConcurrentSkipListSet<FlowContextImpl>();
	
	private final String		name;
	private	final int			_id;
	
	private	final AtomicInteger	totalFlowCount = new AtomicInteger(0);
	
	private	final AtomicLong dealHandledCount = new AtomicLong(0);
	private	final AtomicLong dealCompletedCount = new AtomicLong(0);
	private	final AtomicLong dealBypassCount = new AtomicLong(0);
	
	private final COWCompositeSupport<FlowStateChangeListener> _flowStateChangeListenerSupport
		= new COWCompositeSupport<FlowStateChangeListener>();
	
}
