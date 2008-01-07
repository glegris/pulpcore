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

package pulpcore.sprite;

import pulpcore.animation.Bool;
import pulpcore.animation.Fixed;
import pulpcore.animation.Property;
import pulpcore.image.CoreGraphics;
import pulpcore.image.CoreImage;
import pulpcore.Input;
import pulpcore.math.CoreMath;

/**
    A Slider is a widget that lets the user select a value by sliding a thumb. 
*/
public class Slider extends Sprite {
    
    private static final int DRAG_GUTTER_FIRST_DELAY = 500;
    private static final int DRAG_GUTTER_DELAY = 100;
    
    /** Horizontal orientation. */
    public static final int HORIZONTAL = 0;
    
    /** Vertical orientation. */
    public static final int VERTICAL = 1;
    
    private static final int DRAG_NONE = 0;
    private static final int DRAG_THUMB = 1;
    private static final int DRAG_GUTTER = 2;
    
    /**
        The flag indicating whether this Slider is enabled. An enabled Slider 
        responds to user input. Sliders are enabled by default.
    */
    public final Bool enabled = new Bool(this, true);
    
    /** 
        The value of this Slider, initially set to 50. The value is a Fixed for animation purposes,
        but the Slider's internal methods ensure the end result (after animation) is always an
        integer.
    */
    public final Fixed value = new Fixed(this, 50);
    
    private CoreImage backgroundImage;
    private CoreImage thumbImage;
    private int orientation;
    private int top, left, bottom, right;
    private int min, max, extent;
    
    private int pageDuration = 0;
    private int unitDuration = 0;
    
    private int thumbX, thumbY;
    private int dragMode;
    private int dragOffset;
    private int dragGutterDelay;
    private int dragGutterDir;
    
    /**
        Creates a Slider with a background image and a thumb image.
    */
    public Slider(String backgroundImage, String thumbImage, int x, int y) {
        this(CoreImage.load(backgroundImage), CoreImage.load(thumbImage), x, y);
    }
    
    /**
        Creates a Slider with a background image and a thumb image.
    */
    public Slider(CoreImage backgroundImage, CoreImage thumbImage, int x, int y) {
        super(x, y, backgroundImage.getWidth(), backgroundImage.getHeight());
        this.backgroundImage = backgroundImage;
        this.thumbImage = thumbImage;
        this.min = 0;
        this.max = 100;
        this.extent = 0;
    }
    
    public void propertyChange(Property property) {
        super.propertyChange(property);
        if (property == value) {
            // Clamp the value.
            // Keep things in fixed-point to avoid floating point error
            int fValue = value.getAsFixed();
            int fClampedValue = CoreMath.clamp(fValue, 
                CoreMath.toFixed(min), CoreMath.toFixed(max - extent));
            if (fClampedValue != value.getAsFixed()) {
                // Calls propertyChange() again
                value.setAsFixed(fClampedValue);
            }
            else {
                positionThumb();
            }
        }
    }
    
