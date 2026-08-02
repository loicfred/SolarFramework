package org.solarframework.db.api;

/**
 * The one column kind a read can leave behind.
 *
 * <p>Associations need nothing here: a read nulls every placeholder it hydrated
 * ({@code DBInstanceService.dropPlaceholders}), so an association field is a value or it is absent, and a
 * getter guards itself with {@code x == null}. Leave those fields uninitialized, so null keeps meaning
 * "ask the database" - unless the entity is also built and used without one, the way the tournament
 * objects are, where {@code = new ArrayList<>()} is what lets a hand-built graph grow on its own.
 * Blobs cannot say it that way - a {@code byte[]} has two
 * states and lazy loading needs three, holding bytes, known to be NULL, and never fetched - so the third
 * one is the sentinel below.
 */
public final class Lazy {
    private Lazy() {}

    /**
     * Marks a blob column the framework left out of a read, matched by identity. Declare the field as
     * {@code private byte[] avatar = Lazy.UNLOADED;} and read it through a getter that calls
     * {@code refetchAttribute} while {@link #unloaded(byte[])} holds.
     *
     * <p>Reads replace blob columns with a NULL literal and restore the sentinel afterwards, so an entity
     * never carries the payload unless somebody asks for it; writes skip every column still holding it,
     * which is what keeps an unread blob from being overwritten with NULL.
     */
    public static final byte[] UNLOADED = new byte[0];

    /** True when the blob was never fetched - as opposed to fetched and NULL, which is {@code null}. */
    public static boolean unloaded(byte[] blob) {
        return blob == UNLOADED;
    }
}
