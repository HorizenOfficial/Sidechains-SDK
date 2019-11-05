package com.horizen.utils;


import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Retention(RUNTIME)
@Target(PARAMETER)
public @interface NamedArg {

    public String value();

    public String defaultValue() default "";
}
