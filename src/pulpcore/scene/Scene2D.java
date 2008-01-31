/*
    Copyright (c) 2008, Interactive Pulp, LLC
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

package pulpcore.scene;

import java.util.ArrayList;
import java.util.Iterator;
import pulpcore.animation.event.TimelineEvent;
import pulpcore.animation.Timeline;
import pulpcore.Build;
import pulpcore.CoreSystem;
import pulpcore.image.Colors;
import pulpcore.image.CoreGraphics;
import pulpcore.image.CoreImage;
import pulpcore.Input;
import pulpcore.math.CoreMath;
import pulpcore.math.Rect;
import pulpcore.math.Transform;
import pulpcore.sprite.Group;
import pulpcore.sprite.Sprite;
import pulpcore.Stage;

/**
    The Scene2D class is a Scene that provdes commonly used features like
    Sprite management, layer management, Timeline management,
    and dirty rectangle drawing. 
    <p>
    Note the updateScene() method cannot be overridden,
    and subclasses should override the {@link #update(int) } method instead.
    <p>
    Scene2D (like most of PulpCore) is not thread-safe. For mult-threaded apps (for example, 
    network-enabled apps), use the {@link #addEvent(TimelineEvent)}, 
    {@link #addEventAndWait(TimelineEvent)}, {@link #invokeLater(Runnable)}, or
    {@link #invokeAndWait(Runnable)} methods to ensure code runs in the 
    animation thread. For example:
    <pre>
    // Called from the network thread.
    public void receiveNetworkMessage(String message) {
        // Run the code that modifies the scene in the animation thread.
        scene.invokeLater(new Runnable() {
            public void run() {
                scene.addSprite(new Label(message, 0, 0));
            }
        });
    }
    </pre>
*/
public class Scene2D extends Scene {
    
    // NOTE: experiment with these two values for screen sizes other than 550x400
    /** If the non-dirty area inbetween two dirty rects is less than this value, the two
    rects are union'ed. */
    private static final int MAX_NON_DIRTY_AREA = 2048;
    private static final int NUM_DIRTY_RECTANGLES = 64;
    
    private static final int DEFAULT_MAX_ELAPSED_TIME = 100;
    
    // For debugging - same setting for all Scene2D instances
    
    private static boolean showDirtyRectangles;

    // Scene options
    
    private boolean dirtyRectanglesEnabled;
    private boolean paused;
    private int maxElapsedTime;
    private boolean isUnloading;
    
    // Layers
    
    private Group layers;
    
    // Timelines
    
    private ArrayList timelines;
    
    // Dirty Rectangles
    
    private Rect drawBounds = new Rect();
    private boolean needsFullRedraw;
    private RectList dirtyRectangles;
    private RectList subRects;
    
    private Rect newRect = new Rect();
    private Rect workRect = new Rect();
    private Rect unionRect = new Rect();
    private Rect intersectionRect = new Rect();
    
    // Saved state: options automatically restored on showNotify()
    
    private boolean stateSaved;
    private int desiredFPS;
    private boolean isTextInputMode;
    
    /**
        Creates a new Scene2D with one layer and with dirty rectangles enabled.
    */
    public Scene2D() {
        dirtyRectangles = new RectList(NUM_DIRTY_RECTANGLES);
        subRects = new RectList(NUM_DIRTY_RECTANGLES);
        
        reset();
    }
    
    private void reset() {
        maxElapsedTime = DEFAULT_MAX_ELAPSED_TIME;
        desiredFPS = Stage.DEFAULT_FPS;
        isTextInputMode = false;
        dirtyRectanglesEnabled = true;
        needsFullRedraw = true;
        stateSaved = false;
        paused = false;
        isUnloading = false;
        
        layers = new Group();
        timelines = new ArrayList();
        addLayer(new Group()); 
    }
    
