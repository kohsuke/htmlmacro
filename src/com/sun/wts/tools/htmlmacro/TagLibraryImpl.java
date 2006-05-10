package com.sun.wts.tools.htmlmacro;

import org.apache.commons.jelly.TagLibrary;
import org.apache.commons.jelly.Tag;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.impl.TagFactory;
import org.apache.commons.jelly.impl.DynamicTag;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.xml.sax.Attributes;

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;

/**
 * Ad-hoc {@link TagLibrary} from tag files.
 *
 * @author Kohsuke Kawaguchi
 */
final class TagLibraryImpl extends TagLibrary {
    TagLibraryImpl( Task task, JellyContext context, List<File> tags ) {

        // create another context so as not to use NekoHTML here
        JellyContext child = new JellyContext(context);
        child.setClassLoader(context.getClassLoader());

        for (File tag : tags) {
            task.log("Reading "+tag,Project.MSG_INFO);

            try {
                // compile script
                final Script script = child.compileScript(tag.toURL());

                registerTagFactory(getTagName(tag),new TagFactory() {
                    public Tag createTag(String name, Attributes attributes) {
                        return new DynamicTag(script);
                    }
                });
            } catch (JellyException e) {
                throw new BuildException(e);
            } catch (MalformedURLException e) {
                throw new BuildException(e);
            }
        }

        registerTag("xmp",XmpTag.class);
    }

    private String getTagName(File tag) {
        String name = tag.getName();
        if(name.endsWith(".tag"))
            name = name.substring(0,name.length()-4);
        return name;
    }
}
