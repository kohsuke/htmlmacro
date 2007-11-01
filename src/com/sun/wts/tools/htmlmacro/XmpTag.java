package com.sun.wts.tools.htmlmacro;

import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.MissingAttributeException;
import org.apache.commons.jelly.TagSupport;
import org.apache.commons.jelly.XMLOutput;

/**
 * Once read into XML, XMP becomes harmful, so remove it.
 *
 * @author Kohsuke Kawaguchi
 */
public class XmpTag extends TagSupport {
    public void doTag(XMLOutput output) throws MissingAttributeException, JellyTagException {
        getBody().run(getContext(),output);
    }
}
