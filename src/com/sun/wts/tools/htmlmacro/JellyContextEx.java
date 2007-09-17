package com.sun.wts.tools.htmlmacro;

import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.parser.XMLParser;
import org.cyberneko.html.parsers.SAXParser;
import org.xml.sax.SAXException;

/**
 * {@link JellyContext} that uses NekoHTML.
 *
 * @author Kohsuke Kawaguchi
 */
public class JellyContextEx extends JellyContext {
    public JellyContextEx() {
    }

    @Override
    protected XMLParser createXMLParser() {
        SAXParser p = new SAXParser();
        try {
            p.setFeature("http://xml.org/sax/features/namespaces",true);
            p.setFeature("http://xml.org/sax/features/namespaceprefixes",false);
            p.setProperty("http://cyberneko.org/html/properties/names/elems","match");
        } catch (SAXException e) {
            throw new Error(e);
        }
        return new XMLParser(p);
    }
}
