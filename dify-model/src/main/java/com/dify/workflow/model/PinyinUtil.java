package com.dify.workflow.model;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

/**
 * 中文拼音转换工具。
 * 将中文工具名转换为拼音，用于 workflow.app.name 和工具名称的匹配。
 *
 * 例: "加法计算器1" → "jiafajisuanqi1"
 *      "addCalculator" → "addcalculator"
 */
public class PinyinUtil {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    /**
     * 将包含中文的字符串转换为拼音。
     * 非中文字符保持原样。
     */
    public static String toPinyin(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.toString(c).matches("[\\u4e00-\\u9fa5]")) {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        result.append(pinyinArray[0]);
                    }
                } catch (Exception e) {
                    // 无法转换的字符保持原样
                    result.append(c);
                }
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    /**
     * 工具名匹配: 将输入（可能是中文或英文）转换为拼音小写后比较。
     */
    public static boolean matches(String toolName, String workflowName) {
        if (toolName == null || workflowName == null) {
            return false;
        }

        String toolPinyin = toPinyin(toolName);
        String workflowPinyin = toPinyin(workflowName);

        // 去掉空格和特殊字符后比较
        String normalizedTool = toolPinyin.replaceAll("[^a-z0-9]", "");
        String normalizedWorkflow = workflowPinyin.replaceAll("[^a-z0-9]", "");

        return normalizedTool.equalsIgnoreCase(normalizedWorkflow);
    }
}
