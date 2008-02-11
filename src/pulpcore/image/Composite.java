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

package pulpcore.image;

/* package-private */ abstract class Composite {
        
  
    /* package-private */ abstract void blend(int[] destData, int destOffset, int srcARGB);
    
    /* package-private */ abstract void blend(int[] destData, int destOffset, int srcARGB, 
        int extraAlpha);

    /* package-private */ abstract void blendRow(int[] destData, int destOffset, int srcARGB, 
        int numPixels);
    
    /* package-private */ void blendRow(int[] destData, int destOffset, int srcARGB, 
        int extraAlpha, int numPixels) 
    {
        blendRow(destData, destOffset, multAlpha(srcARGB, extraAlpha), numPixels);
    }
    
    /**
        Rasterize a horizontal row of pixels.
        @param srcOffset srcX + (u >> 16) + (srcY + (v >> 16)) * srcScanSize, 
        only used if rotation == false
        @param numRows only used if rotation == false, renderBilinear == false
    */
    /* package-private */ abstract void blend(int[] srcData, int srcScanSize, boolean srcOpaque, 
        int srcX, int srcY, int srcWidth, int srcHeight, int srcOffset, 
        int u, int v, int du, int dv, 
        boolean rotation,
        boolean renderBilinear, int renderAlpha,
        int[] destData, int destScanSize, int destOffset, int numPixels, int numRows);
    
    
    protected int multAlpha(int srcARGB, int extraAlpha) {
        int newAlpha = ((srcARGB >>> 24) * extraAlpha) << 16;
        if (CoreGraphics.PREMULTIPLIED_ALPHA) {
            srcARGB = Colors.premultiply(srcARGB, extraAlpha);
        }
        return (newAlpha & 0xff000000) | (srcARGB & 0x00ffffff);
    }
    
    protected int multAlphaOpaque(int srcRGB, int extraAlpha) {
        if (CoreGraphics.PREMULTIPLIED_ALPHA) {
            return Colors.premultiply(srcRGB, extraAlpha);
        }
        else {
            return (extraAlpha << 24) | (srcRGB & 0x00ffffff);
        }
    }
    
    //
    // Bilinear filtering 
    //
    
    protected final int getPixelBilinearOpaque(int[] imageData,
        int offsetTop, int offsetBottom, 
        int fx, int fy,
        int srcWidth) 
    {
        // If fx and fy are integers (using 8-bits of fraction), no interpolation
        // is needed - return the color.
        int ffracX = (fx & 0xff00) >> 8;
        int ffracY = (fy & 0xff00) >> 8;
        
        // Find left and right location
        int x = fx >> 16;
        int left;
        int right;
        if (x >= srcWidth - 1) {
            left = srcWidth - 1;
            right = srcWidth - 1;
        }
        else if (x < 0) {
            left = 0;
            right = 0;
        }
        else if ((fx & 0xffff) == 0) {
            left = x;
            right = x;
        }
        else {
            left = x;
            right = x + 1;
        }
        
        if (ffracX == 0 && ffracY == 0) {
            return imageData[offsetTop + left];
        }
        
        // Get the four colors
        int topLeftPixel = imageData[offsetTop + left];
        int topRightPixel = imageData[offsetTop + right];
        int bottomLeftPixel = imageData[offsetBottom + left];
        int bottomRightPixel = imageData[offsetBottom + right];
        
        // If all pixels are the same color, return the color. 
        // Faster for solid-colored and transparent areas.
        if (topLeftPixel == topRightPixel && 
            topLeftPixel == bottomLeftPixel && 
            topLeftPixel == bottomRightPixel) 
        {
            return topLeftPixel;
        }

        // Blend all 4 pixels. (13 mults per pixel here)
    
        // Calculate the weights of each pixel. 
        // The range of each factor is 0..255. The sum of all four is 255.
        int mult = (ffracX * ffracY + 0xff) >> 8; 
        int topLeftFactor = 0xff - ffracX - ffracY + mult;
        int topRightFactor = ffracX - mult;
        int bottomLeftFactor = ffracY - mult;
        int bottomRightFactor = mult;
        
        // The range of each channel result (before shifting) is 0..65025
        
        int redChannel = (
                topLeftFactor * ((topLeftPixel >> 16) & 0xff) +
                topRightFactor * ((topRightPixel >> 16) & 0xff) +
                bottomLeftFactor * ((bottomLeftPixel >> 16) & 0xff) +
                bottomRightFactor * ((bottomRightPixel >> 16) & 0xff)
            ) << 8;
        int greenChannel = (
                topLeftFactor * ((topLeftPixel >> 8) & 0xff) +
                topRightFactor * ((topRightPixel >> 8) & 0xff) +
                bottomLeftFactor * ((bottomLeftPixel >> 8) & 0xff) +
                bottomRightFactor * ((bottomRightPixel >> 8) & 0xff)
            );
        int blueChannel = (
                topLeftFactor * (topLeftPixel & 0xff) +
                topRightFactor * (topRightPixel & 0xff) +
                bottomLeftFactor * (bottomLeftPixel & 0xff) +
                bottomRightFactor * (bottomRightPixel & 0xff)
            ) >> 8;
            
        return
            0xff000000 | 
            (redChannel & 0xff0000) | 
            (greenChannel & 0xff00) |
            blueChannel;
    }
    
    protected final int getPixelBilinearTranslucent(int[] data, int srcScanSize,
        int srcX, int srcY, int srcWidth, int srcHeight,
        int fx, int fy)
    {
        int y = fy >> 16;
        
        // Find top and bottom offsets
        int top;
        int bottom;
        
        if (y >= 0) {
            if (y < srcHeight - 1) {
                top = srcX + (y + srcY) * srcScanSize;
                bottom = top + srcScanSize;
            }
            else if (y == srcHeight - 1) {
                top = srcX + (y + srcY)  * srcScanSize;
                bottom = -1;
            }
            else {
                top = -1;
                bottom = -1;
            }
        }
        else if (y == -1) {
            top = -1;
            bottom = srcX + srcY * srcScanSize;
        }
        else {
            top = -1;
            bottom = -1;
        }
        
        return getPixelBilinearTranslucent(
            data, 
            top,
            bottom,
            fx, 
            fy,
            srcWidth);
    }

    /**
    
        Uses bilinear interpolation to get the color at a specific location
        within an image.
        <pre>
                          imgWidth
        +----------------------------------------+
        |       :                                |
        |       : srcY                           |        
        |  srcX :       srcWidth                 |
        |.......+-------------------+            |
        |       |    :              |            |
        |       |    : fy           |            |
        |       |    :              |            |
        |       |....*              | srcHeight  |
        |       | fx                |            | imgHeight
        |       |                   |            |
        |       |                   |            |
        |       +-------------------+            |
        |                                        |
        |                                        |
        |                                        |
        |                                        |
        |                                        |
        +----------------------------------------+
        </pre>
        
        @param imageData The ARGB pixel data.
        @param offsetTop srcX + (srcY + floor(fy)) * imgWidth
        @param offsetBottom srcX + (srcY + ceil(fy)) * imgWidth
        @param fx fixed-point horizontal distance from srcX
        @param fy fixed-point vertical distance from srcY
        @param srcWidth the maximum width to retrieve pixel data from the image.
    */
    protected final int getPixelBilinearTranslucent(int[] imageData,
        int offsetTop, int offsetBottom, 
        int fx, int fy,
        int srcWidth) 
    {
        // If fx and fy are integers (using 8-bits of fraction), no interpolation
        // is needed - return the color.
        int ffracX = (fx & 0xff00) >> 8;
        int ffracY = (fy & 0xff00) >> 8;
        if (ffracX == 0 && ffracY == 0) {
            if (offsetTop < 0 || fx < 0) {
                return 0;
            }
            else {
               return imageData[offsetTop + (fx >> 16)];
            }
        }
        
        int topLeftPixel = 0;
        int topRightPixel = 0; 
        int bottomLeftPixel = 0; 
        int bottomRightPixel = 0;
        
        // Find left and right location
        int x = fx >> 16;
        
        if (x >= 0 && x < srcWidth) {
            if (offsetTop != -1) {
                topLeftPixel = imageData[offsetTop + x];
            }
            if (offsetBottom != -1) {
                bottomLeftPixel = imageData[offsetBottom + x];
            }
        }
        
        if (x >= -1 && x < srcWidth-1) {
            if (offsetTop != -1) {
                topRightPixel = imageData[offsetTop + x + 1];
            }
            if (offsetBottom != -1) {
                bottomRightPixel = imageData[offsetBottom + x + 1];
            }
        }
           
        // If all pixels are the same color, return the color. 
        // Faster for solid-colored and transparent areas.
        if (topLeftPixel == topRightPixel && 
            topLeftPixel == bottomLeftPixel && 
            topLeftPixel == bottomRightPixel) 
        {
            return topLeftPixel;
        }

        // Blend all 4 pixels. (17 mults per pixel here)
    
        // Calculate the weights of each pixel. 
        // The range of each factor is 0..255. The sum of all four is 255.
        int mult = (ffracX * ffracY + 0xff) >> 8; 
        int topLeftFactor = 0xff - ffracX - ffracY + mult;
        int topRightFactor = ffracX - mult;
        int bottomLeftFactor = ffracY - mult;
        int bottomRightFactor = mult;
        
        // The range of each channel result (before shifting) is 0..65025
        
        int a1 = (topLeftPixel >>> 24);
        int a2 = (topRightPixel >>> 24);
        int a3 = (bottomLeftPixel >>> 24);
        int a4 = (bottomRightPixel >>> 24); 
    
        int alphaChannel = (
            topLeftFactor * a1 +
            topRightFactor * a2 +
            bottomLeftFactor * a3 +
            bottomRightFactor * a4
        ) << 16;
        
        if (!CoreGraphics.PREMULTIPLIED_ALPHA) {
            // If a pixel has an alpha of zero, don't consider that pixel's color.
            if (a1 == 0 || a2 == 0 || a3 == 0 || a4 == 0) {
                  
                int factor = 0;
                int count = 0;
                if (a1 == 0) {
                    factor += topLeftFactor;
                    topLeftFactor = 0;
                    count++;
                }
                if (a2 == 0) {
                    factor += topRightFactor;
                    topRightFactor = 0;
                    count++;
                }
                if (a3 == 0) {
                    factor += bottomLeftFactor;
                    bottomLeftFactor = 0;
                    count++;
                }
                if (a4 == 0) {
                    factor += bottomRightFactor;
                    bottomRightFactor = 0;
                    count++;
                }
                
                if (count == 4) {
                    return 0;
                }
                
                factor /= (4 - count);
                if (a1 != 0) {
                    topLeftFactor += factor;
                }
                if (a2 != 0) {
                    topRightFactor += factor;
                }
                if (a3 != 0) {
                    bottomLeftFactor += factor;
                }
                if (a4 != 0) {
                    bottomRightFactor += factor;
                }
            }
        }
            
        int redChannel = (
                topLeftFactor * ((topLeftPixel >> 16) & 0xff) +
                topRightFactor * ((topRightPixel >> 16) & 0xff) +
                bottomLeftFactor * ((bottomLeftPixel >> 16) & 0xff) +
                bottomRightFactor * ((bottomRightPixel >> 16) & 0xff)
            ) << 8;
        int greenChannel = (
                topLeftFactor * ((topLeftPixel >> 8) & 0xff) +
                topRightFactor * ((topRightPixel >> 8) & 0xff) +
                bottomLeftFactor * ((bottomLeftPixel >> 8) & 0xff) +
                bottomRightFactor * ((bottomRightPixel >> 8) & 0xff)
            );
        int blueChannel = (
                topLeftFactor * (topLeftPixel & 0xff) +
                topRightFactor * (topRightPixel & 0xff) +
                bottomLeftFactor * (bottomLeftPixel & 0xff) +
                bottomRightFactor * (bottomRightPixel & 0xff)
            ) >> 8;
            
        return
            (alphaChannel & 0xff000000) | 
            (redChannel & 0xff0000) | 
            (greenChannel & 0xff00) |
            blueChannel;
    }
}
