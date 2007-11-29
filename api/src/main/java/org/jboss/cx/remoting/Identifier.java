package org.jboss.cx.remoting;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 *
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Identifier {
    String uri();
}
