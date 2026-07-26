package org.solarframework.tournament.impl.render;

import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.api.dto.BracketTheme;
import org.solarframework.tournament.impl.seed.Brackets;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a phase into a {@link RenderModel}.
 *
 * <p>Bracket phases are laid out by the usual rule: round one is evenly spaced, and every later
 * match sits at the midpoint of the matches feeding it, which is what produces the familiar
 * converging tree. The grand final and its reset are placed explicitly rather than by that generic
 * rule, since they are fed by two independent brackets (winners and losers) rather than by two
 * sibling matches in the same column. Group and swiss phases get a standings table plus a column of
 * matches per round.
 *
 * <p>Every gap and offset is derived from {@link BracketTheme}'s metrics rather than hardcoded, so
 * the whole picture scales cleanly whether the phase has 4 entrants or 64.
 */
public class BracketLayout {

    private final IPhase phase;
    private final BracketTheme th;
    private final RenderModel model = new RenderModel();
    /** matchID to the (x, y) of its top-left corner. */
    private final Map<Long, Double> matchX = new HashMap<>();
    private final Map<Long, Double> matchY = new HashMap<>();
    private double maxX, maxY;
    /** Baseline for the round-header row, computed once in {@link #build()}. */
    private double headerY;

    /** The y this match was placed at after {@link #build()} - package-visible so layout invariants are testable. */
    Double placedY(Long matchID) { return matchY.get(matchID); }

    public BracketLayout(IPhase phase, BracketTheme theme) {
        this.phase = phase;
        this.th = theme == null ? BracketTheme.dark() : theme;
    }

    public RenderModel build() {
        model.background = th.getBackground();
        double contentTop = th.getPadding() + (th.isShowTitle() ? titleBlockHeight() : 0);
        headerY = contentTop + th.getHeaderFontSize();
        double matchesTop = contentTop + (th.isShowRoundHeaders() ? th.headerClearance() : 0);

        if (phase.getType().isBracket()) layoutBracket(matchesTop); else layoutTable(matchesTop);

        model.width = (int) Math.ceil(maxX + th.getPadding());
        model.height = (int) Math.ceil(maxY + th.getPadding());
        if (th.isShowTitle()) drawTitle();
        return model;
    }

    private double titleBlockHeight() { return th.getTitleFontSize() + th.getHeaderFontSize() + th.getHeaderFontSize() / 2; }

    private void drawTitle() {
        String sub = phase.getType() + (phase.getParticipantCount() > 0 ? " - " + phase.getParticipantCount() + " entrants" : "");
        model.text(th.getPadding(), th.getPadding() + th.getTitleFontSize(), title(), th.getTitleFontSize(), th.getHeaderColor(), "start", true);
        model.text(th.getPadding(), th.getPadding() + th.getTitleFontSize() + th.getHeaderFontSize() + 4, sub, th.getHeaderFontSize(), th.getMutedTextColor(), "start", false);
    }

    private String title() {
        String t = phase.getTournament() == null ? null : phase.getTournament().getName();
        return t == null ? phase.getName() : t + " - " + phase.getName();
    }

    // ---- bracket ---------------------------------------------------------------------------------

