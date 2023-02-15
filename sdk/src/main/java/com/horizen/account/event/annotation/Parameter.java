package com.horizen.account.event.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When creating a new event, every parameter in the constructor needs to be annotated by this annotation combined with @getter.
 * Check usage example at com.horizen.account.events and EthereumEventTest in com.horizen.account.event.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Parameter {
    int value();
}
