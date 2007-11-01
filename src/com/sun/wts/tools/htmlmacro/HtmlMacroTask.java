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
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    private List<FileSet> documents = new ArrayList<FileSet>();

    private File destdir;

    /**
     * Encoding of the file to be generated.
     * <p>
     * When HTML files are loaded locally, it's assumed to be in the system default encoding,
     * so we can't reliably use any encoding. The best bet is to use us-ascii, since
     * it's a subet of most of the encodings used today.
     */
    private String encoding = "us-ascii";

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
        this.documents.add(fs);
    }

    /**
     * True to read the source files as HTML.
     */
    public void setXhtml(boolean value) {
        this.html = !value;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
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

        registerTagLibrariesFromClasspath(context);

        for (FileSet document : documents) {
            DirectoryScanner ds = document.getDirectoryScanner(getProject());
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
                        XMLOutput xo = HTMLOutput.create(out,encoding);

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

    /**
     * Look up <tt>META-INF/jelly/taglibs</tt> and register them all
     */
    private void registerTagLibrariesFromClasspath(JellyContext context) {
        try {
            Enumeration<URL> e = context.getClassLoader().getResources("META-INF/jelly/taglibs");
            while(e.hasMoreElements()) {
                URL url = e.nextElement();
                try {
                    Properties p = new Properties();
                    p.load(url.openStream());
                    for (Map.Entry<Object,Object> line : p.entrySet()) {
                        log("Picked up taglib "+line.getKey(), Project.MSG_INFO);
                        context.registerTagLibrary(line.getKey().toString(),line.getValue().toString());
                    }
                } catch (IOException x) {
                    throw new BuildException("Failed to load "+url,x);
                }
            }
        } catch (IOException x) {
            throw new BuildException("Failed to read META-INF/jelly/tagslib",x);
        }
    }
}