    private void layoutBracket(double matchesTop) {
        List<IMatch> winners = sorted(phase.getMatches(BracketSide.WINNERS));
        List<IMatch> losers = sorted(phase.getMatches(BracketSide.LOSERS));
        List<IMatch> thirdPlace = phase.getMatches(BracketSide.THIRD_PLACE);
        IMatch grandFinal = phase.getMatches(BracketSide.GRAND_FINAL).stream().findFirst().orElse(null);
        IMatch reset = phase.getMatches(BracketSide.GRAND_FINAL_RESET).stream().findFirst().orElse(null);

        int wbRounds = winners.stream().mapToInt(IMatch::getRound).max().orElse(0);
        double afterWb = placeRounds(winners, matchesTop, th.getPadding());

        if (!thirdPlace.isEmpty()) {
            IMatch tp = thirdPlace.getFirst();
            IMatch wbFinal = winners.stream().filter(m -> m.getRound() == wbRounds).findFirst().orElse(null);
            double under = wbFinal == null ? afterWb + th.getMatchGap() : matchY.get(wbFinal.getID()) + th.matchPitch() + th.headerClearance();
            place(tp, columnX(th.getPadding(), wbRounds), under);
            afterWb = Math.max(afterWb, under + th.matchHeight());
        }

        int lbRounds = losers.stream().mapToInt(IMatch::getRound).max().orElse(0);
        boolean hasLosers = !losers.isEmpty();
        double lbHeaderY = 0, dividerY = 0;
        if (hasLosers) {
            dividerY = afterWb + th.getMatchGap() + th.headerClearance();
            double lbTop = dividerY + th.getMatchGap() + th.headerClearance();
            lbHeaderY = lbTop - (th.headerClearance() - th.getHeaderFontSize());
            placeRounds(losers, lbTop, th.getPadding());
        }

        placeGrandFinal(grandFinal, reset, winners, losers, wbRounds, lbRounds, matchesTop);

        if (hasLosers) drawSectionDivider(dividerY, Math.max(wbRounds, lbRounds));
        if (th.isShowRoundHeaders()) drawBracketHeaders(wbRounds, lbRounds, hasLosers, lbHeaderY);
        drawConnectors();
        for (IMatch m : phase.getMatches()) drawMatch(m);
        drawCaption(thirdPlace.isEmpty() ? null : thirdPlace.getFirst());
        drawCaption(grandFinal);
        drawCaption(reset);
    }

    /**
     * The grand final is fed by two independent brackets, not by two matches in the same column, so
     * it gets its own dedicated column one past the wider of the two, vertically centred between the
     * winners-bracket champion and the losers-bracket runner-up. A reset (if the phase has one) stacks
     * directly below it.
     */
    private void placeGrandFinal(IMatch grandFinal, IMatch reset, List<IMatch> winners, List<IMatch> losers, int wbRounds, int lbRounds, double fallbackTop) {
        if (grandFinal == null) return;
        double gfX = columnX(th.getPadding(), Math.max(wbRounds, lbRounds) + 1);
        IMatch wbFinal = winners.stream().filter(m -> m.getRound() == wbRounds).findFirst().orElse(null);
        IMatch lbFinal = losers.stream().filter(m -> m.getRound() == lbRounds).findFirst().orElse(null);
        double gfY;
        if (wbFinal != null && lbFinal != null) {
            double wbCenter = matchY.get(wbFinal.getID()) + th.matchHeight() / 2.0;
            double lbCenter = matchY.get(lbFinal.getID()) + th.matchHeight() / 2.0;
            gfY = (wbCenter + lbCenter) / 2.0 - th.matchHeight() / 2.0;
        } else if (wbFinal != null) {
            gfY = matchY.get(wbFinal.getID());
        } else {
            gfY = fallbackTop;
        }
        place(grandFinal, gfX, gfY);
        if (reset != null) place(reset, gfX, gfY + th.matchPitch());
    }

    /**
     * Places one bracket side. Round one is evenly spaced; every later match is centred on the
     * matches that feed it, falling back to even spacing when a feeder is missing.
     * @return the y of the bottom of the block
     */
    private double placeRounds(List<IMatch> matches, double top, double left) {
        if (matches.isEmpty()) return top;
        Map<Integer, List<IMatch>> rounds = new LinkedHashMap<>();
        for (IMatch m : matches) rounds.computeIfAbsent(m.getRound(), _ -> new ArrayList<>()).add(m);
        int firstRound = rounds.keySet().stream().mapToInt(Integer::intValue).min().orElse(1);
        double bottom = top;
        for (Map.Entry<Integer, List<IMatch>> e : rounds.entrySet()) {
            List<IMatch> round = e.getValue();
            round.sort(Comparator.comparingInt(IMatch::getPosition));
            double x = columnX(left, e.getKey() - firstRound + 1);
            for (int i = 0; i < round.size(); i++) {
                IMatch m = round.get(i);
                double y = e.getKey() == firstRound ? top + i * th.matchPitch() : centerBetween(matches, m, top + i * th.matchPitch());
                place(m, x, y);
                bottom = Math.max(bottom, y + th.matchHeight());
            }
        }
        return bottom;
    }

