package org.solarframework.tournament.api.dto;

/**
 * Look and layout of a rendered bracket. Colours are {@code #RRGGBB} strings so the theme stays
 * free of any AWT type and can be serialised straight to JSON.
 */
public class BracketTheme {

    public static BracketTheme dark() { return new BracketTheme(); }

    public static BracketTheme light() {
        BracketTheme t = new BracketTheme();
        t.background = "#FFFFFF";
        t.slotBackground = "#F2F4F7";
        t.slotBackgroundAlt = "#E8EBF0";
        t.slotBorder = "#C9D0DA";
        t.textColor = "#101828";
        t.mutedTextColor = "#667085";
        t.connectorColor = "#B4BCC8";
        t.headerColor = "#101828";
        t.scoreBackground = "#DDE2EA";
        return t;
    }

    private String background = "#14161B";
    private String slotBackground = "#1E2229";
    private String slotBackgroundAlt = "#23272F";
    private String slotBorder = "#333944";
    private String winnerBorder = "#4CC38A";
    private String winnerBackground = "#1B2C25";
    private String loserTextColor = "#6B7280";
    private String textColor = "#ECEFF4";
    private String mutedTextColor = "#8A93A2";
    private String connectorColor = "#3A4150";
    private String headerColor = "#ECEFF4";
    private String accentColor = "#5B8DEF";
    private String scoreBackground = "#2B313B";

    private String fontFamily = "SansSerif";
    private int titleFontSize = 22;
    private int headerFontSize = 14;
    private int slotFontSize = 13;
    private int scoreFontSize = 13;

    private int slotWidth = 240;
    private int slotHeight = 32;
    /** Vertical gap between the two slots of one match. */
    private int slotGap = 2;
    /** Vertical gap between matches in the same round. */
    private int matchGap = 24;
    /** Horizontal gap between rounds. */
    private int roundGap = 64;
    private int padding = 44;
    /** Vertical gap between the title block (name / type / entrants) and the round headers below it. */
    private int titleGap = 34;
    private int cornerRadius = 8;
    private int scoreWidth = 36;
    private int seedWidth = 26;

    private boolean showSeeds = true;
    private boolean showScores = true;
    private boolean showRoundHeaders = true;
    private boolean showTitle = true;
    private boolean showMatchNumbers = false;
    private boolean highlightWinners = true;
    /** Scale factor for the raster output. Defaults to a retina-crisp 2x. */
    private double scale = 2.0;

