package com.antoniosifov.popcornpalace.payment;

/** The payment provider could not be reached or answered with an error. */
public class PaymentProviderException extends RuntimeException
{
    public PaymentProviderException(String message)
    {
        super(message);
    }
}