    /**
     * Midpoint of the already-placed matches feeding this one; {@code fallback} if there are none.
     * Only looks within {@code pool} (the same bracket side being laid out) - a losers-bracket major
     * round also receives a dropped winners-bracket loser, but that feed must never pull the row
     * toward the (much higher up) winners bracket, or the whole losers side drifts out of its section.
     */
    private double centerBetween(List<IMatch> pool, IMatch m, double fallback) {
        List<Double> centers = new ArrayList<>();
        for (IMatch x : pool) {
            boolean feeds = Objects.equals(x.getNextMatchID(), m.getID()) || Objects.equals(x.getNextLoserMatchID(), m.getID());
            Double y = matchY.get(x.getID());
            if (feeds && y != null) centers.add(y + th.matchHeight() / 2.0);
        }
        if (centers.isEmpty()) return fallback;
        double avg = centers.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return avg - th.matchHeight() / 2.0;
    }

    private double columnX(double left, int columnIndex) { return left + (columnIndex - 1) * th.roundPitch(); }

    private void place(IMatch m, double x, double y) {
        matchX.put(m.getID(), x);
        matchY.put(m.getID(), y);
        maxX = Math.max(maxX, x + th.getSlotWidth());
        maxY = Math.max(maxY, y + th.matchHeight());
    }

    private void drawBracketHeaders(int wbRounds, int lbRounds, boolean hasLosers, double lbHeaderY) {
        for (int r = 1; r <= wbRounds; r++) model.text(columnX(th.getPadding(), r), headerY, Brackets.roundName(r, wbRounds), th.getHeaderFontSize(), th.getMutedTextColor(), "start", true);
        if (!hasLosers) return;
        for (int r = 1; r <= lbRounds; r++) model.text(columnX(th.getPadding(), r), lbHeaderY, r == lbRounds ? "Losers Final" : "Losers Round " + r, th.getHeaderFontSize(), th.getMutedTextColor(), "start", true);
    }

    /** Rule plus caption marking the losers bracket off as its own bracket below the main one. It stops short of the grand final column, which straddles both sections. */
    private void drawSectionDivider(double y, int columns) {
        model.poly(th.getSlotBorder(), 1, th.getPadding(), y, columnX(th.getPadding(), columns) + th.getSlotWidth(), y);
        model.text(th.getPadding(), y - (th.headerClearance() - th.getHeaderFontSize()), "LOSERS BRACKET", th.getHeaderFontSize(), th.getAccentColor(), "start", true);
    }

    /** A small caption above a match's own box - used for the grand final, its reset and third place, since none of them share a round-header column. */
    private void drawCaption(IMatch m) {
        if (m == null) return;
        Double x = matchX.get(m.getID()), y = matchY.get(m.getID());
        if (x == null || y == null) return;
        String label = m.getLabel() != null && !m.getLabel().isBlank() ? m.getLabel() : fallbackCaption(m.getBracketSide());
        String color = m.getBracketSide() == BracketSide.THIRD_PLACE ? th.getMutedTextColor() : th.getAccentColor();
        model.text(x, y - (th.headerClearance() - th.getHeaderFontSize()), label, th.getHeaderFontSize(), color, "start", true);
    }

    private String fallbackCaption(BracketSide side) {
        return switch (side) {
            case THIRD_PLACE -> "3rd Place Match";
            case GRAND_FINAL -> "Grand Final";
            case GRAND_FINAL_RESET -> "Grand Final (Reset)";
            default -> "";
        };
    }

