package com.sun.wts.tools.htmlmacro;

import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.jelly.expression.ConstantExpression;
import org.apache.commons.jelly.impl.TagScript;
import org.apache.commons.jelly.tags.core.JellyTag;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.Charset;

/**
 * @author Kohsuke Kawaguchi
 */
public class HtmlMacroTask extends Task {

    /**
     * Used to load additional jelly tag libraries.
     */
    private final Path classpath = new Path(null);

    /**
     * Tag files.
     */
    private final List<File> tags = new ArrayList<File>();

    /**
     * Reads the source file as HTML.
     */
    private boolean html = true;

    /**
     * Source files to process.
     */
    private FileSet documents;

    private File destdir;

    private Map<String,String> properties = new HashMap<String,String>();


    public void setDestdir(File destdir) {
        this.destdir = destdir;
    }

    /** Nested &lt;classpath> element. */
    public void setClasspath( Path cp ) {
        classpath.createPath().append(cp);
    }

    /** Nested &lt;classpath> element. */
    public Path createClasspath() {
        return classpath.createPath();
    }

    public void setClasspathRef(Reference r) {
        classpath.createPath().setRefid(r);
    }

    public void addConfiguredProperty(Environment.Variable v) {
        if(v.getKey()==null)
            return;
        properties.put(v.getKey(),v.getValue());
    }

    /**
     * Nested tag files.
     */
    public void addConfiguredTags( FileSet fs ) {
        populateFiles(fs,tags);
    }

    /**
     * Docs to process.
     */
    public void addConfiguredDocuments( FileSet fs ) {
        this.documents = fs;
    }

    /**
     * True to read the source files as HTML.
     */
    public void setXhtml(boolean value) {
        this.html = !value;
    }

    private void populateFiles(FileSet fs, List<File> r) {
        DirectoryScanner ds = fs.getDirectoryScanner(getProject());
        String[] includedFiles = ds.getIncludedFiles();
        File baseDir = ds.getBasedir();

        for (String value : includedFiles) {
            r.add(new File(baseDir, value));
        }
    }

    public void execute() throws BuildException {
        classpath.setProject(getProject());
        destdir.mkdirs();

        JellyContext context = html ? new JellyContextEx() : new JellyContext();

        // just dump all the Ant properties first.
        context.getVariables().putAll(getProject().getProperties());

        // properties specified in the build script. allow them to override Ant properties
        context.getVariables().putAll(properties);

        // pass all system properties last, allow individuals to override the build script setting
        context.getVariables().putAll(System.getProperties());


        context.setClassLoader(
            new AntClassLoader(getClass().getClassLoader(),getProject(),classpath, true));

        // make the tag lib from config files
        context.registerTagLibrary("",new TagLibraryImpl(this,context,tags));

        DirectoryScanner ds = documents.getDirectoryScanner(getProject());
        String[] includedFiles = ds.getIncludedFiles();
        File baseDir = ds.getBasedir();

        try {
            for (String value : includedFiles) {
                File src = new File(baseDir, value);
                log("Processing "+src, Project.MSG_INFO);

                context.getVariables().put("fileName",src.getName());

                // wrap the whole thing into JellyTag to prevent whitespace trimming
                TagScript root = TagScript.newInstance(JellyTag.class);
                Script child = context.compileScript(src.toURL());
                root.setTagBody(child);
                ((TagScript)child).setParent(root);
                root.addAttribute("trim",new ConstantExpression(false));

                Charset cs = Charset.defaultCharset();

                FileOutputStream out = new FileOutputStream(new File(destdir,value));
                try {
                    XMLOutput xo = HTMLOutput.create(out);

                    root.run(context,xo);
                    xo.close();
                } finally {
                    out.close();
                }
            }
        } catch (JellyException e) {
            throw new BuildException(e);
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}
