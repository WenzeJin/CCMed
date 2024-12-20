package com.wenzejin.core.util;

import com.wenzejin.core.rule.Rule;

import java.io.FileOutputStream;
import java.util.List;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XmlToRuleTest {

    @Test
    void testParseValidXmlFile() throws Exception {
        String filePath = "rules/4_level_rule/rule_data/implies_rules/rule_3_1470.xml";

        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();
        List<Rule> rules = xmlToRuleParser.parseRuleFromXmlFile(filePath);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.getFirst();
        assertEquals("rule_3_1470", rule.id);
        assertNotNull(rule.formula);
        assertTrue(rule.formula.isValid());
    }

    @Test
    void testToXmlString() throws Exception {
        // 初始化 XmlToRuleParser
        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();

        // 原始 XML 文件路径
        String oriPath = "rules/4_level_rule/rule_data/implies_rules/rule_3_1470.xml";

        // 解析原始 XML 文件
        List<Rule> rules = xmlToRuleParser.parseRuleFromXmlFile(oriPath);
        assertNotNull(rules);
        assertEquals(1, rules.size());

        // 获取第一个 Rule
        Rule rule = rules.getFirst();
        assertEquals("rule_3_1470", rule.id);

        // 将 Rule 转换为 XML 字符串
        String xmlString = "<?xml version=\"1.0\"?>" + "<rules>\n" + rule.toXmlString() + "</rules>";
        assertNotNull(xmlString);

        String tempPath = "test_to_xml_string.xml";
        try (FileOutputStream fos = new FileOutputStream(tempPath)) {
            fos.write(xmlString.getBytes());
        }
        // 解析临时文件中的 XML
        List<Rule> parsedRules = xmlToRuleParser.parseRuleFromXmlFile(tempPath);

        // 验证解析后的规则是否与原始规则相等
        assertNotNull(parsedRules);
        assertEquals(1, parsedRules.size());
        Rule parsedRule = parsedRules.getFirst();

        // 比较原始 Rule 和解析后的 Rule 是否相等
        assertEquals(rule, parsedRule);

    }

    @Test
    void testEquals() throws Exception {
        String filePath = "rules/4_level_rule/rule_data/and_rules/rule_3_1626.xml";

        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();
        List<Rule> rules = xmlToRuleParser.parseRuleFromXmlFile(filePath);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.getFirst();

        List<Rule> rules2 = xmlToRuleParser.parseRuleFromXmlFile(filePath);
        assertNotNull(rules2);
        assertEquals(1, rules2.size());
        Rule rule2 = rules2.getFirst();

        assertTrue(rule.equals(rule2));
    }

    @Test
    void parseSpeedTest() throws Exception {
        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();

        String fileDir = "rules/4_level_rule/rule_data";

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long startTime = threadMXBean.getCurrentThreadCpuTime();
        List<Rule> rules = xmlToRuleParser.parseRulesInDirectory(fileDir);
        long endTime = threadMXBean.getCurrentThreadCpuTime();
        long duration = endTime - startTime;
        float durationInMs = (float) duration / 1000000;
        System.out.println("Execution time (CPU time): " + durationInMs + " ms");

        List<Rule> rules2 = xmlToRuleParser.parseRulesInDirectory(fileDir);

        System.out.println("Parsed " + rules.size() + " rules");
        assertNotNull(rules);

        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            Rule rule2 = rules2.get(i);
            assertEquals(rule, rule2);
        }
    }

    @Test
    void testClone() throws Exception {
        String filePath = "rules/4_level_rule/rule_data/and_rules/rule_3_1626.xml";

        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();
        List<Rule> rules = xmlToRuleParser.parseRuleFromXmlFile(filePath);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.getFirst();
        Rule rule2 = rule.clone();

        assertEquals(rule, rule2);
        assertNotSame(rule, rule2);
    }
}