    /**
     * Elbow connectors from each match's right edge into the slot it feeds. Only the winner feed is
     * drawn: a loser feed goes into another section of the picture entirely (the losers bracket below,
     * or the third place match), so a line for it would sweep across the whole bracket for information
     * the target slot already spells out as "Loser of M7". The one exception is a loser feed straight
     * into the grand final, which happens when the phase has no losers bracket at all.
     */
    private void drawConnectors() {
        for (IMatch m : phase.getMatches()) {
            connect(m, m.getNextMatchID(), m.getNextMatchSlot());
            BracketSide to = m.getNextLoserMatchID() == null ? null : phase.getMatch(m.getNextLoserMatchID()).map(IMatch::getBracketSide).orElse(null);
            if (to != null && to != BracketSide.LOSERS && to != BracketSide.THIRD_PLACE) connect(m, m.getNextLoserMatchID(), m.getNextLoserMatchSlot());
        }
    }

    private void connect(IMatch from, Long toID, Integer slot) {
        if (toID == null || slot == null) return;
        Double fx = matchX.get(from.getID()), fy = matchY.get(from.getID());
        Double tx = matchX.get(toID), ty = matchY.get(toID);
        if (fx == null || fy == null || tx == null || ty == null) return;
        double x1 = fx + th.getSlotWidth(), y1 = fy + th.matchHeight() / 2.0;
        double x2 = tx, y2 = ty + (slot == 1 ? th.getSlotHeight() / 2.0 : th.getSlotHeight() * 1.5 + th.getSlotGap());
        // The grand final is fed from both sections at once, so its two feeds run in the gutter right
        // in front of it rather than halfway across - a halfway elbow would cut through the bracket
        // (and the section divider) that sits between the two columns.
        boolean gf = phase.getMatch(toID).map(m -> m.getBracketSide() == BracketSide.GRAND_FINAL || m.getBracketSide() == BracketSide.GRAND_FINAL_RESET).orElse(false);
        double mid = x2 <= x1 ? x1 + th.getRoundGap() / 2.0 : gf ? x2 - th.getRoundGap() / 2.0 : x1 + (x2 - x1) / 2.0;
        model.poly(th.getConnectorColor(), 2, x1, y1, mid, y1, mid, y2, x2, y2);
    }

    // ---- match block -----------------------------------------------------------------------------

    private void drawMatch(IMatch m) {
        Double x = matchX.get(m.getID()), y = matchY.get(m.getID());
        if (x == null || y == null) return;
        drawSlot(m, 1, x, y);
        drawSlot(m, 2, x, y + th.getSlotHeight() + th.getSlotGap());
        if (th.isShowMatchNumbers()) {
            model.text(x - 6, y + th.matchHeight() / 2.0 + 4, "M" + m.getMatchNumber(), Math.max(9, th.getSlotFontSize() - 3), th.getMutedTextColor(), "end", false);
        }
    }

    private void drawSlot(IMatch m, int slot, double x, double y) {
        Long pid = m.getParticipantID(slot);
        String dataId = pid == null ? null : String.valueOf(pid);
        boolean decided = m.getState().isDecided();
        boolean isWinner = decided && pid != null && m.isWinner(pid);
        boolean isLoser = decided && pid != null && m.isLoser(pid);
        String fill = isWinner && th.isHighlightWinners() ? th.getWinnerBackground() : (slot == 1 ? th.getSlotBackground() : th.getSlotBackgroundAlt());
        String stroke = isWinner && th.isHighlightWinners() ? th.getWinnerBorder() : th.getSlotBorder();
        model.box(x, y, th.getSlotWidth(), th.getSlotHeight(), fill, stroke, th.getCornerRadius(), dataId);

        double textX = x + 10;
        double available = th.getSlotWidth() - 20;
        double baseline = y + th.getSlotHeight() / 2.0 + th.getSlotFontSize() * 0.36;

        if (th.isShowSeeds()) {
            int seed = seedOf(pid);
            if (seed > 0) model.text(textX, baseline, String.valueOf(seed), Math.max(9, th.getSlotFontSize() - 2), th.getMutedTextColor(), "start", false, dataId);
            textX += th.getSeedWidth();
            available -= th.getSeedWidth();
        }
        if (th.isShowScores()) available -= th.getScoreWidth();

        String name = m.getSlotLabel(slot);
        boolean placeholder = pid == null;
        String color = placeholder ? th.getMutedTextColor() : (isLoser ? th.getLoserTextColor() : th.getTextColor());
        model.text(textX, baseline, RenderModel.fit(name.isEmpty() ? "TBD" : name, th.getSlotFontSize(), available), th.getSlotFontSize(), color, "start", isWinner, dataId);

        if (!th.isShowScores()) return;
        double sx = x + th.getSlotWidth() - th.getScoreWidth();
        model.box(sx, y, th.getScoreWidth(), th.getSlotHeight(), th.getScoreBackground(), th.getSlotBorder(), th.getCornerRadius(), dataId);
        model.text(sx + th.getScoreWidth() / 2.0, baseline, scoreText(m, slot), th.getScoreFontSize(), isWinner ? th.getWinnerBorder() : th.getTextColor(), "middle", isWinner, dataId);
    }

