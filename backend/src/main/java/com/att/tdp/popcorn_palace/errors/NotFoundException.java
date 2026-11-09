package com.att.tdp.popcorn_palace.errors;

public class NotFoundException extends RuntimeException
{
    public NotFoundException(String message)
    {
        super(message);
    }
}