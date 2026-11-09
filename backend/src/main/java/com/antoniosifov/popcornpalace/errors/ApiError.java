package com.antoniosifov.popcornpalace.errors;

import org.springframework.http.HttpStatus;


public record ApiError(HttpStatus statusCode, String message)
{

}
