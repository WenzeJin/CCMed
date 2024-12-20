package com.wenzejin.core.preprocess;

import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.rule.Rule;
import com.wenzejin.core.util.XmlToRuleParser;
import com.wenzejin.core.util.TestCase;

import java.util.ArrayList;
import java.util.List;


/**
 * Preprocessor class.
 * Read rules from all xml files under rulesInputDirectory.
 * Generate random data for each rule.
 * Enumerate bfuncs for rules and bind them.
 */
public class Preprocessor {

    String rulesInputDirectory;
    List<Rule> rulesInput;

    /**
     * Create a new Preprocessor and set its input dir.
     * @param rulesInputDirectory input dir
     */
    public Preprocessor(String rulesInputDirectory) {
        this.rulesInputDirectory = rulesInputDirectory;
    }

    /**
     * Read rules from rulesInputDirectory
     * @throws XmlParseException xml parsing error
     */
    public void readRules() throws XmlParseException {
        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();
        rulesInput = xmlToRuleParser.parseRulesInDirectory(rulesInputDirectory);
    }


    /* Getters and Setters */

    public String getRulesInputDirectory() {
        return rulesInputDirectory;
    }

    public void setRulesInputDirectory(String rulesInputDirectory) {
        this.rulesInputDirectory = rulesInputDirectory;
    }
}