    public String getBackground() { return background; }
    public BracketTheme setBackground(String v) { this.background = v; return this; }
    public String getSlotBackground() { return slotBackground; }
    public BracketTheme setSlotBackground(String v) { this.slotBackground = v; return this; }
    public String getSlotBackgroundAlt() { return slotBackgroundAlt; }
    public BracketTheme setSlotBackgroundAlt(String v) { this.slotBackgroundAlt = v; return this; }
    public String getSlotBorder() { return slotBorder; }
    public BracketTheme setSlotBorder(String v) { this.slotBorder = v; return this; }
    public String getWinnerBorder() { return winnerBorder; }
    public BracketTheme setWinnerBorder(String v) { this.winnerBorder = v; return this; }
    public String getWinnerBackground() { return winnerBackground; }
    public BracketTheme setWinnerBackground(String v) { this.winnerBackground = v; return this; }
    public String getLoserTextColor() { return loserTextColor; }
    public BracketTheme setLoserTextColor(String v) { this.loserTextColor = v; return this; }
    public String getTextColor() { return textColor; }
    public BracketTheme setTextColor(String v) { this.textColor = v; return this; }
    public String getMutedTextColor() { return mutedTextColor; }
    public BracketTheme setMutedTextColor(String v) { this.mutedTextColor = v; return this; }
    public String getConnectorColor() { return connectorColor; }
    public BracketTheme setConnectorColor(String v) { this.connectorColor = v; return this; }
    public String getHeaderColor() { return headerColor; }
    public BracketTheme setHeaderColor(String v) { this.headerColor = v; return this; }
    public String getAccentColor() { return accentColor; }
    public BracketTheme setAccentColor(String v) { this.accentColor = v; return this; }
    public String getScoreBackground() { return scoreBackground; }
    public BracketTheme setScoreBackground(String v) { this.scoreBackground = v; return this; }
    public String getFontFamily() { return fontFamily; }
    public BracketTheme setFontFamily(String v) { this.fontFamily = v; return this; }
    public int getTitleFontSize() { return titleFontSize; }
    public BracketTheme setTitleFontSize(int v) { this.titleFontSize = v; return this; }
    public int getHeaderFontSize() { return headerFontSize; }
    public BracketTheme setHeaderFontSize(int v) { this.headerFontSize = v; return this; }
    public int getSlotFontSize() { return slotFontSize; }
    public BracketTheme setSlotFontSize(int v) { this.slotFontSize = v; return this; }
    public int getScoreFontSize() { return scoreFontSize; }
    public BracketTheme setScoreFontSize(int v) { this.scoreFontSize = v; return this; }
    public int getSlotWidth() { return slotWidth; }
    public BracketTheme setSlotWidth(int v) { this.slotWidth = v; return this; }
    public int getSlotHeight() { return slotHeight; }
    public BracketTheme setSlotHeight(int v) { this.slotHeight = v; return this; }
    public int getSlotGap() { return slotGap; }
    public BracketTheme setSlotGap(int v) { this.slotGap = v; return this; }
    public int getMatchGap() { return matchGap; }
    public BracketTheme setMatchGap(int v) { this.matchGap = v; return this; }
    public int getRoundGap() { return roundGap; }
    public BracketTheme setRoundGap(int v) { this.roundGap = v; return this; }
    public int getPadding() { return padding; }
    public BracketTheme setPadding(int v) { this.padding = v; return this; }
    public int getTitleGap() { return titleGap; }
    public BracketTheme setTitleGap(int v) { this.titleGap = v; return this; }
    public int getCornerRadius() { return cornerRadius; }
    public BracketTheme setCornerRadius(int v) { this.cornerRadius = v; return this; }
    public int getScoreWidth() { return scoreWidth; }
    public BracketTheme setScoreWidth(int v) { this.scoreWidth = v; return this; }
    public int getSeedWidth() { return seedWidth; }
    public BracketTheme setSeedWidth(int v) { this.seedWidth = v; return this; }
    public boolean isShowSeeds() { return showSeeds; }
    public BracketTheme setShowSeeds(boolean v) { this.showSeeds = v; return this; }
    public boolean isShowScores() { return showScores; }
    public BracketTheme setShowScores(boolean v) { this.showScores = v; return this; }
    public boolean isShowRoundHeaders() { return showRoundHeaders; }
    public BracketTheme setShowRoundHeaders(boolean v) { this.showRoundHeaders = v; return this; }
    public boolean isShowTitle() { return showTitle; }
    public BracketTheme setShowTitle(boolean v) { this.showTitle = v; return this; }
    public boolean isShowMatchNumbers() { return showMatchNumbers; }
    public BracketTheme setShowMatchNumbers(boolean v) { this.showMatchNumbers = v; return this; }
    public boolean isHighlightWinners() { return highlightWinners; }
    public BracketTheme setHighlightWinners(boolean v) { this.highlightWinners = v; return this; }
    public double getScale() { return scale; }
    public BracketTheme setScale(double v) { this.scale = Math.max(0.25, Math.min(8, v)); return this; }

    /** Full height of one match block (two slots plus the gap between them). */
    public int matchHeight() { return slotHeight * 2 + slotGap; }
    /** Vertical pitch from one match to the next in the same round. */
    public int matchPitch() { return matchHeight() + matchGap; }
    /** Horizontal pitch from one round to the next. */
    public int roundPitch() { return slotWidth + roundGap; }
    /** Vertical space a row of round-header labels needs above the matches below it. */
    public int headerClearance() { return headerFontSize + Math.max(6, headerFontSize / 3); }
    /** Vertical gap between stacked sections, e.g. the winners bracket and the losers bracket. */
    public int sectionGap() { return matchGap * 2 + headerClearance(); }
}