    public void update(int elapsedTime) {
        super.update(elapsedTime);
        value.update(elapsedTime);
        enabled.update(elapsedTime);
        
        boolean imageChange = false;
        imageChange |= backgroundImage.update(elapsedTime);
        imageChange |= thumbImage.update(elapsedTime);
        if (imageChange) {
            setDirty(true);
        }
        
        boolean takeInput = enabled.get() && visible.get() && alpha.get() > 0;
        
        if (!takeInput) {
            dragMode = DRAG_NONE;
        }
        else if (dragMode == DRAG_THUMB) {
            if (Input.isMouseReleased()) {
                dragMode = DRAG_NONE;
            }
            else {
                int offset;
                if (isHorizontal()) {
                    offset = getLocalX(Input.getMouseX(), Input.getMouseY());
                }
                else {
                    offset = getLocalY(Input.getMouseX(), Input.getMouseY());
                }
                value.set(getValue(offset - dragOffset));
            }
        }
        else if (dragMode == DRAG_GUTTER) {
            if (Input.isMouseReleased()) {
                dragMode = DRAG_NONE;
            }
            else {
                if (Input.isMouseMoving()) {
                    dragGutterDir = 0;
                }
                dragGutterDelay -= elapsedTime;
                if (dragGutterDelay <= 0) {
                    dragGutterDelay = DRAG_GUTTER_DELAY;
                    int x = getLocalX(Input.getMouseX(), Input.getMouseY());
                    int y = getLocalY(Input.getMouseX(), Input.getMouseY());
                    if (isHorizontal()) {
                        if (x < thumbX && dragGutterDir <= 0) {
                            // Scroll left
                            page(-extent, x - thumbImage.getWidth()/2);
                        }
                        else if (x >= thumbX + thumbImage.getWidth() && dragGutterDir >= 0) {
                            // Scroll right
                            page(extent, x - thumbImage.getWidth()/2);
                        }
                    }
                    else {
                        // Vertical
                        if (y < thumbY && dragGutterDir <= 0) {
                            // Scroll left
                            page(-extent, y - thumbImage.getHeight()/2);
                        }
                        else if (y >= thumbY + thumbImage.getHeight() && dragGutterDir >= 0) {
                            // Scroll right
                            page(extent, y - thumbImage.getHeight()/2);
                        }
                    }
                }
            }
        }
        else if (isMousePressed()) {
            int x = getLocalX(Input.getMousePressX(), Input.getMousePressY());
            int y = getLocalY(Input.getMousePressX(), Input.getMousePressY());
            if (isHorizontal()) {
                dragOffset = x - thumbX;
                if (x < thumbX) {
                    // Scroll left
                    page(-extent, x - thumbImage.getWidth()/2);
                    setDragGutterMode(-1);
                }
                else if (x >= thumbX + thumbImage.getWidth()) {
                    // Scroll right
                    page(extent, x - thumbImage.getWidth()/2);
                    setDragGutterMode(1);
                }
                else if (y >= thumbY && y < thumbY + thumbImage.getHeight()) {
                    // Start dragging
                    dragOffset = x - thumbX;
                    dragMode = DRAG_THUMB;
                }
            }
            else {
                // Vertical
                if (y < thumbY) {
                    // Scroll left
                    page(-extent, y - thumbImage.getHeight()/2);
                    setDragGutterMode(-1);
                }
                else if (y >= thumbY + thumbImage.getHeight()) {
                    // Scroll right
                    page(extent, y - thumbImage.getHeight()/2);
                    setDragGutterMode(1);
                }
                else if (x >= thumbX && x < thumbX + thumbImage.getWidth()) {
                    // Start dragging
                    dragOffset = y - thumbY;
                    dragMode = DRAG_THUMB;
                }
            }
        }
    }
    
    private void setDragGutterMode(int dir) {
        if (extent == 0) {
            dragMode = DRAG_THUMB;
            if (isHorizontal()) {
                dragOffset = thumbImage.getWidth()/2;
            }
            else {
                dragOffset = thumbImage.getHeight()/2;
            }
        }
        else {
            dragMode = DRAG_GUTTER;
            dragGutterDir = dir;
            dragGutterDelay = DRAG_GUTTER_FIRST_DELAY;
        }
    }
    
    private int getValue(int offset) {
        int start;
        int space;
        if (isHorizontal()) {
            start = (left > 0 ? left : 0);
            space = getHorizontalSpace();
        }
        else {
            start = (top > 0 ? top : 0);
            space = getVerticalSpace();
        }
        
        if (space == 0) {
            return 0;
        }
        
        int value = min + CoreMath.intDivCeil((offset - start) * (max - extent - min), space);
        return CoreMath.clamp(value, min, max - extent);
    }
    
    private void page(int scroll, int offset) {
        if (scroll != 0) {
            scroll(scroll, pageDuration);
        }
        else {
            value.set(getValue(offset));
        }
    }
    
