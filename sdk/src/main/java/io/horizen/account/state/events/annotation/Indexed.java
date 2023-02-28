package io.horizen.account.state.events.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When creating a new event, use this annotation together with @getter to annotate indexed event parameters in the constructor.
 * Check usage example at io.horizen.account.events and EthereumEventTest in io.horizen.account.event.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Indexed { }
