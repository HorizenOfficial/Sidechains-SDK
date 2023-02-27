package com.horizen.account.state.events.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When creating a new event, use this annotation together with @getter to annotate indexed event parameters in the constructor.
 * Check usage example at com.horizen.account.events and EthereumEventTest in com.horizen.account.event.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Indexed { }
