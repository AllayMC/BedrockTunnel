package org.allaymc.bedrocktunnel.ui;

import java.awt.Color;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

enum ANSIColor {
    BLACK("(0;)?30(0;)?m", Color.BLACK),
    RED("(0;)?31(0;)?m", new Color(0xfff0524f)),
    GREEN("(0;)?32(0;)?m", new Color(0xff5c962c)),
    YELLOW("(0;)?33(0;)?m", new Color(0xffa68a0d)),
    BLUE("(0;)?34(0;)?m", new Color(0xff6cb6ff)),
    MAGENTA("(0;)?35(0;)?m", new Color(0xffa771bf)),
    CYAN("(0;)?36(0;)?m", new Color(0xff96d0ff)),
    WHITE("(0;)?37(0;)?m", new Color(0xffbcbec4)),
    B_BLACK("(0;)?(1;30|30;1)m", Color.BLACK),
    B_RED("(0;)?(1;31|31;1)m", new Color(0xfff0524f)),
    B_GREEN("(0;)?(1;32|32;1)m", new Color(0xff5c962c)),
    B_YELLOW("(0;)?(1;33|33;1)m", new Color(0xffa68a0d)),
    B_BLUE("(0;)?(1;34|34;1)m", new Color(0xff3993d4)),
    B_MAGENTA("(0;)?(1;35|35;1)m", new Color(0xffa771bf)),
    B_CYAN("(0;)?(1;36|36;1)m", new Color(0xff00a3a3)),
    B_WHITE("(0;)?(1;37|37;1)m", new Color(0xff808080)),
    RESET("0m", WHITE.color);

    private static final ANSIColor[] VALUES = values();
    private static final String PREFIX = Pattern.quote("\u001B[");
    private static final Map<ANSIColor, Pattern> PATTERNS = new EnumMap<>(ANSIColor.class);

    static {
        for (ANSIColor color : VALUES) {
            PATTERNS.put(color, Pattern.compile(PREFIX + color.ansiCode));
        }
    }

    private final String ansiCode;
    private final Color color;

    ANSIColor(String ansiCode, Color color) {
        this.ansiCode = ansiCode;
        this.color = color;
    }

    public Color color() {
        return color;
    }

    public static boolean isBoldColor(Color color) {
        return color.equals(B_BLACK.color)
                || color.equals(B_RED.color)
                || color.equals(B_GREEN.color)
                || color.equals(B_YELLOW.color)
                || color.equals(B_BLUE.color)
                || color.equals(B_MAGENTA.color)
                || color.equals(B_CYAN.color)
                || color.equals(B_WHITE.color);
    }

    public static ANSIColor fromANSI(String code) {
        return Arrays.stream(VALUES)
                .filter(value -> PATTERNS.get(value).matcher(code).matches())
                .findFirst()
                .orElse(RESET);
    }
}