    /**
        Sets the paused state of this Scene2D. A paused Scene2D does not update sprites or 
        timelines, but update() is called as usual. By default, a Scene2D is not paused.
    */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }
    
    /**
        Gets the paused state of this Scene2D.
        @return true if this Scene2D is paused.
        @see #setPaused(boolean)
    */
    public boolean isPaused() {
        return paused;
    }
    
    /**
        Sets the default cursor for this Scene. By default, a Scene2D uses the default cursor.
        @see pulpcore.Input
        @see #getCursor()
    */
    public final void setCursor(int cursor) {
        layers.setCursor(cursor);
    }
    
    /**
        Gets the cursor for this Scene. 
        @see pulpcore.Input
        @see #setCursor(int)
    */
    public final int getCursor() {
        int cursor = layers.getCursor();
        if (cursor == -1) {
            return Input.CURSOR_DEFAULT;
        }
        else {
            return cursor;
        }
    }
    
    /**
        Sets the dirty rectangle mode on or off. By default, a Scene2D has dirty rectangles
        enabled, but some apps may have better performance with
        dirty rectangles disabled.
    */
    public void setDirtyRectanglesEnabled(boolean dirtyRectanglesEnabled) {
        if (this.dirtyRectanglesEnabled != dirtyRectanglesEnabled) {
            this.dirtyRectanglesEnabled = dirtyRectanglesEnabled;
            needsFullRedraw = true;
            if (!this.dirtyRectanglesEnabled) {
                clearDirtyRects(layers);
            }
        }
    }
    
    /**
        Checks the dirty rectangles are enabled for this Scene2D.
        @return true if dirty rectangles are enabled.
        @see #setDirtyRectanglesEnabled(boolean)
    */
    public boolean isDirtyRectanglesEnabled() {
        return dirtyRectanglesEnabled;
    }
    
    /**
        Sets the maximum elapsed time used to update this Scene2D.
        If this value is zero, no maximum elapsed time is enforced: 
        the elapsed time always follows system time (while the Scene2D is active.)
        <p>
        If the maximum elapsed time is greater than zero, 
        long pauses between updates
        (caused by other processes or the garbage collector) effectively 
        slow down the animations rather than create a visible 
        skip in time.
        <p>
        By default, the maximum elapsed time is 100. 
    */
    public void setMaxElapsedTime(int maxElapsedTime) {
        this.maxElapsedTime = maxElapsedTime;
    }
    
    
    /**
        Gets the maximum elapsed time used to update this Scene2D.
        @see #setMaxElapsedTime(int)
    */
    public int getMaxElapsedTime() {
        return maxElapsedTime;
    }
    
    
    //
    // Timelines
    //
    
    
    /**
        Adds a Timeline to this Scene2D. The Timeline is automatically updated in the updateScene()
        method. The Timeline is removed when is is finished animating.
        <p>
        This method is safe to call from any thread.
    */
    public void addTimeline(Timeline timeline) {
        synchronized (timelines) {
            if (!timelines.contains(timeline)) {
                timelines.add(timeline);
            }
        }
    }
    
    
    /**
        Removes a timeline from this Scene2D.
        @param gracefully if true and the timeline is not looping, the timeline is 
        fast-forwarded to its end before it is removed.
    */
    public void removeTimeline(Timeline timeline, boolean gracefully) {
        if (timeline == null) {
            return;
        }
        if (gracefully) {
            timeline.fastForward();
        }
        synchronized (timelines) {
            timelines.remove(timeline);
        }
    }
    
    
    /**
        Removes all timelines from this Scene2D.
        @param gracefully if true, all non-looping timelines are 
        fastforwarded to their end before they are removed.
    */
    public void removeAllTimelines(boolean gracefully) {
        synchronized (timelines) {
            if (gracefully) {
                for (int i = 0; i < timelines.size(); i++) {
                    Timeline t = (Timeline)timelines.get(i);
                    t.fastForward();
                }
            }
            timelines.clear();
        }
    }
    
    
    /**
        Gets the number of currently animating timelines. 
    */
    public int getNumTimelines() {
        return timelines.size();
    }

    //
    // Events
    //
    
    /**
        Adds a TimelineEvent to this Scene2D. The TimelineEvent is automatically triggered
        in the updateScene() method. The TimelineEvent is removed after triggering.
        <p>
        This method is safe to call from any thread.
    */
    public void addEvent(TimelineEvent event) {
        Timeline timeline = new Timeline();
        timeline.add(event);
        addTimeline(timeline);
    }
    
    /**
        Adds a TimelineEvent to this Scene2D and returns after the TimelineEvent executes or
        when this Scene2D is unloaded (whichever comes first).
        @throws Error if the current thread is the animation thread.
    */
    public void addEventAndWait(TimelineEvent event) {
        if (Stage.isAnimationThread()) {
            throw new Error("Cannot call addEventAndWait() or invokeAndWait() from the " + 
                "animation thread.");
        }
        
        synchronized (event) {
            addEvent(event);
            while (!event.hasExecuted() && !isUnloading) {
                try {
                    event.wait();
                }
                catch (InterruptedException ex) { }
            }
        }
    }
    
    /**
        Causes a runnable to have it's run() method called in the animation thread. This method
        is equivalent to:
        <pre>
        addEvent(new TimelineEvent(0) {
            public void run() {
                runnable.run();
            }
        });
        </pre>
        <p>
        This method is safe to call from any thread.
    */
    public void invokeLater(final Runnable runnable) {
        addEvent(new TimelineEvent(0) {
            public void run() {
                runnable.run();
            }
        });
    }
    
    /**
        Causes a runnable to have it's run() method called in the animation thread, and returns
        after the Runnable executes or
        when this Scene2D is unloaded (whichever comes first). This method is equivalent to:
        <pre>
        addEventAndWait(new TimelineEvent(0) {
            public void run() {
                runnable.run();
            }
        });
        </pre>
        @throws Error if the current thread is the animation thread.
    */
    public void invokeAndWait(final Runnable runnable) {
        addEventAndWait(new TimelineEvent(0) {
            public void run() {
                runnable.run();
            }
        });
    }
    
    //
    // Layers
    //
    
    /**
        Returns the main (bottom) layer. This layer cannot be removed.
    */
    public Group getMainLayer() {
        return (Group)layers.get(0);
    }
    
    /**
        Adds the specified Group as the top-most layer.
    */
    public void addLayer(Group layer) {
        layers.add(layer);
    }
    
    /**
        Removes the specified layer. If the specified layer is the main layer, this method
        does nothing.
    */
    public void removeLayer(Group layer) {
        if (layer != getMainLayer()) {
            layers.remove(layer);
        }
    }
    
    /**
        Returns the total number of sprites in all layers.
    */
    public int getNumSprites() {
        return layers.getNumSprites();
    }

    /**
        Returns the total number of visible sprites in all layers.
    */
    public int getNumVisibleSprites() {
        return layers.getNumVisibleSprites();
    }
        
    //
    // Sprites
    //
    
    /**
        Adds a sprite to the main (bottom) layer.
    */
    public void add(Sprite sprite) {
        getMainLayer().add(sprite);
    }
    
    /**
        Removes a sprite from the main (bottom) layer.
    */
    public void remove(Sprite sprite) {
        getMainLayer().remove(sprite);
    }
    
    //
    // Dirty Rectangles
    //
    
    private void addDirtyRectangle(Rect r) {
        if (r == null) {
            return;
        }
        
        subRects.clear();
        
        // Increase bounds to correct off-by-one miscalculation in some rare rotated sprites.
        addDirtyRectangle(r.x - 1, r.y - 1, r.width + 2, r.height + 2, MAX_NON_DIRTY_AREA);
        
        int originalSize = subRects.size();
        for (int i = 0; i < subRects.size() && !dirtyRectangles.isOverflowed(); i++) {
            Rect r2 = subRects.get(i);
            if (i < originalSize) {
                addDirtyRectangle(r2.x, r2.y, r2.width, r2.height, MAX_NON_DIRTY_AREA);
            }
            else {
                addDirtyRectangle(r2.x, r2.y, r2.width, r2.height, 0);
            }
            if (subRects.isOverflowed()) {
                // Ah, crap.
                dirtyRectangles.overflow();
            }
        }
    }
    
    private void addDirtyRectangle(int x, int y, int w, int h, int maxNonDirtyArea) {
        if (w <= 0 || h <= 0 || dirtyRectangles.isOverflowed()) {
            return;
        }
        
        newRect.setBounds(x, y, w, h);
        newRect.intersection(drawBounds);
        if (newRect.width <= 0 || newRect.height <= 0) {
            return;
        }
        
        // The goal here is to have no overlapping dirty rectangles because
        // it would lead to problems with alpha blending.
        //
        // Performing a union on two overlapping rectangles would lead to
        // dirty rectangles that cover large portions of the scene that are
        // not dirty.
        // 
        // Instead: shrink, split, or remove existing dirty rectangles, or
        // shrink or remove the new dirty rectangle.
        for (int i = 0; i < dirtyRectangles.size(); i++) {
            
            Rect dirtyRect = dirtyRectangles.get(i);
            
            unionRect.setBounds(dirtyRect);
            unionRect.union(newRect);
            if (unionRect.equals(dirtyRect)) {
                return;
            }
            intersectionRect.setBounds(dirtyRect);
            intersectionRect.intersection(newRect);
            
            int newArea = unionRect.getArea() + intersectionRect.getArea() - 
                dirtyRect.getArea() - newRect.getArea();
            if (newArea < maxNonDirtyArea) {
                newRect.setBounds(unionRect);
                dirtyRectangles.remove(i);
                if (newArea > 0) {
                    // Start over - make sure there's no overlap
                    i = -1;
                }
                else {
                    i--;
                }
            }
            else if (dirtyRect.intersects(newRect)) {
                int code = dirtyRect.getIntersectionCode(newRect);
                int numSegments = CoreMath.countBits(code);
                if (numSegments == 0) {
                    // Remove the existing dirty rect in favor of the new one
                    dirtyRectangles.remove(i);
                    i--;
                }
                else if (numSegments == 1) {
                    // Shrink the existing dirty rect
                    dirtyRect.setOutsideBoundary(Rect.getOppositeSide(code), 
                        newRect.getBoundary(code));
                    subRects.add(dirtyRect);
                    
                    dirtyRectangles.remove(i);
                    i--;
                }
                else if (numSegments == 2) {
                    // Split the existing dirty rect into two
                    int side1 = 1 << CoreMath.log2(code);
                    int side2 = code - side1;
                    workRect.setBounds(dirtyRect);
                    
                    // First split
                    dirtyRect.setOutsideBoundary(Rect.getOppositeSide(side1), 
                        newRect.getBoundary(side1));
                    subRects.add(dirtyRect);
                    
                    // Second split
                    workRect.setOutsideBoundary(side1, 
                        dirtyRect.getBoundary(Rect.getOppositeSide(side1)));
                    workRect.setOutsideBoundary(Rect.getOppositeSide(side2), 
                        newRect.getBoundary(side2));
                    subRects.add(workRect);
                    
                    dirtyRectangles.remove(i);
                    i--;
                }
                else if (numSegments == 3) {
                    // Shrink the new dirty rect
                    int side = code ^ 0xf;
                    newRect.setOutsideBoundary(Rect.getOppositeSide(side), 
                        dirtyRect.getBoundary(side));
                    if (newRect.width <= 0 || newRect.height <= 0) {
                        return;
                    }
                }
                else if (numSegments == 4) {
                    // Exit - don't add this new rect
                    return;
                }
            }
        }
        
        dirtyRectangles.add(newRect);
    }
    
    //
    // Scene implementation
    //
    
    /**
        Forces all invokeAndWait() and addEventAndWait() calls to return, and 
        removes all layers, sprites, and timelines. 
    */
    public void unload() {
        isUnloading = true;
        
        synchronized (timelines) {
            for (int i = 0; i < timelines.size(); i++) {
                Timeline timeline = (Timeline)timelines.get(i);
                timeline.notifyChildren();
            }
        }
        
        reset();
    }
    
    /**
        Notifies that this scene has been shown after another Scene is hidden
        or immediately after a call to start(). 
        <p>
        Subclasses that override this method should call {@code super.showNotify();}.
    */
    public void showNotify() {
        if (stateSaved) {
            Stage.setFrameRate(desiredFPS);
            Input.setTextInputMode(isTextInputMode);
            stateSaved = false;
        }
        redrawNotify();
    }
    
    /**
        Notifies that this scene has been hidden by another Scene or 
        immediately before a call to stop(). 
        <p>
        Subclasses that override this method should call {@code super.hideNotify();}.
    */
    public void hideNotify() {
        desiredFPS = Stage.getFrameRate();
        isTextInputMode = Input.isTextInputMode();
        stateSaved = true;
    }
    
    public final void redrawNotify() {
        Transform defaultTransform = Stage.getDefaultTransform();
                
        drawBounds.setBounds(
            CoreMath.toInt(defaultTransform.getTranslateX()),
            CoreMath.toInt(defaultTransform.getTranslateY()),
            CoreMath.toInt(Stage.getWidth() * defaultTransform.getScaleX()),
            CoreMath.toInt(Stage.getHeight() * defaultTransform.getScaleY()));
        needsFullRedraw = true;
    }
    
    public final void updateScene(int elapsedTime) {
        
        if (maxElapsedTime > 0 && elapsedTime > maxElapsedTime) {
            elapsedTime = maxElapsedTime;
        }
        
        if (Build.DEBUG && Input.isControlDown() && 
            Input.isPressed(Input.KEY_D))
        {
            showDirtyRectangles = !showDirtyRectangles;
            needsFullRedraw = true;
        }
        
        // Update timelines, layers, and sprites
        if (!paused) {
            layers.update(elapsedTime);
            
            synchronized (timelines) {
                for (int i = 0; i < timelines.size(); i++) {
                    Timeline timeline = (Timeline)timelines.get(i);
                    timeline.update(elapsedTime);
                    if (timeline.isFinished()) {
                        timelines.remove(i);
                        i--;
                    }
                }
            }
        }
        // Allow subclasses to check input, change scenes, etc.
        update(elapsedTime);
        
        // Set cursor
        int cursor = Input.CURSOR_DEFAULT;
        if (Input.isMouseInside()) {
            Sprite pick = layers.pickEnabledAndVisible(Input.getMouseX(), Input.getMouseY());
            if (pick != null) {
                cursor = pick.getCursor();
            }
        }
        Input.setCursor(cursor);
        
        if (!dirtyRectanglesEnabled || needsFullRedraw) {
            dirtyRectangles.overflow();
        }
        else {
            dirtyRectangles.clear();
        }
        
        if (needsFullRedraw) {
            layers.setDirty(true);
        }
        
        if (dirtyRectanglesEnabled) {
            // Add dirty rectangles
            addDirtyRectangles(layers, needsFullRedraw);
            layers.setDirty(false);
            
            // Add dirty rectangle for the custom cursor
            // TODO: custom cursor may be broken for non-identity parent transforms
            /*
            CoreImage cursor = Input.getCustomCursor();
            if (cursor != null) {
                addDirtyRectangle(
                    Input.getMouseX() - cursor.getHotspotX(),
                    Input.getMouseY() - cursor.getHotspotY(),
                    cursor.getWidth(), cursor.getHeight());
            }
            */
                
            // Add dirty rectangle for debug overlay
            if (Build.DEBUG) {
                Sprite overlay = Stage.getInfoOverlay();
                if (overlay != null) {
                    //overlay.prepareToDraw(transform, needsFullRedraw);
                    if (needsFullRedraw || overlay.isDirty()) {
                        if (dirtyRectangles.isOverflowed()) {
                            overlay.updateDirtyRect();
                        }
                        else {
                            addDirtyRectangle(overlay.getDirtyRect());
                            boolean boundsChanged = overlay.updateDirtyRect();
                            if (boundsChanged) {
                                addDirtyRectangle(overlay.getDirtyRect());
                            }
                        }
                    }
                    overlay.setDirty(false);
                }
            }
        }
    }
    
    private void setDirty(Group group, boolean dirty) {
        group.setDirty(dirty);
        for (int i = 0; i < group.size(); i++) {
            Sprite sprite = group.get(i);
            if (sprite instanceof Group) {
                setDirty((Group)sprite, dirty);
            }
            else {
                sprite.setDirty(dirty);
            }
        }
    }
    
    private void clearDirtyRects(Group group) {
        group.clearDirtyRect();
        for (int i = 0; i < group.size(); i++) {
            Sprite sprite = group.get(i);
            if (sprite instanceof Group) {
                clearDirtyRects((Group)sprite);
            }
            else {
                sprite.clearDirtyRect();
            }
        }
    }
    
    /**
        Recursive function to loop through all the child sprites of the 
        specified group.
    */
    private void addDirtyRectangles(Group group, boolean parentDirty) {
        
        parentDirty |= group.isDirty();
        
        Iterator e = group.getRemovedSprites();
        while (e != null && e.hasNext()) {
            notifyRemovedSprite((Sprite)e.next());
        }
        
        for (int i = 0; i < group.size(); i++) {
            Sprite sprite = group.get(i);
            if (sprite instanceof Group) {
                addDirtyRectangles((Group)sprite, parentDirty);
            }
            else if (parentDirty || sprite.isDirty()) {
                if (dirtyRectangles.isOverflowed()) {
                    sprite.updateDirtyRect();
                }
                else {
                    addDirtyRectangle(sprite.getDirtyRect());
                    boolean boundsChanged = sprite.updateDirtyRect();
                    if (boundsChanged) {
                        addDirtyRectangle(sprite.getDirtyRect());
                    }
                }
            }
            
            sprite.setDirty(false);
        }
    }
        
    private final void notifyRemovedSprite(Sprite sprite) {
        if (dirtyRectangles.isOverflowed()) {
            return;
        }
            
        if (sprite instanceof Group) {
            Group group = (Group)sprite;
            for (int i = 0; i < group.size(); i++) {
                notifyRemovedSprite(group.get(i));
            }
        }
        else if (sprite != null) {
            addDirtyRectangle(sprite.getDirtyRect());
        }
    }
    
    /**
        Allows subclasses to check for input, change scenes, etc. By default, this method does
        nothing.
    */
    public void update(int elapsedTime) {
        // Do nothing
    }
    
    /**
        Draws all of the sprites in this scene. Most apps will not override this method.
    */
    public void drawScene(CoreGraphics g) {
        
        if (!dirtyRectanglesEnabled || needsFullRedraw || dirtyRectangles.isOverflowed()) {
            layers.draw(g);
            needsFullRedraw = false;
        }
        else if (Build.DEBUG && showDirtyRectangles) {
            layers.draw(g);
            
            for (int i = 0; i < dirtyRectangles.size(); i++) {
                Rect r = dirtyRectangles.get(i);
                g.setColor(Colors.GREEN);
                g.drawRect(r.x, r.y, r.width, r.height);
                g.setColor(Colors.rgba(Colors.GREEN, 128));
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        }
        else {
            // This might be a place to optimize. Currently every sprite is drawn for every
            // rectangle, and the clipping bounds makes sure we don't overdraw. 
            boolean drawOverlay = (Build.DEBUG && Stage.getInfoOverlay() != null);
            
            for (int i = 0; i < dirtyRectangles.size(); i++) {
                Rect r = dirtyRectangles.get(i);
                g.setClip(r.x, r.y, r.width, r.height);
                layers.draw(g);
                if (Build.DEBUG && drawOverlay) {
                    Stage.getInfoOverlay().draw(g);
                }
            }
            Stage.setDirtyRectangles(dirtyRectangles.rects, dirtyRectangles.size);
        }
        
        dirtyRectangles.clear();
    }
    
    static class RectList {
        
        private Rect[] rects;
        private int size;
        
        public RectList(int capacity) {
            rects = new Rect[capacity];
            for (int i = 0; i < capacity; i++) {
                rects[i] = new Rect();
            }
            size = 0;
        }
        
        public int size() {
            return size;
        }
        
        public void clear() {
            size = 0;
        }
        
        public boolean isOverflowed() {
            return (size < 0);
        }
        
        public void overflow() {
            size = -1;
        }
        
        public Rect get(int i) {
            return rects[i];
        }
        
        public void remove(int i) {
            if (size > 0) {
                if (i < size - 1) {
                    rects[i].setBounds(rects[size - 1]);
                }
                size--;
            }
        }
        
        public boolean add(Rect r) {
            if (size < 0 || size == rects.length) {
                size = -1;
                return false;
            }
            else {
                rects[size++].setBounds(r);
                return true;
            }
        }
    }
}