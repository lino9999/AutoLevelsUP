package com.Lino.autoLevelsUP.utils;

import org.bukkit.ChatColor;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</gradient>");
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String process(String text) {
        if (text == null) return "";

        // 1. Process Gradients
        Matcher gradientMatcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (gradientMatcher.find()) {
            String startHex = gradientMatcher.group(1);
            String endHex = gradientMatcher.group(2);
            String content = gradientMatcher.group(3);
            gradientMatcher.appendReplacement(sb, applyGradient(content, startHex, endHex));
        }
        gradientMatcher.appendTail(sb);
        text = sb.toString();

        // 2. Process Hex Colors (&#RRGGBB)
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        sb = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            hexMatcher.appendReplacement(sb, replacement.toString());
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();

        // 3. Process Standard Colors
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String applyGradient(String text, String startHex, String endHex) {
        StringBuilder result = new StringBuilder();
        Color start = Color.decode(startHex);
        Color end = Color.decode(endHex);
        int length = text.length();

        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (float) (length - 1);
            int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
            int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
            int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);

            result.append(String.format("§x§%x§%x§%x§%x§%x§%x",
                    (red >> 4) & 15, red & 15,
                    (green >> 4) & 15, green & 15,
                    (blue >> 4) & 15, blue & 15));
            result.append(text.charAt(i));
        }
        return result.toString();
    }
}