    private String scoreText(IMatch m, int slot) {
        if (m.getState() == MatchState.BYE) return slot == 1 && m.getParticipantID1() != null || slot == 2 && m.getParticipantID2() != null ? "BYE" : "";
        if (!m.getState().isDecided() && m.getScore1() == 0 && m.getScore2() == 0) return "";
        return String.valueOf(slot == 1 ? m.getScore1() : m.getScore2());
    }

    private int seedOf(Long participantID) {
        return participantID == null ? 0 : phase.getStanding(participantID).map(IStanding::getSeed).orElse(0);
    }

    // ---- group / swiss ---------------------------------------------------------------------------

    private void layoutTable(double top) {
        double x = th.getPadding();
        int groups = Math.max(1, phase.getGroupCount());
        double tableBottom = top;
        for (int g = 0; g < groups; g++) {
            double bottom = drawStandings(x, top, g, groups > 1 ? "Group " + (char) ('A' + g) : "Standings");
            tableBottom = Math.max(tableBottom, bottom);
            if (g % 2 == 1 || groups == 1) { x = th.getPadding(); top = tableBottom + th.getMatchGap() * 2; }
            else x += tableWidth() + th.getRoundGap();
        }
        drawMatchColumns(th.getPadding(), tableBottom + th.sectionGap());
        for (IMatch m : phase.getMatches()) drawMatch(m);
    }

    private double tableWidth() { return th.getSlotWidth() + 250; }

    /** Rank / name / played / W-D-L / game diff / points, one row per entrant. */
    private double drawStandings(double x, double y, int groupIndex, String heading) {
        List<IStanding> rows = phase.getStandings().stream().filter(s -> s.getGroupIndex() == groupIndex)
                .sorted(Comparator.comparingInt(IStanding::getRank)).toList();
        if (rows.isEmpty()) return y;
        double w = tableWidth(), rowH = th.getSlotHeight();
        model.text(x, y - (th.headerClearance() - th.getHeaderFontSize()), heading, th.getHeaderFontSize(), th.getMutedTextColor(), "start", true);
        double[] cols = {36, w - 36 - 40 - 40 - 40 - 50 - 55, 40, 40, 40, 50, 55};
        String[] headers = {"#", "Entrant", "P", "W", "L", "GD", "Pts"};
        model.box(x, y, w, rowH, th.getSlotBackgroundAlt(), th.getSlotBorder(), th.getCornerRadius());
        drawRowText(x, y, cols, headers, th.getMutedTextColor(), true, null);

        double cy = y + rowH + 2;
        for (IStanding s : rows) {
            String dataId = String.valueOf(s.getParticipantID());
            boolean qualified = s.isQualified();
            model.box(x, cy, w, rowH, qualified ? th.getWinnerBackground() : th.getSlotBackground(), qualified ? th.getWinnerBorder() : th.getSlotBorder(), th.getCornerRadius(), dataId);
            String[] cells = {
                    String.valueOf(s.getRank()),
                    RenderModel.fit(s.getName(), th.getSlotFontSize(), cols[1] - 12),
                    String.valueOf(s.getPlayed()),
                    String.valueOf(s.getWins()),
                    String.valueOf(s.getLosses()),
                    (s.getGameDiff() > 0 ? "+" : "") + s.getGameDiff(),
                    trimNumber(s.getPoints())
            };
            drawRowText(x, cy, cols, cells, th.getTextColor(), false, dataId);
            cy += rowH + 2;
        }
        maxX = Math.max(maxX, x + w);
        maxY = Math.max(maxY, cy);
        return cy;
    }

