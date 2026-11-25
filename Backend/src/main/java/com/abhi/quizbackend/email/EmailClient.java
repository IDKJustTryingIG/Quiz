package com.abhi.quizbackend.email;

/** Sends one email through a provider. */
public interface EmailClient {

    /**
     * @return the provider's id of the email
     * @throws EmailSendException if the provider did not send it
     */
    String send(OutgoingEmail email);
}
