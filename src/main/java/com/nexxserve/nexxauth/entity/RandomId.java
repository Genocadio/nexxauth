package com.nexxserve.nexxauth.entity;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Hibernate {@link IdGeneratorType} meta-annotation that pairs
 * {@link RandomLongIdGenerator} with the annotated field.
 * <p>
 * Replaces the deprecated {@code @GenericGenerator} / {@code @GeneratedValue(generator=...)}
 * pattern from Hibernate 6.
 *
 * <pre>
 * &#64;Id &#64;RandomId
 * private Long id;
 * </pre>
 */
@IdGeneratorType(RandomLongIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RandomId {
}
