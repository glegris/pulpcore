/*
    Copyright (c) 2007, Interactive Pulp, LLC
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

package pulpcore.platform.applet;

import pulpcore.CoreSystem;

/**
    Uses System.currentTimeMillis() and compensates for backward jumps in
    time that can occur on some systems (notably, Java 5 on Windows 98).
*/
public class SystemTimer {
    
    private Thread granularityThread;
    private long lastTime;
    private long virtualTime;
    
    
    public synchronized void start() {
        lastTime = System.currentTimeMillis();
        virtualTime = 0;
        granularityThread = null;
        
        if (CoreSystem.isWindowsXPorNewer()) {
            // Improves the granularity of the sleep() function on Windows XP
            // See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6435126
            granularityThread = new Thread("PulpCore-Win32Granularity") {
                
                public void run() {
                    while (Thread.currentThread() == granularityThread) {
                        try {
                            Thread.sleep(Integer.MAX_VALUE);
                        }
                        catch (InterruptedException ex) {
                            // Ignore
                        }
                    }
                }
            };
            granularityThread.setDaemon(true);
            granularityThread.start();
        }
    }
    
    
    public synchronized void stop() {
        if (granularityThread != null) {
            Thread t = granularityThread;
            granularityThread = null;
            t.interrupt();
        }
    }
    
    
    public long getTimeMillis() {
        long time = System.currentTimeMillis();
        if (time > lastTime) {
            virtualTime += time - lastTime;
        }
        lastTime = time;
        
        return virtualTime;
    }
    

    public long getTimeMicros() {
        return getTimeMillis() * 1000;
    }
    
    
    public String getName() {
        return "SystemTimer";
    }
    
    
    public long sleepUntilTimeMicros(long timeMicros) {
        if (CoreSystem.isWindowsXPorNewer()) {
            while (true) {
                long currentTimeMicros = getTimeMicros();
                if (currentTimeMicros >= timeMicros - 666) {
                    return currentTimeMicros;
                }
                try {
                    Thread.sleep(1);
                }
                catch (InterruptedException ex) { }
            }
        }
        else {
            long time = timeMicros - getTimeMicros();
            if (time >= 500) {
                try {
                    Thread.sleep((int)((time + 500) / 1000));
                }
                catch (InterruptedException ex) { }
            }
            return getTimeMicros();
        }
    }
}