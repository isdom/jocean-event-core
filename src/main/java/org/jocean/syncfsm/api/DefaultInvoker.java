/**
 * 
 */
package org.jocean.syncfsm.api;

import java.lang.reflect.Method;

import org.jocean.syncfsm.api.annotion.OnEvent;


/**
 * @author isdom
 *
 */
public class DefaultInvoker implements EventInvoker {
	private DefaultInvoker(final Object target, final Method method) {
		this._target = target;
		this._method = method;
		this._method.setAccessible(true);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <RET> RET invoke(final Object[] args) throws Exception {
		return (RET)this._method.invoke(this._target, args);
	}
	
	@Override
	public String getBindedEvent() {
		final OnEvent onEvent = _method.getAnnotation(OnEvent.class);
		
		return	null != onEvent ? onEvent.event() : null;
	}
	
	public static DefaultInvoker of(final Object target, final String methodName) {
		if ( null == target ) {
			return null;
		}
		final Method[] methods = target.getClass().getDeclaredMethods();
		for ( Method method : methods ) {
			if ( method.getName().equals(methodName) ) {
				return new DefaultInvoker(target, method);
			}
		}
		return null;
	}
	
	private final Object _target;
	private final Method _method;
}
