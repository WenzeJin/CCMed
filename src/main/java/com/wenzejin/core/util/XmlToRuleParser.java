package com.wenzejin.core.util;

import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.rule.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class XmlToRuleParser {

    public List<Rule> parseRulesInDirectory(String directoryPath) throws XmlParseException {
        List<Rule> allRules = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .filter(file -> file.toString().endsWith(".xml"))) {

            paths.forEach(file -> {
                try {
                    // 对每个 XML 文件进行解析
                    List<Rule> rules = parseRuleFromXmlFile(file.toString());
                    allRules.addAll(rules); // 将解析结果添加到总列表
                } catch (XmlParseException e) {
                    System.err.println("Failed to parse file: " + file + " - " + e.getMessage());
                }
            });

        } catch (IOException e) {
            throw new XmlParseException("Failed to read files in directory: " + directoryPath);
        }

        return allRules;
    }

    public List<Rule> parseRuleFromXmlFile(String filePath) throws XmlParseException {
        try {
            // 读取文件内容
            FileInputStream fileInputStream = new FileInputStream(filePath);
            // 使用文件内容调用 parseRuleFromXmlDoc 方法进行解析
            return parseRuleFromXmlDoc(fileInputStream);
        } catch (FileNotFoundException e) {
            throw new XmlParseException("File not found: " + filePath);
        } catch (XmlParseException e) {
            String message = e.getMessage();
            throw new XmlParseException("File \" " + filePath + "\" could not be parsed: \n" + message);
        }
    }

    public List<Rule> parseRuleFromXmlDoc(InputStream xml) throws XmlParseException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document document;

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(xml);
        } catch (Exception e) {
            throw new XmlParseException(e.getMessage());
        }

        // get root element
        Element root = document.getDocumentElement();
        NodeList ruleNodes = root.getElementsByTagName("rule");

        if (ruleNodes.getLength() == 0) {
            throw new XmlParseException("No rule found in file");
        }

        List<Rule> rules = new ArrayList<>();

        for (int i = 0; i < ruleNodes.getLength(); i++) {
            Element ruleElement = (Element) ruleNodes.item(i);
            String id = ruleElement.getElementsByTagName("id").item(0).getTextContent();
            Formula formula = parseFormula(ruleElement.getElementsByTagName("formula").item(0));
            if (formula == null || !formula.isValid()) {
                throw new XmlParseException("formula is invalid");
            }
            rules.add(new Rule(id, formula));
        }

        return rules;
    }

    private Formula parseFormula(Node formulaNode) {
        if (formulaNode == null) {
            return null;
        }

        return switch (formulaNode.getNodeName()) {
            case "formula"  -> parseFormula(((Element) formulaNode).getElementsByTagName("*").item(0));
            case "forall"   -> parseOpQuantifier(formulaNode, OpQuantifier.Op.FORALL);
            case "exists"   -> parseOpQuantifier(formulaNode, OpQuantifier.Op.EXISTS);
            case "and"      -> parseOpConnector(formulaNode, OpConnector.Op.AND);
            case "or"       -> parseOpConnector(formulaNode, OpConnector.Op.OR);
            case "implies"  -> parseOpConnector(formulaNode, OpConnector.Op.IMPLIES);
            case "not"      -> parseOpNot(formulaNode);
            case "bfunc"    -> parseLiteralBfunc(formulaNode);
            default         -> null;
        };

    }

    private OpQuantifier parseOpQuantifier(Node quantifierNode, OpQuantifier.Op opType) {
        String var = ((Element) quantifierNode).getAttribute("var");
        String pattern = ((Element) quantifierNode).getAttribute("in");

        // get child formula
        Formula formula = parseFormula(getDirectChildElements((Element) quantifierNode).item(0));

        return new OpQuantifier(opType, var, pattern, formula);
    }

    private OpConnector parseOpConnector(Node connectorNode, OpConnector.Op opType) {
        Formula left = null;
        Formula right = null;

        NodeList elements = getDirectChildElements((Element) connectorNode);

        int length = elements.getLength();

        // get left and right
        if (length > 0) {
            left = parseFormula(elements.item(0));
        }
        if (length > 1) {
            right = parseFormula(elements.item(1));
        }

        return new OpConnector(opType, left, right);
    }

    private OpNot parseOpNot(Node notNode) {
        Formula formula = parseFormula(getDirectChildElements((Element) notNode).item(0));
        return new OpNot(formula);
    }

    private LiteralBfunc parseLiteralBfunc(Node bfuncNode) {
        String name = ((Element) bfuncNode).getAttribute("name");
        NodeList paramNodes = ((Element) bfuncNode).getElementsByTagName("param");
        LiteralBfunc bfunc = new LiteralBfunc(name, new ArrayList<>());

        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element paramElement = (Element) paramNodes.item(i);
            String pos = paramElement.getAttribute("pos");
            String var = paramElement.getAttribute("var");
            String field = paramElement.getAttribute("field");
            bfunc.addParam(pos, var, field);
        }

        return bfunc;
    }

    private static NodeList getDirectChildElements(Element element) {
        NodeList allChildNodes = element.getChildNodes();
        // 使用一个 ArrayList 来收集直接子元素
        List<Element> directChildren = new ArrayList<>();

        for (int i = 0; i < allChildNodes.getLength(); i++) {
            Node node = allChildNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                directChildren.add((Element) node);
            }
        }

        // 将结果转换为 NodeList 返回
        return new NodeList() {
            @Override
            public int getLength() {
                return directChildren.size();
            }

            @Override
            public Node item(int index) {
                return directChildren.get(index);
            }
        };
    }
}
