package com.webizon.tenancy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as intentionally operating across tenants.
 *
 * <p>Use this sparingly. The only legitimate reasons:
 * <ul>
 *   <li>Platform-admin tooling ({@code platform_admin} role)</li>
 *   <li>Reconciliation, backup, and cleanup jobs</li>
 *   <li>Global configuration reads</li>
 * </ul>
 *
 * <p>Methods annotated with this must include the justification in the {@link #reason()}
 * field. Code review MUST verify the justification is legitimate.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowCrossTenant {
    String reason();
}
