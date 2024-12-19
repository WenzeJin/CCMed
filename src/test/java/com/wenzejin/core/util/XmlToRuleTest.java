package com.wenzejin.core.util;

import com.wenzejin.core.rule.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmlToRuleTest {

    @Test
    void testParseValidXmlFile() throws Exception {
        String filePath = "rules/4_level_rule/rule_data/and_rules/rule_3_1626.xml";

        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();
        List<Rule> rules = xmlToRuleParser.parseRuleFromXmlFile(filePath);

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.getFirst();
        assertEquals("rule_3_1626", rule.id);
        assertNotNull(rule.formula);
        assertTrue(rule.formula.isValid());

    }
}