    private void positionThumb() {
        setDirty(true);
        int horizontalSpace = getHorizontalSpace();
        int verticalSpace =  getVerticalSpace();
        int range = max - extent - min;
        double thumbValue = CoreMath.clamp(value.get(), min, max - extent);
        
        if (isHorizontal()) {
            thumbX = (left > 0 ? left : 0);
            if (range > 0) {
                thumbX += (int)Math.round((thumbValue - min) * horizontalSpace / range);
            }
            thumbY = (top > 0 ? top : 0) + verticalSpace / 2;
        }
        else {
            thumbX = (left > 0 ? left : 0) + horizontalSpace / 2;
            thumbY = (top > 0 ? top : 0);
            if (range > 0) {
                thumbY += (int)Math.round((thumbValue - min) * verticalSpace / range);
            }
        }
    }
    
    private int getHorizontalSpace() {
        return backgroundImage.getWidth() - left - right - thumbImage.getWidth();
    }
    
    private int getVerticalSpace() {
        return backgroundImage.getHeight() - top - bottom - thumbImage.getHeight();
    }
    
    protected int getNaturalWidth() {
        int w = backgroundImage.getWidth();
        if (left < 0) {
            w -= left;
        }
        if (right < 0) {
            w -= right;
        }
        return CoreMath.toFixed(w);
    }
    
    protected int getNaturalHeight() {
        int h = backgroundImage.getHeight();
        if (top < 0) {
            h -= top;
        }
        if (bottom < 0) {
            h -= bottom;
        }
        return CoreMath.toFixed(h);
    }
    
    /**
        Sets the thumb image.
    */
    public void setThumb(CoreImage thumbImage) {
        if (this.thumbImage != thumbImage) {
            this.thumbImage = thumbImage;
            positionThumb();
        }
    }
    
    /**
        Sets the visual insets that the thumb image is bound to. 
        <p>
        If an inset is positive, it is used as inner boundry within the background image. If 
        an inset is negative, the the thumb can extend outisde the background image by that amount.
        <p>
        For horizontal sliders, the left and right insets are use as boundaries, 
        and the thumb is centered vertically between the top and bottom insets.
        <p>
        For vertical sliders, the top and bottom insets are use as boundaries, 
        and the thumb is centered horizontally between the left and right insets.
    */
    public void setInsets(int top, int left, int bottom, int right) {
        if (this.top != top || this.left != left || this.bottom != bottom || this.right != right) {
            int oldNaturalWidth = getNaturalWidth();
            int oldNaturalHeight = getNaturalHeight();
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            if (width.getAsFixed() == oldNaturalWidth) {
                width.setAsFixed(getNaturalWidth());
            }
            if (height.getAsFixed() == oldNaturalHeight) {
                height.setAsFixed(getNaturalHeight());
            }
            positionThumb();
        }
    }
    
    protected void drawSprite(CoreGraphics g) {
        int bgX = (left < 0) ? -left : 0;
        int bgY = (top < 0) ? -top : 0;
        
        g.drawImage(backgroundImage, bgX, bgY);
        g.drawImage(thumbImage, thumbX, thumbY);
    }
    
    // 
    // Model
    //
    
    private boolean isHorizontal() {
        // Default is horizontal
        return (orientation != VERTICAL);
    }
    
    /**
        Sets the orientation of this Slider: either {@link #HORIZONTAL} or {@link #VERTICAL}.
        By default, the Slider is horizontal.
    */
    public void setOrientation(int orientation) {
        if (this.orientation != orientation) {
            this.orientation = orientation;
            positionThumb();
        }
    }
    
    /**
        Sets the internal data model for this Slider. The range of the value is set have a bounds
        of the specified minimum and maximum values. The extent is set to zero.
        <p>
        The minimum and maximum can be any integer, and do not 
        correspond to any pixel value. By default, the minimum is 0, the maximum is 100, and the
        extent is 0.
        @see #value
        @see #setRange(int, int, int)
    */
    public void setRange(int min, int max) {
        setRange(min, max, 0);
    }
    
