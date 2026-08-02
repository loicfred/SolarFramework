package org.solarframework.discord.utils;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.Arrays;
import java.util.function.IntFunction;

/**
 * Arrow-button pagination, replacing the old "list every page in a StringSelectMenu" rows which
 * could never show more than 25 pages. Only the neighbouring pages are ever encoded, so a list is
 * paginated indefinitely as long as the user keeps pressing ▶.
 * <p>
 * Build the row with {@code makePageRow(...)} on {@link MyCMD} / {@link MySlashCMD}; the target page
 * is appended <b>last</b> to the button metadata, so a pager button reads it back with {@link #pageOf}.
 */
public final class Pager {

    public static final String PREV = "◀";
    public static final String NEXT = "▶";
    /** Pass as {@code total} when the source cannot count its rows — use the {@code hasNext} overload instead. */
    public static final int UNKNOWN_TOTAL = -1;

    /** 1-based last page of a known total, or {@link #UNKNOWN_TOTAL}. */
    public static int lastPage(int perPage, int total) { return total < 0 ? UNKNOWN_TOTAL : Math.max(1, (int) Math.ceil((double) total / perPage)); }

    /** Caller metadata with the target page appended last. */
    public static Object[] withPage(Object[] metadata, int page) {
        Object[] M = Arrays.copyOf(metadata, metadata.length + 1);
        M[metadata.length] = page;
        return M;
    }

    /** The page a pager button was built for — always the last metadata entry. */
    public static int pageOf(String[] metadata) { return Integer.parseInt(metadata[metadata.length - 1]); }

    /** ◀ / current / ▶, the outer two disabled at the bounds. {@code btn} builds the pager button for a target page. */
    public static ActionRow row(int page, String label, boolean hasNext, IntFunction<Button> btn) {
        return ActionRow.of(btn.apply(page - 1).withLabel(PREV).withDisabled(page <= 1),
                btn.apply(page).withLabel(label).asDisabled(),
                btn.apply(page + 1).withLabel(NEXT).withDisabled(!hasNext));
    }
}
