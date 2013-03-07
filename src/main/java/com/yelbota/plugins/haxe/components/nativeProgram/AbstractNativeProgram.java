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
package com.yelbota.plugins.haxe.components.nativeProgram;

import com.yelbota.plugins.haxe.utils.HaxeFileExtensions;
import com.yelbota.plugins.haxe.utils.HaxelibHelper;
import com.yelbota.plugins.haxe.utils.CleanStream;
import com.yelbota.plugins.nd.UnpackHelper;
import com.yelbota.plugins.nd.utils.DefaultUnpackMethods;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents package on native application (or group on applications) as
 * simple executor arguments
 */
public abstract class AbstractNativeProgram implements NativeProgram {

    //-------------------------------------------------------------------------
    //
    //  Injections
    //
    //-------------------------------------------------------------------------

    @Requirement
    protected RepositorySystem repositorySystem;

    @Requirement
    protected Logger logger;

    //-------------------------------------------------------------------------
    //
    //  Fields
    //
    //-------------------------------------------------------------------------

    protected Artifact artifact;

    protected File outputDirectory;

    protected File pluginHome;

    protected Set<String> path;

    protected File directory;

    private boolean initialized = false;

    //-------------------------------------------------------------------------
    //
    //  Public methods
    //
    //-------------------------------------------------------------------------

    public void initialize(Artifact artifact, File outputDirectory, File pluginHome, Set<String> path)
    {
        if (initialized)
            return;

        this.artifact = artifact;
        this.outputDirectory = outputDirectory;
        this.pluginHome = pluginHome;
        this.path = path;

        if (!artifact.getType().equals(HaxeFileExtensions.HAXELIB)
                && !artifact.getType().equals(HaxeFileExtensions.POM_HAXELIB)) {
            try
            {
                this.directory = getDirectory(artifact);
            }
            catch (Exception e)
            {
                logger.error(String.format("Can't unpack %s", artifact.getArtifactId(), e));
            }
            initialized = true;
        }
    }

    public int execute(List<String> arguments) throws NativeProgramException
    {
        return execute(arguments, logger);
    }

    public int execute(List<String> arguments, Logger outputLogger) throws NativeProgramException
    {
        try
        {
            String[] environment = getEnvironment();
            arguments = updateArguments(arguments);
            logger.debug("Executing: " + StringUtils.join(arguments.iterator(), " "));

            Process process;

            process = Runtime.getRuntime().exec(
                    arguments.toArray(new String[]{}),
                    environment,
                    outputDirectory
            );

            return processExecution(process, outputLogger);
        }
        catch (IOException e)
        {
            throw new NativeProgramException("Executable not found", e);
        }
        catch (Exception e)
        {
            throw new NativeProgramException("", e);
        }
    }

    @Override
    public int execute(String[] arguments) throws NativeProgramException
    {
        List<String> list = new ArrayList<String>();

        for (String arg : arguments)
            list.add(arg);

        return execute(list);
    }

    @Override
    public int execute(String arg1) throws NativeProgramException
    {
        return execute(new String[]{arg1});
    }

    @Override
    public int execute(String arg1, String arg2) throws NativeProgramException
    {
        return execute(new String[]{arg1, arg2});
    }

    @Override
    public int execute(String arg1, String arg2, String arg3) throws NativeProgramException
    {
        return execute(new String[]{arg1, arg2, arg3});
    }

    @Override
    public int execute(String arg1, String arg2, String arg3, String arg4) throws NativeProgramException
    {
        return execute(new String[]{arg1, arg2, arg3, arg4});
    }

    protected String[] getEnvironment()
    {
        return new String[]{
                "PATH=" + StringUtils.join(path.iterator(), ":"),
                "HOME=" + pluginHome.getAbsolutePath()
        };
    }

    //-------------------------------------------------------------------------
    //
    //  Protected methods
    //
    //-------------------------------------------------------------------------

    protected abstract List<String> updateArguments(List<String> arguments);

    protected int processExecution(Process process, Logger outputLogger) throws NativeProgramException
    {
        try
        {
            CleanStream cleanError = new CleanStream(
                    process.getErrorStream(),
                    outputLogger, CleanStream.CleanStreamType.ERROR, myName()
            );

            CleanStream cleanOutput = new CleanStream(
                    process.getInputStream(),
                    outputLogger, CleanStream.CleanStreamType.INFO, myName()
            );

            cleanError.start();
            cleanOutput.start();

            return process.waitFor();
        }
        catch (InterruptedException e)
        {
            throw new NativeProgramException("Program was interrupted", e);
        }
    }

    protected String myName() { return "abstract"; }

    public Set<String> getPath()
    {
        return path;
    }

    public String getInstalledPath()
    {
        return this.directory.getAbsolutePath();
    }

    public boolean getInitialized()
    {
        if (!initialized) { 
            if (artifact != null 
                    && (artifact.getType().equals(HaxeFileExtensions.HAXELIB)
                        || artifact.getType().equals(HaxeFileExtensions.POM_HAXELIB))) {
                File haxelibDirectory = HaxelibHelper.getHaxelibDirectoryForArtifact(artifact.getArtifactId(), artifact.getVersion());
                if (haxelibDirectory != null || haxelibDirectory.exists()) {
                    this.directory = haxelibDirectory;
                    initialized = true;
                }
            }
        }
        return initialized;
    }

    protected File getUnpackDirectoryForArtifact(Artifact artifact) throws NativeProgramException
    {
        return new File(pluginHome, getUniqueArtifactPath());
    }

    private String getUniqueArtifactPath()
    {
        return artifact.getArtifactId() + "-" + artifact.getVersion();
    }

    private File getDirectory(Artifact artifact) throws Exception
    {
        File unpackDirectory = getUnpackDirectoryForArtifact(artifact);

        if (!unpackDirectory.exists())
        {
            File tmpDir = new File(outputDirectory, getUniqueArtifactPath() + "-unpack");

            if (tmpDir.exists())
                tmpDir.delete();

            UnpackHelper unpackHelper = new UnpackHelper() {};
            DefaultUnpackMethods unpackMethods = new DefaultUnpackMethods(logger);
            unpackHelper.unpack(tmpDir, artifact, unpackMethods, null);

            for (String firstFileName : tmpDir.list())
            {
                File firstFile = new File(tmpDir, firstFileName);
                //logger.info(" *** moving " + firstFile.getAbsolutePath() + " to " + unpackDirectory.getAbsolutePath());
                //FileUtils.moveDirectory(firstFile, unpackDirectory);
                firstFile.renameTo(unpackDirectory);
                if (!unpackDirectory.exists()) {
                    // manually move using shell call as last ditch
                    Process process = Runtime.getRuntime().exec(
                            "mv " + firstFile.getAbsolutePath() + " " + unpackDirectory.getAbsolutePath()
                    );
                }
                break;
            }

            if (tmpDir.exists())
                tmpDir.delete();
        }

        String directoryPath = unpackDirectory.getAbsolutePath();
        // Add current directory to path
        path.add(directoryPath);
        return unpackDirectory;
    }

    protected boolean isWindows()
    {
        String systemName = System.getProperty("os.name");
        String preparedName = systemName.toLowerCase();

        return preparedName.indexOf("win") > -1;
    }
}