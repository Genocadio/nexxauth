package com.nexxserve.nexxauth.entity;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.security.SecureRandom;

/**
 * Generates random non-sequential Long IDs to prevent enumeration attacks.
 * Used on entities where sequential IDs would leak information (e.g. Organisation).
 * The ID space is 1..9007199254740991 (safe for JSON JavaScript numbers).
 */
public class RandomLongIdGenerator implements IdentifierGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MAX = 9_007_199_254_740_991L; // 2^53 - 1

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object)
            throws HibernateException {
        return generateRandomId();
    }

    /** Generate a random non-sequential long ID. Can be called from services
     *  that need to set the ID before persisting (e.g. when overriding the
     *  BaseEntity sequential strategy for specific entities). */
    public static long generateRandomId() {
        long id;
        do {
            id = RANDOM.nextLong(1, MAX + 1);
        } while (id <= 0);
        return id;
    }
}
