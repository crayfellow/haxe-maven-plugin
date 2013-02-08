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
package com.yelbota.plugins.haxe.components.repository;

import com.yelbota.plugins.haxe.utils.PackageTypes;
import com.yelbota.plugins.haxe.components.nativeProgram.NativeProgram;
import com.yelbota.plugins.haxe.components.nativeProgram.HaxelibNativeProgram;
import com.yelbota.plugins.haxe.components.nativeProgram.NativeProgramException;
import com.yelbota.plugins.haxe.utils.HaxeFileExtensions;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.spi.connector.*;
import org.sonatype.aether.transfer.ArtifactTransferException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.annotations.Requirement;
import com.yelbota.plugins.nd.UnpackHelper;
import com.yelbota.plugins.nd.utils.DefaultUnpackMethods;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.File;

public class HaxelibRepositoryConnector implements RepositoryConnector {

    @Requirement
    private RepositorySystem repositorySystem;

    //-------------------------------------------------------------------------
    //
    //  Fields
    //
    //-------------------------------------------------------------------------

    private final RemoteRepository repository;

    private final RepositoryConnector defaultRepositoryConnector;

    private final NativeProgram haxelib;

    private final Logger logger;

    private boolean needsSet = false;

    //-------------------------------------------------------------------------
    //
    //  Public methods
    //
    //-------------------------------------------------------------------------

    public HaxelibRepositoryConnector(RemoteRepository repository, RepositoryConnector defaultRepositoryConnector, NativeProgram haxelib, Logger logger)
    {
        this.repository = repository;
        this.defaultRepositoryConnector = defaultRepositoryConnector;
        this.haxelib = haxelib;
        this.logger = logger;
    }

    @Override
    public void get(Collection<? extends ArtifactDownload> artifactDownloads, Collection<? extends MetadataDownload> metadataDownloads)
    {
        if (artifactDownloads == null)
        {
            defaultRepositoryConnector.get(artifactDownloads, metadataDownloads);
        }
        else
        {
            ArrayList<ArtifactDownload> normalArtifacts = new ArrayList<ArtifactDownload>();
            ArrayList<ArtifactDownload> haxelibArtifacts = new ArrayList<ArtifactDownload>();
            ArrayList<ArtifactDownload> pomHaxelibArtifacts = new ArrayList<ArtifactDownload>();

            // Separate artifacts collection. Get haxelib artifacts and all others.
            for (ArtifactDownload artifactDownload : artifactDownloads)
            {
                Artifact artifact = artifactDownload.getArtifact();
                if (artifact.getExtension().equals(HaxeFileExtensions.HAXELIB))
                    haxelibArtifacts.add(artifactDownload);
                else if (artifact.getExtension().equals(HaxeFileExtensions.POM_HAXELIB))
                    pomHaxelibArtifacts.add(artifactDownload);
                else normalArtifacts.add(artifactDownload);
            }

            // Get normal artifacts
            defaultRepositoryConnector.get(normalArtifacts, metadataDownloads);

            getHaxelibs(haxelibArtifacts);
            getPomHaxelibs(pomHaxelibArtifacts);
        }
    }

    private void getHaxelibs(List<ArtifactDownload> haxelibArtifacts)
    {
        for (ArtifactDownload artifactDownload : haxelibArtifacts)
        {
            Artifact artifact = artifactDownload.getArtifact();
            logger.info("Resolving " + artifact);
            if (artifact.getExtension().equals(HaxeFileExtensions.HAXELIB))
            {
                try
                {
                    int code = haxelib.execute(
                            "install",
                            artifact.getArtifactId(),
                            artifact.getVersion()
                    );

                    if (code > 0)
                    {
                        artifactDownload.setException(new ArtifactTransferException(
                                artifact, repository, "Can't resolve artifact " + artifact.toString()));
                    }
                    else
                    {
                        try
                        {
                            // TODO In this case we can had a problems with new version of plugin.
                            // TODO Need custom dependency resolver.
                            artifactDownload.getFile().createNewFile();
                        }
                        catch (IOException e)
                        {
                            logger.error("Can't create haxelib dummy artifact", e);
                        }
                    }
                }
                catch (NativeProgramException e)
                {
                    artifactDownload.setException(new ArtifactTransferException(
                            artifact, repository, e));
                }
            }
        }
    }

