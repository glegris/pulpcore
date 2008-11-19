/*
 * Copyright (c) 2008, Florent Dupont
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of the PulpCore project nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package pulpcore.image.filter;


import pulpcore.image.CoreImage;

/**
 * <p>A fast blur filter can be used to blur pictures quickly. This filter is an
 * implementation of the box blur algorithm. The blurs generated by this
 * algorithm might show square artifacts, especially on pictures containing
 * straight lines (rectangles, text, etc.) On most pictures though, the
 * result will look very good.</p>
 * <p>The force of the blur can be controlled with a radius and the
 * default radius is 3. Since the blur clamps values on the edges of the
 * source picture, you might need to provide a picture with empty borders
 * to avoid artifacts at the edges. The performance of this filter are
 * independant from the radius.</p>
 * Inspired by Romain Guy <romain.guy@mac.com>'s example on Filthy Rich Client
 * and modified for PulpCore.
 *
 * @author Florent Dupont
 */
public class BlurFilter {

	
	public static CoreImage filter(CoreImage img, int radius) {
	    
		int width = img.getWidth();
		int height = img.getHeight();
		//fin param
		
		 CoreImage blurredImage = new CoreImage(width, height, img.isOpaque());
		 
		// colors data
		    int[] srcPixels = img.getData();
		    int[] dstPixels = blurredImage.getData();
		    
		 
		 // horizontal pass
		    blur(srcPixels, dstPixels, width, height, radius);
		    
		    // copy the dstPixels in a temporary buffer
		    int size = height * width;
		    int[] tmpPixels = new int[size];
		    for(int i = 0; i < size; i++)
		    	tmpPixels[i] = dstPixels[i]; 
		    
		    // vertical pass
		    blur(tmpPixels, dstPixels, height, width, radius);
			
		 return blurredImage;
	}

	 /**
     * <p>Blurs the source pixels into the destination pixels. The force of
     * the blur is specified by the radius which must be greater than 0.</p>
     * <p>The source and destination pixels arrays are expected to be in the
     * INT_ARGB_PRE format.</p>
     *
     * @param srcPixels the source pixels
     * @param dstPixels the destination pixels
     * @param width the width of the source picture
     * @param height the height of the source picture
     * @param radius the radius of the blur effect
     */
	private static void blur(int[] srcPixels, int[] dstPixels, int width, int height, int radius) {
		
	    final int windowSize = radius * 2 + 1;
	    final int radiusPlusOne = radius + 1;

	    int sumAlpha;
	    int sumRed;
	    int sumGreen;
	    int sumBlue;

	    int srcIndex = 0;
	    int dstIndex;
	    int pixel;

	    int[] sumLookupTable = new int[256 * windowSize];
	    for (int i = 0; i < sumLookupTable.length; i++) {
	        sumLookupTable[i] = i / windowSize;
	    }

	    int[] indexLookupTable = new int[radiusPlusOne];
	    if (radius < width) {
	        for (int i = 0; i < indexLookupTable.length; i++) {
	            indexLookupTable[i] = i;
	        }
	    } else {
	        for (int i = 0; i < width; i++) {
	            indexLookupTable[i] = i;
	        }
	        for (int i = width; i < indexLookupTable.length; i++) {
	            indexLookupTable[i] = width - 1;
	        }
	    }

	    for (int y = 0; y < height; y++) {
	        sumAlpha = sumRed = sumGreen = sumBlue = 0;
	        dstIndex = y;

	        pixel = unpremultiply(srcPixels[srcIndex]);
	        sumAlpha += radiusPlusOne * ((pixel >> 24) & 0xFF);
	        sumRed   += radiusPlusOne * ((pixel >> 16) & 0xFF);
	        sumGreen += radiusPlusOne * ((pixel >>  8) & 0xFF);
	        sumBlue  += radiusPlusOne * ( pixel        & 0xFF);

	        for (int i = 1; i <= radius; i++) {
	            pixel = unpremultiply(srcPixels[srcIndex + indexLookupTable[i]]);
	            sumAlpha += (pixel >> 24) & 0xFF;
	            sumRed   += (pixel >> 16) & 0xFF;
	            sumGreen += (pixel >>  8) & 0xFF;
	            sumBlue  +=  pixel        & 0xFF;
	        }

	        for  (int x = 0; x < width; x++) {
	            dstPixels[dstIndex] = premultiply(sumLookupTable[sumAlpha] << 24 |
	                                  sumLookupTable[sumRed]   << 16 |
	                                  sumLookupTable[sumGreen] <<  8 |
	                                  sumLookupTable[sumBlue]);
	            dstIndex += height;

	            int nextPixelIndex = x + radiusPlusOne;
	            if (nextPixelIndex >= width) {
	                nextPixelIndex = width - 1;
	            }

	            int previousPixelIndex = x - radius;
	            if (previousPixelIndex < 0) {
	                previousPixelIndex = 0;
	            }

	            int nextPixel = unpremultiply(srcPixels[srcIndex + nextPixelIndex]);
	            int previousPixel = unpremultiply(srcPixels[srcIndex + previousPixelIndex]);

	            sumAlpha += (nextPixel     >> 24) & 0xFF;
	            sumAlpha -= (previousPixel >> 24) & 0xFF;

	            sumRed += (nextPixel     >> 16) & 0xFF;
	            sumRed -= (previousPixel >> 16) & 0xFF;

	            sumGreen += (nextPixel     >> 8) & 0xFF;
	            sumGreen -= (previousPixel >> 8) & 0xFF;

	            sumBlue += nextPixel & 0xFF;
	            sumBlue -= previousPixel & 0xFF;
	        }

	        srcIndex += width;
	    }
	     
	}

	/* package-private */ static int unpremultiply(int pmColor) {
	    int a = pmColor >>> 24;
	    
	    if (a == 0) {
	        return 0;
	    }
	    else if (a == 255) {
	        return pmColor;
	    }
	    else {
	        int r = (pmColor >> 16) & 0xff;
	        int g = (pmColor >> 8) & 0xff;
	        int b = pmColor & 0xff;
	    
	        r = 255 * r / a;
	        g = 255 * g / a;
	        b = 255 * b / a;
	        return (a << 24) | (r << 16) | (g << 8) | b;
	    }
	}


	/* package-private */ static int premultiply(int argbColor) {
	    int a = argbColor >>> 24;
	    int r = (argbColor >> 16) & 0xff;
	    int g = (argbColor >> 8) & 0xff;
	    int b = argbColor & 0xff;

	    r = (a * r + 127) / 255;
	    g = (a * g + 127) / 255;
	    b = (a * b + 127) / 255;
	    
	    return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/* package-private */ static int premultiply(int rgbColor, int alpha) {
	    int r = (rgbColor >> 16) & 0xff;
	    int g = (rgbColor >> 8) & 0xff;
	    int b = rgbColor & 0xff;

	    r = (alpha * r + 127) / 255;
	    g = (alpha * g + 127) / 255;
	    b = (alpha * b + 127) / 255;
	    
	    return (alpha << 24) | (r << 16) | (g << 8) | b;
	}


}
