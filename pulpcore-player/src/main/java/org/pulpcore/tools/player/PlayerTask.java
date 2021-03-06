/*
    Copyright (c) 2007-2010, Interactive Pulp, LLC
    All rights reserved.
    
    Redistribution and use in source and binary forms, with or without 
    modification, are permitted provided that the following conditions are met:

        * Redistributions of source code must retain the above copyright 
          notice, this list of conditions and the following disclaimer.
        * Redistributions in binary form must reproduce the above copyright 
          notice, this list of conditions and the following disclaimer in the 
          documentation and/or other materials provided with the distribution.
        * Neither the name of Interactive Pulp, LLC nor the names of its 
          contributors may be used to endorse or promote products derived from 
          this software without specific prior written permission.
    
    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
    POSSIBILITY OF SUCH DAMAGE.
*/

package org.pulpcore.tools.player;

import java.awt.Frame;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 @goal run
 */
public class PlayerTask extends AbstractMojo {

    /**
     @parameter expression="" default-value="${project.build.directory}"
     @required
     */
    private String path;

    /**
     @parameter expression="" default-value="${project.build.finalName}.jar"
     @required
     */
    private String archive;

    /**
     @parameter expression="" default-value="${project.build.finalName}.zip"
     @required
     */
    private String assets;

    /**
     @parameter expression="" default-value="${pulpcore.scene}"
     @required
     */
    private String scene;

    /**
     @parameter expression="" default-value="${pulpcore.width}"
     @required
     */
    private int width;

    /**
     @parameter expression="" default-value="${pulpcore.height}"
     @required
     */
    private int height;

    /**
     @parameter expression="" default-value="${pulpcore.params}"
     */
    private String params;

    /**
     @parameter
     */
    private boolean waitUntilClosed = true;
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public void setArchive(String archive) {
        this.archive = archive;
    }
    
    public void setScene(String scene) {
        this.scene = scene;
    }
    
    public void setParams(String params) {
        this.params = params;
    }
    
    public void setAssets(String assets) {
        this.assets = assets;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public void setWaitUntilClosed(boolean waitUntilClosed) {
        this.waitUntilClosed = waitUntilClosed;
    }
    
    public void execute() throws MojoExecutionException {
        if (path == null) {
            throw new MojoExecutionException("The path is not specified.");
        }
        if (archive == null) {
            throw new MojoExecutionException("The archive is not specified.");
        }
        if (scene == null) {
            throw new MojoExecutionException("The scene is not specified.");
        }
        if (width <= 0) {
            throw new MojoExecutionException("The width is not specified.");
        }
        if (height <= 0) {
            throw new MojoExecutionException("The height is not specified.");
        }

        // This input handler on Mac OS X causes deadlock - warn about it (killing it doesn't work?)
//        if ("Mac OS X".equals(System.getProperty("os.name"))) {
//            String badInputHandler =
//                    "org.eclipse.ant.internal.ui.antsupport.inputhandler.ProxyInputHandler";
//            InputHandler inputHandler = getProject().getInputHandler();
//            if (inputHandler != null && inputHandler.getClass().getName().equals(badInputHandler)) {
//                log("Running PulpCore in this Eclipse configuration may cause a deadlock. Solutions:");
//                log("Select Run->External Tools->External Tools Configurations->JRE->Run in same JRE as the workspace.");
//                log("Or...");
//                log("Deselect Run->External Tools->External Tools Configurations->Main->Set an Input handler");
//            }
//        }
        
        Map<String, String> appProperties = parseParams();
        appProperties.put("scene", scene);
        appProperties.put("assets", assets);
        
        if (!waitUntilClosed) {
            // Find an existing player within a running IDE
            // Note, Ant creates a new ClassLoader each time a taskdef'd task is executed.
            // So we can't access the same class since it was started from a different ClassLoader.
            // A trick: loop through existing frames to find the class.
            String className = PulpCorePlayer.class.getName();
            Frame[] frames = Frame.getFrames();
            for (int i = 0; i < frames.length; i++) {
                Class c = frames[i].getClass();
                if (c.getName().equals(className)) {
                    try {
                        Method m = c.getMethod("start", String.class, String.class, 
                            Integer.TYPE, Integer.TYPE, Map.class, Boolean.TYPE);
                        m.invoke(null, path, archive, width, height, 
                            appProperties, waitUntilClosed);
                        return;
                    }
                    catch (Exception ex) {
                        getLog().error(ex);
                    }
                }
            }
        }
        
        // No running copy found: start a new one.
        PulpCorePlayer.start(path, archive, width, height, 
            appProperties, waitUntilClosed);
    }
    
    /**
        Parse JavaScript formatted parameters. Example:
        <code>name: "John", avatar: "robot", id: 12345</code>
    */
    private Map<String, String> parseParams() throws MojoExecutionException {
        
        Map<String, String> map = new HashMap<String, String>();
        
        if (params == null || params.length() == 0) {
            return map;
        }
        
        String optionalSpace = "\\s*";
        String identifierStart = "[a-zA-Z_\\$]";
        String identifierPart = "[a-zA-Z_\\$0-9]";
        String identifier = "(" + identifierStart + identifierPart + "*" + ")";
        String stringValue = "\"([^\\\"]*)\"";
        String decimalValue = "[0-9\\.]+";
        String value = "(" + stringValue + "|" + decimalValue + ")";
        String end = optionalSpace + "(,|\\z)";
        
        String regex = optionalSpace +
            identifier + ":" + optionalSpace + 
            value + end;
        
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(params);
        
        int index = 0;
        while (matcher.find(index)) {
            if (matcher.start() != index) {
                throw new MojoExecutionException("Could not parse substring: " +
                    params.substring(index, matcher.start()));
            }
            index = matcher.end();
            
            String paramName = matcher.group(1);
            String paramValue = matcher.group(3); // stringValue
            if (paramValue == null) {
                paramValue = matcher.group(2); // decimalValue
            }
            
            map.put(paramName, paramValue);
            //log(paramName + " = " + paramValue);
        }
        
        if (map.size() == 0) {
            throw new MojoExecutionException("Invalid params: " + params);
        }
        
        return map;
    }
}