    private void drawRowText(double x, double y, double[] cols, String[] cells, String color, boolean bold, String dataId) {
        double cx = x, baseline = y + th.getSlotHeight() / 2.0 + th.getSlotFontSize() * 0.36;
        for (int i = 0; i < cells.length && i < cols.length; i++) {
            boolean numeric = i != 1;
            double tx = i == 0 ? cx + 12 : (numeric ? cx + cols[i] - 12 : cx + 6);
            model.text(tx, baseline, cells[i], th.getSlotFontSize(), color, i == 0 || !numeric ? "start" : "end", bold, dataId);
            cx += cols[i];
        }
    }

    private String trimNumber(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d); }

    /** One column of match blocks per round, to the right of the tables. */
    private void drawMatchColumns(double left, double top) {
        Map<Integer, List<IMatch>> rounds = new LinkedHashMap<>();
        for (IMatch m : phase.getMatches()) rounds.computeIfAbsent(m.getRound(), _ -> new ArrayList<>()).add(m);
        if (rounds.isEmpty()) return;
        List<Integer> keys = new ArrayList<>(rounds.keySet());
        keys.sort(Integer::compareTo);
        double headerGap = th.headerClearance() - th.getHeaderFontSize();
        for (int c = 0; c < keys.size(); c++) {
            List<IMatch> round = rounds.get(keys.get(c));
            round.sort(Comparator.comparingInt(IMatch::getPosition));
            double x = columnX(left, c + 1);
            if (th.isShowRoundHeaders()) model.text(x, top - headerGap, "Round " + keys.get(c), th.getHeaderFontSize(), th.getMutedTextColor(), "start", true);
            for (int i = 0; i < round.size(); i++) place(round.get(i), x, top + i * th.matchPitch());
        }
    }

    private List<IMatch> sorted(List<IMatch> in) {
        List<IMatch> out = new ArrayList<>(in);
        out.sort(Comparator.comparingInt(IMatch::getRound).thenComparingInt(IMatch::getPosition));
        return out;
    }

    /** Convenience for a whole tournament: stacks each phase's model into one canvas. */
    public static RenderModel stack(List<RenderModel> parts, BracketTheme theme, String background) {
        RenderModel out = new RenderModel();
        out.background = background;
        double offset = 0;
        for (RenderModel p : parts) {
            for (RenderModel.Shape s : p.shapes) out.shapes.add(translate(s, offset));
            out.width = Math.max(out.width, p.width);
            offset += p.height + (theme == null ? 40 : theme.getPadding());
        }
        out.height = (int) Math.ceil(offset);
        return out;
    }

    private static RenderModel.Shape translate(RenderModel.Shape s, double dy) {
        return switch (s) {
            case RenderModel.Box b -> new RenderModel.Box(b.x(), b.y() + dy, b.w(), b.h(), b.fill(), b.stroke(), b.strokeWidth(), b.radius(), b.dataId());
            case RenderModel.Text t -> new RenderModel.Text(t.x(), t.y() + dy, t.text(), t.size(), t.color(), t.anchor(), t.bold(), t.dataId());
            case RenderModel.Poly p -> new RenderModel.Poly(p.points().stream().map(pt -> new double[]{pt[0], pt[1] + dy}).toList(), p.stroke(), p.strokeWidth());
        };
    }
}
