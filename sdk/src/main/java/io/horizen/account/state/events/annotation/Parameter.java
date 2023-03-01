package io.horizen.account.state.events.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When creating a new event, every parameter in the constructor needs to be annotated by this annotation combined with @getter.
 * Check usage example at io.horizen.account.events and EthereumEventTest in io.horizen.account.event.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Parameter {
    int value();
}