    private void getPomHaxelibs(List<ArtifactDownload> pomHaxelibArtifacts)
    {
        for (ArtifactDownload artifactDownload : pomHaxelibArtifacts)
        {
            Artifact artifact = artifactDownload.getArtifact();
            logger.info("Resolving hybrid POM/haxelib '"+artifact.getArtifactId()+"'");
            if (artifact.getExtension().equals(HaxeFileExtensions.POM_HAXELIB))
            {
                artifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), PackageTypes.TARGZ, artifact.getVersion());
                artifactDownload.setArtifact(artifact);

                File artifactFile = artifactDownload.getFile();
                String packagePath = artifactFile.getAbsolutePath().replace(HaxeFileExtensions.POM_HAXELIB, PackageTypes.TARGZ);
                artifactDownload.setFile(new File(packagePath));

                File unpackDirectory = getUnpackDirectoryForArtifact(artifact);
                File testFile = artifactDownload.getFile();

                if (!testFile.exists() || !unpackDirectory.exists()) {
                    ArrayList<ArtifactDownload> artifacts = new ArrayList<ArtifactDownload>();
                    artifacts.add(artifactDownload);
                    defaultRepositoryConnector.get(artifacts, null);

                    ArtifactTransferException exception = artifactDownload.getException();
                    if (exception == null) {
                        artifactFile = artifactDownload.getFile();

                        File tmpDir = new File(artifactFile.getParentFile(), artifact.getArtifactId() + "-unpack");

                        if (tmpDir.exists())
                            tmpDir.delete();

                        UnpackHelper unpackHelper = new UnpackHelper() {};
                        DefaultUnpackMethods unpackMethods = new DefaultUnpackMethods(logger);
                        try {
                            unpackHelper.unpack(tmpDir, artifactDownload, unpackMethods, null);
                        }
                        catch (Exception e)
                        {
                            logger.error(String.format("Can't unpack %s", artifact.getArtifactId(), e));
                        }

                        for (String firstFileName : tmpDir.list())
                        {
                            File firstFile = new File(tmpDir, firstFileName);
                            firstFile.renameTo(unpackDirectory);
                            break;
                        }

                        if (tmpDir.exists())
                            tmpDir.delete();

                        if (needsSet) {
                            try
                            {
                                haxelib.execute("set", artifact.getArtifactId(), artifact.getVersion());
                            }
                            catch (NativeProgramException e)
                            {
                                logger.error("Unable to set version for haxelib '"+artifact.getArtifactId()+"'.", e);
                                // todo: throw exception
                            }
                        }
                    } else {
                        logger.error("Unable to resolve " + HaxeFileExtensions.POM_HAXELIB + " artifact: " + artifact);
                    }
                }
            }
        }
    }
    
    private File getUnpackDirectoryForArtifact(Artifact artifact)
    {
        File unpackDirectory = artifact.getFile();
        HaxelibNativeProgram haxelibNativeProgram = (HaxelibNativeProgram) haxelib;
        if (haxelibNativeProgram != null) {
            File haxelibHome = new File(haxelibNativeProgram.getLocalRepositoryPath(), artifact.getArtifactId());
            if (!haxelibHome.exists()) {
                haxelibHome.mkdirs();
            }

            File currentFile = new File(haxelibHome, ".current");
            if (!currentFile.exists()) {
                try {
                    needsSet = true;
                    currentFile.createNewFile();
                } catch (IOException e) {
                    logger.error("Unable to create pointer for '"+artifact.getArtifactId()+"' haxelib.", e);
                    // todo: throw exception
                }
            }
            unpackDirectory = new File(haxelibHome, artifact.getVersion().replace(".", ","));
        }
        return unpackDirectory;
    }

    @Override
    public void put(Collection<? extends ArtifactUpload> artifactUploads, Collection<? extends MetadataUpload> metadataUploads)
    {
        // TODO Deploying to http://lib.haxe.org. Need to define haxelib packaging?
    }

    @Override
    public void close()
    {
    }
}
