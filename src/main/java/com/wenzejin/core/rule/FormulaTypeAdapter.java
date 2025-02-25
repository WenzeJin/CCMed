package com.wenzejin.core.rule;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.util.XmlToRuleParser;

import java.io.IOException;

public class FormulaTypeAdapter extends TypeAdapter<Formula>{

    @Override
    public void write(JsonWriter out, Formula value) throws IOException {
        out.beginObject();
        out.name("content").value(value.toXmlString());
        out.endObject();
    }

    @Override
    public Formula read(JsonReader in) throws IOException {
        in.beginObject();
        in.nextName();
        String content = in.nextString();
        in.endObject();
        XmlToRuleParser parser = new XmlToRuleParser();
        try {
            Formula formula = parser.parseXmlToFormula(content);
            return formula;
        } catch (XmlParseException e) {
            return null;
        }
    }
}
