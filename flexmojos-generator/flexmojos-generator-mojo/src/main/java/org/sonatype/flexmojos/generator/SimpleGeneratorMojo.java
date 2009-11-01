/**
 * Copyright 2008 Marvin Herman Froeder
 * Copyright 2009 Edward Yakop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */
package org.sonatype.flexmojos.generator;

import static java.lang.Thread.currentThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.SelectorUtils;
import org.sonatype.flexmojos.generator.api.GenerationException;
import org.sonatype.flexmojos.generator.api.GenerationRequest;
import org.sonatype.flexmojos.generator.api.Generator;
import org.sonatype.flexmojos.generator.api.GeneratorFactory;

/**
 * This goal generate actionscript 3 code based on Java classes. It does uses Granite GAS3.
 * 
 * @author Marvin Herman Froeder (velo.br@gmail.com)
 * @author edward.yakop@gmail.com
 * @goal generate
 * @phase generate-sources
 * @requiresDependencyResolution test
 * @since 1.0
 */
public class SimpleGeneratorMojo
    extends AbstractMojo
{

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * File to generate as3 file. If not defined assumes all classes must be included
     * 
     * @parameter
     * @deprecated
     */
    private String[] includeClasses;

    /**
     * File to exclude from as3 generation. If not defined, assumes no exclusions
     * 
     * @parameter
     * @deprecated
     */
    private String[] excludeClasses;

    /**
     * File to generate as3 file. If not defined assumes all classes must be included
     * 
     * @parameter
     */
    private String[] includeJavaClasses;

    /**
     * File to exclude from as3 generation. If not defined, assumes no exclusions
     * 
     * @parameter
     */
    private String[] excludeJavaClasses;

    /**
     * @parameter default-value="${project.build.sourceDirectory}"
     */
    private File outputDirectory;

    /**
     * @parameter default-value="${project.build.directory}/generated-sources/flexmojos"
     */
    private File baseOutputDirectory;

    /**
     * @parameter
     * @deprecated
     */
    private String uid = "uid";

    /**
     * @parameter
     * @deprecated
     */
    private String[] entityTemplate;

    /**
     * @parameter
     * @deprecated
     */
    private String[] interfaceTemplate;

    /**
     * @parameter
     * @deprecated
     */
    private String[] beanTemplate;

    /**
     * @parameter
     * @deprecated
     */
    private String[] enumTemplate;

    /**
     * Controls whether or not enum classes are output to the baseOutputDirectory (true) or the outputDirectory (false)
     * 
     * @parameter default-value="false"
     * @deprecated
     */
    private boolean outputEnumToBaseOutputDirectory;

    /**
     * @parameter default-value="graniteds1" expression="${generatorToUse}"
     */
    private String generatorToUse;

    /**
     * @parameter default-value="false";
     * @deprecated
     */
    private boolean useTideEntityTemplate;

    /**
     * @component role="org.sonatype.flexmojos.generator.api.GeneratorFactory"
     */
    private GeneratorFactory generatorFactory;

    /**
     * Configurations used by the generator implementation, check generator docs to see which parameters can be used.
     * 
     * @parameter
     */
    private Map<String, String> extraOptions;

    /**
     * Templates used by the generator implementation, check generator docs to see which Templates can be used. Example:
     * 
     * <pre>
     * &lt;templates&gt;
     *   &lt;base-enum-template&gt;your-template&lt;/base-enum-template&gt;
     * &lt;/templates&gt;
     * </pre>
     * 
     * @parameter
     */
    private Map<String, String> templates;

    public void execute()
        throws MojoExecutionException
    {
        setUp();

        GenerationRequest request = new GenerationRequest();
        request.setClasses( getFilesToGenerator() );
        request.setClassLoader( this.initializeClassLoader() );
        request.setExtraOptions( extraOptions );
        request.setPersistentOutputFolder( outputDirectory );
        request.setTemplates( templates );
        request.setTransientOutputFolder( baseOutputDirectory );

        ClassLoader cl = currentThread().getContextClassLoader();

        try
        {
            currentThread().setContextClassLoader( request.getClassLoader() );

            Generator generator = generatorFactory.getGenerator( generatorToUse );
            generator.generate( request );
        }
        catch ( GenerationException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        finally
        {
            currentThread().setContextClassLoader( cl );
        }
    }

    private void setUp()
        throws MojoExecutionException
    {
        if ( includeJavaClasses == null )
        {
            if ( includeClasses != null )
            {
                includeJavaClasses = includeClasses;
            }
            else
            {
                includeJavaClasses = new String[] { "*" };
            }
        }

        if ( excludeJavaClasses == null )
        {
            excludeJavaClasses = excludeClasses;
        }

        if ( !outputDirectory.exists() )
        {
            outputDirectory.mkdirs();
        }

        String outputPath = outputDirectory.getAbsolutePath();
        if ( !project.getCompileSourceRoots().contains( outputPath ) )
        {
            project.addCompileSourceRoot( outputPath );
        }

        if ( !baseOutputDirectory.exists() )
        {
            baseOutputDirectory.mkdirs();
        }
        String baseOutputPath = baseOutputDirectory.getAbsolutePath();
        if ( !project.getCompileSourceRoots().contains( baseOutputPath ) )
        {
            project.addCompileSourceRoot( baseOutputPath );
        }

        if ( extraOptions == null )
        {
            extraOptions = new HashMap<String, String>();
            extraOptions.put( "uid", uid );
            extraOptions.put( "outputEnumToBaseOutputDirectory", String.valueOf( outputEnumToBaseOutputDirectory ) );
            extraOptions.put( "tide", String.valueOf( useTideEntityTemplate ) );
        }

        if ( templates == null )
        {
            templates = new HashMap<String, String>();
            if ( enumTemplate != null )
            {
                templates.put( "enum-template", enumTemplate[0] );
            }
            if ( interfaceTemplate != null )
            {
                templates.put( "base-interface-template", interfaceTemplate[0] );
                templates.put( "interface-template", interfaceTemplate[1] );
            }
            if ( entityTemplate != null )
            {
                templates.put( "base-entity-template", entityTemplate[0] );
                templates.put( "entity-template", entityTemplate[1] );
            }
            if ( beanTemplate != null )
            {
                templates.put( "base-bean-template", beanTemplate[0] );
                templates.put( "bean-template", beanTemplate[1] );
            }
        }
    }

    private final Map<String, File> getFilesToGenerator()
        throws MojoExecutionException
    {
        List<String> classpaths = getDirectDependencies();
        Map<String, File> classes = new HashMap<String, File>();

        for ( String fileName : classpaths )
        {
            File file = new File( fileName ).getAbsoluteFile();

            if ( file.isDirectory() )
            {
                DirectoryScanner ds = new DirectoryScanner();
                ds.setBasedir( file );
                ds.setIncludes( new String[] { "**/*.class" } );
                ds.scan();

                for ( String classFileName : ds.getIncludedFiles() )
                {
                    String className = classFileName.replace( File.separatorChar, '.' );
                    className = className.substring( 0, className.length() - 6 );

                    if ( matchWildCard( className, includeJavaClasses )
                        && !matchWildCard( className, excludeJavaClasses ) )
                    {
                        classes.put( className, new File( file, classFileName ) );
                    }
                }
            }
            else
            {

                try
                {
                    JarInputStream jar = new JarInputStream( new FileInputStream( file ) );

                    JarEntry jarEntry;
                    while ( true )
                    {
                        jarEntry = jar.getNextJarEntry();

                        if ( jarEntry == null )
                        {
                            break;
                        }

                        String className = jarEntry.getName();

                        if ( jarEntry.isDirectory() || !className.endsWith( ".class" ) )
                        {
                            continue;
                        }

                        className = className.replace( '/', '.' );
                        className = className.substring( 0, className.length() - 6 );

                        if ( matchWildCard( className, includeJavaClasses )
                            && !matchWildCard( className, excludeJavaClasses ) )
                        {
                            classes.put( className, file );
                        }
                    }
                }
                catch ( IOException e )
                {
                    throw new MojoExecutionException( "Error on classes resolve", e );
                }
            }
        }

        return classes;
    }

    private boolean matchWildCard( String className, String... wildCards )
    {
        if ( wildCards == null )
        {
            return false;
        }

        for ( String wildCard : wildCards )
        {
            if ( SelectorUtils.matchPath( wildCard, className ) )
            {
                return true;
            }
        }

        return false;
    }

    private ClassLoader initializeClassLoader()
        throws MojoExecutionException
    {
        List<String> classpaths = getClasspath();

        try
        {
            List<URL> classpathsUrls = new ArrayList<URL>();

            // add all the jars to the new child realm
            for ( String path : classpaths )
            {
                URL url = new File( path ).toURI().toURL();
                classpathsUrls.add( url );
            }

            return new URLClassLoader( classpathsUrls.toArray( new URL[0] ), currentThread().getContextClassLoader() );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Unable to get dependency URL", e );
        }
    }

    @SuppressWarnings( "unchecked" )
    private List<String> getClasspath()
        throws MojoExecutionException
    {
        List<String> classpaths;
        try
        {
            classpaths = project.getCompileClasspathElements();
            classpaths.remove( project.getBuild().getOutputDirectory() );
        }
        catch ( DependencyResolutionRequiredException e )
        {
            throw new MojoExecutionException( "Failed to find dependencies", e );
        }
        return classpaths;
    }

    private List<String> getDirectDependencies()
        throws MojoExecutionException
    {
        List<String> classpaths = new ArrayList<String>();
        Set<Artifact> artifacts = project.getDependencyArtifacts();
        for ( Artifact artifact : artifacts )
        {
            if ( "jar".equals( artifact.getType() ) )
            {
                classpaths.add( artifact.getFile().getAbsolutePath() );
            }
        }
        return classpaths;
    }

}