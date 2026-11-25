package com.abhi.quizbackend.email;

/**
 * The provider did not send the email.
 *
 * @see #isTemporary()
 */
public class EmailSendException extends RuntimeException {

    private final boolean temporary;

    public EmailSendException(String message, boolean temporary, Throwable cause) {
        super(message, cause);
        this.temporary = temporary;
    }

    /**
     * True if trying again later can help: a rate limit, a server error or a
     * network error. False if the provider refused the email itself, for
     * example a recipient the test sender may not send to.
     */
    public boolean isTemporary() {
        return temporary;
    }
}
