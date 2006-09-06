package com.sun.wts.tools.htmlmacro;

import org.apache.commons.jelly.XMLOutput;
import org.dom4j.io.HTMLWriter;
import org.dom4j.io.OutputFormat;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.Stack;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.charset.CharsetEncoder;

/**
 * @author Kohsuke Kawaguchi
 */
public class HTMLOutput extends XMLOutput {
    public static XMLOutput create(OutputStream os, String encoding) throws UnsupportedEncodingException {
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding(encoding);
        format.setTrimText(false);
        return createXMLOutput(new HTMLWriter(os, format) {

            /**
             * {@link Charset} being used for the encoding.
             */
            private Charset charset;
            private CharsetEncoder encoder;

            protected Writer createWriter(OutputStream outStream, String encoding) throws UnsupportedEncodingException {
                try {
                    charset = Charset.forName(encoding);
                    encoder = charset.newEncoder();
                } catch (UnsupportedCharsetException e) {
                    throw new UnsupportedEncodingException(encoding);
                }
                return new BufferedWriter(new OutputStreamWriter(outStream,charset));
            }

            class State {
                private final boolean newLines;
                private final boolean trimText;
                private final String indent;

                State() {
                    OutputFormat currentFormat = getOutputFormat();
                    newLines = currentFormat.isNewlines();
                    trimText = currentFormat.isTrimText();
                    indent = currentFormat.getIndent();
                }

                void restore() {
                    OutputFormat currentFormat = getOutputFormat();
                    currentFormat.setNewlines(newLines);
                    currentFormat.setTrimText(trimText);
                    currentFormat.setIndent(indent);
                }
            }

            private Stack<State> states = new Stack<State>();

            public void startElement(String namespaceURI, String localName, String qName, Attributes attributes) throws SAXException {
                super.startElement(namespaceURI, localName, qName, attributes);
                states.push(new State());

                if(isPreformattedTag(qName)) {
                    OutputFormat currentFormat = getOutputFormat();
                    currentFormat.setNewlines(false);//actually, newlines are handled in this class by writeString, depending on if the stack is empty.
                    currentFormat.setTrimText(false);
                    currentFormat.setIndent("");
                }
            }

            public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
                super.endElement(namespaceURI, localName, qName);
                states.pop().restore();
            }

            protected boolean shouldEncodeChar(char c) {
                return super.shouldEncodeChar(c) || c==160/*nbsp*/ || !encoder.canEncode(c);
            }
        });
    }
}
