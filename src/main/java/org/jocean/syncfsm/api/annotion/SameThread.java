/**
 * 
 */
package org.jocean.syncfsm.api.annotion;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author isdom
 *
 */
@Retention(RetentionPolicy.RUNTIME) 
public @interface SameThread {
	public abstract boolean value() default true;
}
