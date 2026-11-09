package com.att.tdp.popcorn_palace.errors;

import org.springframework.http.HttpStatus;


public record ApiError(HttpStatus statusCode, String message)
{

}
