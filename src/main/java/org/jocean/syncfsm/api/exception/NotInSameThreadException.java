/**
 * 
 */
package org.jocean.syncfsm.api.exception;

/**
 * @author isdom
 *
 */
public class NotInSameThreadException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1780161954101692713L;

    public NotInSameThreadException(String message) {
        super(message);
    }
}
