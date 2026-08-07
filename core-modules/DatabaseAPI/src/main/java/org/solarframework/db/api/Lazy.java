package org.solarframework.db.api;

/**
 * Marks a blob column the framework left out of a read, matched by identity. A {@code byte[]} has two states
 * and lazy loading needs three - holding bytes, known to be NULL, and never fetched - so the third one is this
 * sentinel. Declare the field as {@code private byte[] avatar = Lazy.UNLOADED;} and read it through a getter
 * that calls {@code refetchAttribute} while {@link #unloaded(byte[])} holds.
 *
 * <p>Reads replace blob columns with a NULL literal and restore the sentinel afterwards; writes skip every
 * column still holding it, which keeps an unread blob from being overwritten with NULL.
 *
 * <p>Associations need no sentinel either, because Hibernate already ships one: the PersistentBag or proxy
 * a read leaves in the field faults itself on first access under {@code enable_lazy_load_no_trans}, even
 * though the EntityManager closed with the query - collections and single-valued associations alike. A
 * getter guards itself with {@code x == null} only to cover the other case, an instance built with
 * {@code new}, which Hibernate never touched and which needs an empty list it can grow before it is ever
 * saved.
 */
public final class Lazy {
    private Lazy() {}

    public static final byte[] UNLOADED = new byte[0];

    /** True when the blob was never fetched - as opposed to fetched and NULL, which is {@code null}. */
    public static boolean unloaded(byte[] blob) {
        return blob == UNLOADED;
    }
}