    /**
        Sets the internal data model for this Slider. The range of the value is set have a bounds
        of the specified minimum and maximum values. The extent is the inner range that this Slider
        covers, such that:
        <code>
        minimum <= value <= value+extent <= maximum
        </code>
        <p>
        The minimum, maximum, and extent can be any integer, and do not 
        correspond to any pixel value. By default, the minimum is 0, the maximum is 100, and the
        extent is 0.
        @see #value
        @see #setRange(int, int)
    */
    public void setRange(int min, int max, int extent) {
        if (this.min != min || this.max != max || this.extent != extent) {
            this.min = min;
            this.max = max;
            this.extent = extent;
            value.stopAnimation(true);
            value.set(CoreMath.clamp(value.getAsInt(), min, max - extent));
            positionThumb();
        }
    }
    
    /**
        Gets the minimum value of the internal data model of this Slider.
        The default minimum value is 0.
        @see #setRange(int, int)
        @see #setRange(int, int, int)
    */
    public int getMin() {
        return min;
    }
    
    /**
        Gets the maximum value of the internal data model of this Slider.
        The default maximum value is 100.
        @see #setRange(int, int)
        @see #setRange(int, int, int)
    */
    public int getMax() {
        return max;
    }
    
    /**
        Gets the extent (inner range) of the internal data model of this Slider.
        The default extent is 0.
        @see #setRange(int, int)
        @see #setRange(int, int, int)
    */
    public int getExtent() {
        return extent;
    }
    
    /**
        Sets the duration, in milliseconds, to animate when the value is changed when the
        gutter (the background of the Slider outside the thumb) is clicked or when the 
        {@link #scrollUp()}, {@link #scrollDown()}, 
        {@link #scrollPageUp()}, or {@link #scrollPageDown()} methods are called. 
        <p>
        The page duration is only used if the extent is non-zero. By default, both animation
        durations are set to zero.
    */
    public void setAnimationDuration(int unitDuration, int pageDuration) {
        this.unitDuration = unitDuration;
        this.pageDuration = pageDuration;
    }
    
    /**
        Sets the {@link #value} to the minimum. No animation is performed.
    */
    public void scrollHome() {
        value.set(min);
    }
    
    /**
        Sets the {@link #value} to (maximum - extent). No animation is performed.
    */
    public void scrollEnd() {
        value.set(max - extent);
    }
    
    /**
        Changes the {@link #value} by the specified number of units, 
        animating the change if the unit animation duration is defined.
        @see #setAnimationDuration(int, int)
    */
    public void scroll(int units) {
        scroll(units, unitDuration*Math.abs(units));
    }
    
    /**
        Decreases the {@link #value} by 1, animating the change if the unit animation duration
        is defined.
        @see #setAnimationDuration(int, int)
    */
    public void scrollUp() {
        scroll(-1, unitDuration);
    }
    
    /**
        Increases the {@link #value} by 1, animating the change if the unit animation duration
        is defined.
        @see #setAnimationDuration(int, int)
    */
    public void scrollDown() {
        scroll(1, unitDuration);
    }
    
    /**
        Decreases the {@link #value} by the extent, animating the change if the page animation 
        duration is defined. If the extent is zero, this method does nothing.
        @see #setAnimationDuration(int, int)
    */
    public void scrollPageUp() {
        if (extent != 0) {
            scroll(-extent, pageDuration);
        }
    }
    
    /**
        Decreases the {@link #value} by the extent, animating the change if the page animation 
        duration is defined. If the extent is zero, this method does nothing.
        @see #setAnimationDuration(int, int)
    */
    public void scrollPageDown() {
        if (extent != 0) {
            scroll(extent, pageDuration);
        }
    }
    
    private void scroll(int units, int dur) {
        double oldValue = value.get();
        value.stopAnimation(true);
        int newValue = CoreMath.clamp(value.getAsInt() + units, min, max-extent);
        if (dur == 0) {
            value.set(newValue);
        }
        else {
            double actualUnits = Math.abs(oldValue - newValue);
            if (actualUnits < Math.abs(units)) {
                dur = (int)Math.round(actualUnits * dur / Math.abs(units));
            }
            value.animate(oldValue, newValue, dur);
        }
    }
}