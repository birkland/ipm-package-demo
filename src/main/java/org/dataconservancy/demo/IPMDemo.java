
package org.dataconservancy.demo;

import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.ModelFactory;

import org.dataconservancy.packaging.tool.api.Package;
import org.dataconservancy.packaging.tool.api.PackageGenerationService;
import org.dataconservancy.packaging.tool.impl.IpmRdfTransformService;
import org.dataconservancy.packaging.tool.model.GeneralParameterNames;
import org.dataconservancy.packaging.tool.model.PackageGenerationParameters;
import org.dataconservancy.packaging.tool.model.PackageState;
import org.dataconservancy.packaging.tool.model.PropertiesConfigurationParametersBuilder;
import org.dataconservancy.packaging.tool.model.ipm.FileInfo;
import org.dataconservancy.packaging.tool.model.ipm.Node;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/* A quick app that builds a package from RDF and binary via creating an PIM tree */
public class IPMDemo {

    static final String PACKAGE_NAME = "MyPackage";

    static final ClassPathXmlApplicationContext cxt =
            new ClassPathXmlApplicationContext("classpath*:applicationContext.xml",
                                               "classpath*:org/dataconservancy/config/applicationContext.xml",
                                               "classpath*:org/dataconservancy/packaging/tool/ser/config/applicationContext.xml");

    public static void main(String[] args) throws Exception {

        IpmRdfTransformService ipm2rdf =
                cxt.getBean(IpmRdfTransformService.class);

        PackageState state = new PackageState();

        /* Put the domain object RDF into the package state */
        state.setDomainObjectRDF(ModelFactory.createDefaultModel()
                .read(IPMDemo.class
                        .getResourceAsStream("/content/domainObjects.ttl"),
                      null,
                      "TTL"));

        /*
         * Now put the IPM tree in the package state. We build it in java via
         * buildContentTree(), then serialize to RDF
         */
        state.setPackageTree(ipm2rdf.transformToRDF(buildContentTree()));

        /* Construct the package */
        Package pkg = buildPackage(state);

        /* Now just write the package out to a file */
        FileOutputStream out = new FileOutputStream(PACKAGE_NAME + ".tar.gz");
        IOUtils.copy(pkg.serialize(), out);
        out.close();
        pkg.cleanupPackage();

        System.out.println("DONE");

    }

    /*
     * We manually build the IPM tree here. Fundamentally, we're doing three
     * things: 1) Creating "directory" nodes that correspond to a domain object.
     * 2) Creating "content" nodes that correspond to a domain object that
     * describes associated content. 3) Arranging these nodes into a tree
     * structure of our liking.
     */
    private static Node buildContentTree() throws Exception {

        /*
         * First the nodes - correlate each node with a domain object and
         * possibly content
         */

        /*
         * These are the IDs of our domain objects. In this quick example, we
         * know this a priori
         */
        final String id1 = "test:/1";
        final String id2 = "test:/2";
        final String id3 = "test:/3";

        Node one = new Node(URI.create(id1));
        one.setDomainObject(URI.create(id1));
        one.setFileInfo(directory("one"));

        Node two = new Node(URI.create(id2));
        two.setDomainObject(URI.create(id2));
        two.setFileInfo(directory("two"));

        Node three = new Node(URI.create(id3));
        three.setDomainObject(URI.create(id3));
        three.setFileInfo(contentFromClasspath("/content/three.txt"));

        /* Now the hierarchy */

        one.addChild(two);
        two.addChild(three);

        return one;
    }

    /**
     * Create a FileInfo that points to file content present in the classpath.
     * <p>
     * The logical name of the file represented in the FileInfo is the file's
     * name, so the /path/to/file.txt will hafe name file.txt
     * </p>
     * 
     * @param path
     *        Path to the file content in the classpath.
     * @return populated FileInfo
     * @throws Exception
     */
    private static FileInfo contentFromClasspath(String path) throws Exception {
        FileInfo info = new FileInfo(Paths
                .get(IPMDemo.class.getResource(path).toURI()));
        info.setIsFile(true);
        return info;
    }

    /**
     * Create a FileInfo that names a directory.
     * <p>
     * This is a *logical* name for a directory in an IPM tree. The physical
     * directory pointed to by the resulting FileInfo is irrelevant. Only the
     * given name matters.
     * </p>
     * 
     * @param name
     *        Given directory name
     * @return FileInfo for a node corresponding to this directory.
     */
    private static FileInfo directory(String name) {
        /*
         * DomainObjectResourceBuilder has a sanity check that files and
         * directories must exist. This check is not meaningful here, since
         * we're not creating an IPM tree from a filesystem. We should eliminate
         * or move that sanity check. In the meantime, just use cwd as a
         * workaround"
         */
        FileInfo info = new FileInfo(Paths.get(".").toUri(), name);
        info.setIsDirectory(true);
        return info;
    }

    /* Package building boilerplate */
    private static Package buildPackage(PackageState state) throws Exception {
        PackageGenerationParameters params =
                new PropertiesConfigurationParametersBuilder()
                        .buildParameters(IPMDemo.class
                                .getResourceAsStream("/PackageGenerationParams.properties"));

        PackageGenerationService generator =
                cxt.getBean(PackageGenerationService.class);

        params.addParam(GeneralParameterNames.PACKAGE_LOCATION,
                        System.getProperty("java.io.tmpdir"));
        params.addParam(GeneralParameterNames.PACKAGE_NAME, PACKAGE_NAME);

        return generator.generatePackage(state, params);
    }
}
