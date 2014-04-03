/**
 * 
 */
package org.jocean.syncfsm.api.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author isdom
 *
 */
@Retention(RetentionPolicy.RUNTIME) 
public @interface OnEvent {
	public abstract String event();
}
