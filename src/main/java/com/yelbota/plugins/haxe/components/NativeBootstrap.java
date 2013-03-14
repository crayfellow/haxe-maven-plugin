/**
 * Copyright (C) 2012 https://github.com/yelbota/haxe-maven-plugin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yelbota.plugins.haxe.components;

import javax.annotation.Nonnull;
import com.yelbota.plugins.haxe.utils.HaxeFileExtensions;
import com.yelbota.plugins.haxe.utils.HaxelibHelper;
import com.yelbota.plugins.haxe.utils.PackageTypes;
import com.yelbota.plugins.haxe.utils.OSClassifiers;
import com.yelbota.plugins.haxe.components.nativeProgram.HaxelibNativeProgram;
import com.yelbota.plugins.haxe.components.nativeProgram.NativeProgram;
import com.yelbota.plugins.haxe.components.nativeProgram.NativeProgramException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.util.*;

@Component(role = NativeBootstrap.class)
public class NativeBootstrap {

    //-------------------------------------------------------------------------
    //
    //  Injection
    //
    //-------------------------------------------------------------------------

    @Requirement
    private RepositorySystem repositorySystem;

    @Requirement(hint = "haxe")
    private NativeProgram haxe;

    @Requirement(hint = "neko")
    private NativeProgram neko;

    @Requirement(hint = "haxelib")
    private HaxelibNativeProgram haxelib;

    @Requirement(hint = "nme")
    private NativeProgram nme;

    @Requirement(hint = "munit")
    private NativeProgram munit;

    @Requirement(hint = "chxdoc")
    private NativeProgram chxdoc;

    @Requirement
    private Logger logger;

    //-------------------------------------------------------------------------
    //
    //  Fields
    //
    //-------------------------------------------------------------------------

    private MavenProject project;

    private ArtifactRepository localRepository;

    //-------------------------------------------------------------------------
    //
    //  Public
    //
    //-------------------------------------------------------------------------

    public void initialize(MavenProject project, ArtifactRepository localRepository) throws Exception
    {
        this.project = project;
        this.localRepository = localRepository;

        Map<String, Plugin> pluginMap = project.getBuild().getPluginsAsMap();
        Plugin plugin = pluginMap.get("com.yelbota.plugins:haxe-maven-plugin");
        Artifact pluginArtifact = resolveArtifact(repositorySystem.createPluginArtifact(plugin), false);
        String pluginHomeName = plugin.getArtifactId() + "-" + plugin.getVersion();
        File pluginHome = new File(pluginArtifact.getFile().getParentFile(), pluginHomeName);

        if (!pluginHome.exists())
            pluginHome.mkdirs();

        initializePrograms(pluginHome, plugin.getDependencies());
        initializeHaxelib(pluginHome);
    }

    //-------------------------------------------------------------------------
    //
    //  Private methods
    //
    //-------------------------------------------------------------------------

    private void initializePrograms(File pluginHome, List<Dependency> pluginDependencies) throws Exception
    {
        Map<String, Artifact> artifactsMap = new HashMap<String, Artifact>();
        Set<String> path = new HashSet<String>();
        File outputDirectory = getOutputDirectory();

        // Add java to PATH
        path.add(new File(System.getProperty("java.home"), "bin").getAbsolutePath());

        for (Dependency dependency : pluginDependencies)
        {
            String artifactKey = dependency.getGroupId() + ":" + dependency.getArtifactId();

            if (artifactKey.equals(HAXE_COMPILER_KEY) 
                || artifactKey.equals(NEKO_KEY)
                || artifactKey.equals(NME_KEY))
            {
                String classifier = OSClassifiers.getDefaultClassifier();
                String packaging = PackageTypes.getSDKArtifactPackaging(classifier);
                if (artifactKey.equals(NME_KEY)) classifier = null;
                Artifact artifact = repositorySystem.createArtifactWithClassifier(
                        dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
                        packaging, classifier
                );

                Artifact resolvedArtifact = resolveArtifact(artifact, true);
                boolean resolvedLocally = (resolvedArtifact != null);
                if (!resolvedLocally) {
                    resolvedArtifact = resolveArtifact(artifact, false);
                }
                if (resolvedArtifact != null) {
                    logger.info("toolchain resolved '"+resolvedArtifact.getArtifactId()+"' with version '"+resolvedArtifact.getVersion()+"' locally: " + resolvedLocally);
                    artifactsMap.put(artifactKey, resolvedArtifact);
                }
            }
        }

        if (artifactsMap.get(NEKO_KEY) == null)
        {
            throw new Exception(String.format(
                    "Neko Runtime dependency (%s) not fount in haxe-maven-plugin dependencies",
                    NEKO_KEY));
        }

        if (artifactsMap.get(HAXE_COMPILER_KEY) == null)
        {
            throw new Exception(String.format(
                    "Haxe Compiler dependency (%s) not fount in haxe-maven-plugin dependencies",
                    HAXE_COMPILER_KEY));
        }

        neko.initialize(artifactsMap.get(NEKO_KEY), outputDirectory, pluginHome, path);
        haxe.initialize(artifactsMap.get(HAXE_COMPILER_KEY), outputDirectory, pluginHome, path);
        haxelib.initialize(artifactsMap.get(HAXE_COMPILER_KEY), outputDirectory, pluginHome, path);
        HaxelibHelper.setHaxelib(haxelib);

        Set<Artifact> projectDependencies = project.getDependencyArtifacts();
        if (projectDependencies != null) {
            Iterator<Artifact> iterator = projectDependencies.iterator();
            while(iterator.hasNext()) {
                Artifact a = iterator.next();

                if (a.getType().equals(HaxeFileExtensions.HAXELIB)) {
                    File haxelibDirectory = HaxelibHelper.getHaxelibDirectoryForArtifact(a.getArtifactId(), a.getVersion());

                    if (haxelibDirectory != null && haxelibDirectory.exists()) {
                        iterator.remove();
                    }
                } else {
                    if (a.getClassifier().equals("haxelib")) {
                        String packaging = PackageTypes.getSDKArtifactPackaging(OSClassifiers.getDefaultClassifier());
                        Artifact artifact = repositorySystem.createArtifactWithClassifier(
                                a.getGroupId(), a.getArtifactId(), a.getVersion(),
                                packaging, null
                        );
                        Artifact resolvedArtifact = resolveArtifact(artifact, true);
                        boolean resolvedLocally = (resolvedArtifact != null);
                        if (!resolvedLocally) {
                            resolvedArtifact = resolveArtifact(artifact, false);
                        }
                        if (resolvedArtifact != null) {
                            HaxelibHelper.injectPomHaxelib(resolvedArtifact, outputDirectory, logger, resolvedLocally);
                            iterator.remove();
                        }
                    }
                }

                if (a.getArtifactId().equals(MUNIT_ID)) {
                    munit.initialize(a, outputDirectory, pluginHome, path);
                }

                if (a.getArtifactId().equals(CHXDOC_ID)) {
                    chxdoc.initialize(a, outputDirectory, pluginHome, path);
                }
            }
        }

        if (artifactsMap.get(NME_KEY) != null) {
            if (projectDependencies != null) {
                Iterator<Artifact> iterator = projectDependencies.iterator();
                while(iterator.hasNext()) {
                    Artifact a = iterator.next();
                    if (a.getType().equals(HaxeFileExtensions.HAXELIB)
                        && a.getArtifactId().equals("nme")
                        && (a.getVersion() == null 
                            || a.getVersion().equals("")
                            || a.getVersion().equals(artifactsMap.get(NME_KEY).getVersion()))) {
                        iterator.remove();
                    }
                }
            }
            nme.initialize(artifactsMap.get(NME_KEY), outputDirectory, pluginHome, path);
        }
    }
    
    private void initializeHaxelib(File pluginHome) throws Exception
    {
        // Add haxelib virtual repository.
        project.getRemoteArtifactRepositories().add(
                new MavenArtifactRepository("lib.haxe.org", "http://lib.haxe.org",
                new HaxelibRepositoryLayout(),
                new ArtifactRepositoryPolicy(false, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE),
                new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE)
        ));
    }

    @Nonnull
    private File getOutputDirectory()
    {
        File outputDirectory = new File(project.getBuild().getDirectory());

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        else if (!outputDirectory.isDirectory()) {
            outputDirectory.delete();
            outputDirectory.mkdirs();
        }

        return outputDirectory;
    }

    @Nonnull
    private Artifact resolveArtifact(Artifact artifact, boolean localOnly) throws Exception
    {
        ArtifactResolutionRequest request = new ArtifactResolutionRequest();

        request.setArtifact(artifact);
        request.setLocalRepository(localRepository);
        if (!localOnly) {
            request.setRemoteRepositories(project.getRemoteArtifactRepositories());
        }
        ArtifactResolutionResult resolutionResult = repositorySystem.resolve(request);

        if (!resolutionResult.isSuccess())
        {
            if (artifact.getType().equals(PackageTypes.TGZ)) {
                artifact = repositorySystem.createArtifactWithClassifier(
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                        PackageTypes.TARGZ, artifact.getClassifier());
                request = new ArtifactResolutionRequest();
                request.setArtifact(artifact);
                request.setLocalRepository(localRepository);
                if (!localOnly) {
                    request.setRemoteRepositories(project.getRemoteArtifactRepositories());
                }
                resolutionResult = repositorySystem.resolve(request);
                if (resolutionResult.isSuccess()) {
                    return artifact;
                }
            }
            if (!localOnly) {
                String message = "Failed to resolve artifact " + artifact;
                throw new Exception(message);
            } else {
                artifact = null;
            }
        }

        return artifact;
    }

    private static final String HAXE_COMPILER_KEY = "org.haxe.compiler:haxe-compiler";
    private static final String NEKO_KEY = "org.nekovm:nekovm";
    private static final String NME_KEY = "org.haxenme:nme";
    private static final String MUNIT_ID = "munit";
    private static final String CHXDOC_ID = "chxdoc";

    private class HaxelibRepositoryLayout extends DefaultRepositoryLayout {

        @Override
        public String getId()
        {
            return "haxelib";
        }
    }
    
}
