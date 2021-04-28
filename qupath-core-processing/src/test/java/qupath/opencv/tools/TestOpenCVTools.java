/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.opencv.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.tools.IJTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;

@SuppressWarnings("javadoc")
public class TestOpenCVTools {
	
	private final static Logger logger = LoggerFactory.getLogger(TestOpenCVTools.class);

	/**
	 * Test creation of BufferedImages of different types, and conversions between BufferedImage, Mat and ImagePlus.
	 */
	@Test
	public void testImageConversions() {
		
		int width = 50;
		int height = 20;
		int nChannels = 4;
		int[] colors = IntStream.range(0, nChannels).map(i -> ImageChannel.getDefaultChannelColor(i)).toArray();
		var skipTypes = Arrays.asList(PixelType.INT8, PixelType.UINT32);
		var rand = new Random(100L);
		
		try (PointerScope scope = new PointerScope()) {
			
			for (var type : PixelType.values()) {
				if (skipTypes.contains(type))
					continue;
				
				double max = type.isFloatingPoint() ? 2 : Math.pow(2, type.getBitsPerPixel());
				double offset = type.isUnsignedInteger() ? 0 : -max / 2.0;
				
				var colorModel = ColorModelFactory.createColorModel(type, nChannels, false, colors);
				var raster = colorModel.createCompatibleWritableRaster(width, height);
				var buf = raster.getDataBuffer();
				int n = width * height;
				for (int b = 0; b < nChannels; b++) {
				    for (int i = 0; i < n; i++)
				        buf.setElemDouble(b, i, rand.nextDouble() * max + offset);
				}
				var img = new BufferedImage(colorModel, raster, false, null);
				
				// Convert to ImagePlus
				var imp = IJTools.convertToUncalibratedImagePlus(type.toString(), img);
				// Convert to Mat
				var mat = OpenCVTools.imageToMat(img);
				// Convert Mat to ImagePlus
				var imp2 = OpenCVTools.matToImagePlus(type.toString() + " from Mat", mat);
				
				// Check values
				float[] expected = null;
				for (int b = 0; b < nChannels; b++) {
					expected = raster.getSamples(0, 0, width, height, b, expected);
					float[] actual = (float[])imp.getStack().getProcessor(b+1).convertToFloatProcessor().getPixels();
					assertArrayEquals(expected, actual, 0.0001f);
					actual = (float[])imp2.getStack().getProcessor(b+1).convertToFloatProcessor().getPixels();
					assertArrayEquals(expected, actual, 0.0001f);
				}
				
			}
			
		}
	}
	
	
	@Test
	public void testScalar() {
		double[] values = new double[] {-100, 123.4, 0, -0, 12023.423};
		// Test floats
		for (int type : new int[] {opencv_core.CV_32F, opencv_core.CV_32FC3, opencv_core.CV_64F}) {
			for (double value : values) {
				Mat mat = OpenCVTools.scalarMat(value, type);
				assertEquals(1, mat.rows());
				assertEquals(1, mat.cols());
				assertEquals(1, mat.channels());
				assertEquals(value, mat.createIndexer().getDouble(0L), 1e-3);
			}
		}
		// Test unsigned integers
		for (int type : new int[] {opencv_core.CV_8U}) {
			for (double value : values) {
				Mat mat = OpenCVTools.scalarMat(value, type);
				assertEquals(1, mat.rows());
				assertEquals(1, mat.cols());
				assertEquals(1, mat.channels());
				double val = Math.min(255, Math.max(0, Math.round(value)));
				assertEquals(val, mat.createIndexer().getDouble(0L));
			}
		}
		// Test signed integers
		for (int type : new int[] {opencv_core.CV_32S}) {
			for (double value : values) {
				Mat mat = OpenCVTools.scalarMat(value, type);
				assertEquals(1, mat.rows());
				assertEquals(1, mat.cols());
				assertEquals(1, mat.channels());
				double val = Math.round(value);
				assertEquals(val, mat.createIndexer().getDouble(0L));
			}
		}
	}
	
	
	@Test
	public void testStats() {
		double[] values = new double[] {-7, 3, 0, -0, -20, 100, 45.3, 19.2};
//		var stats = DoubleStream.of(values).summaryStatistics();
		var stats = new DescriptiveStatistics(values);
		var mat = new Mat(values);
		var stdDev = Math.sqrt(stats.getPopulationVariance());
		
		assertEquals(1, mat.rows());
		assertEquals(values.length, mat.cols());
		
		assertEquals(stats.getMean(), OpenCVTools.mean(mat));
		assertEquals(stdDev, OpenCVTools.stdDev(mat));
		assertEquals(stats.getSum(), OpenCVTools.sum(mat));
		assertEquals(stats.getMin(), OpenCVTools.minimum(mat));
		assertEquals(stats.getMax(), OpenCVTools.maximum(mat));

		assertEquals(stats.getMean(), OpenCVTools.channelMean(mat)[0]);
		assertEquals(stdDev, OpenCVTools.channelStdDev(mat)[0]);
		assertEquals(stats.getSum(), OpenCVTools.channelSum(mat)[0]);
		assertEquals(stats.getMin(), OpenCVTools.channelMinimum(mat)[0]);
		assertEquals(stats.getMax(), OpenCVTools.channelMaximum(mat)[0]);

		// Transpose
		mat = mat.t().asMat();
		assertEquals(1, mat.cols());
		assertEquals(values.length, mat.rows());
		assertEquals(stats.getMean(), OpenCVTools.mean(mat));
		assertEquals(stdDev, OpenCVTools.stdDev(mat));
		assertEquals(stats.getSum(), OpenCVTools.sum(mat));
		assertEquals(stats.getMin(), OpenCVTools.minimum(mat));
		assertEquals(stats.getMax(), OpenCVTools.maximum(mat));

		assertEquals(stats.getMean(), OpenCVTools.channelMean(mat)[0]);
		assertEquals(stdDev, OpenCVTools.channelStdDev(mat)[0]);
		assertEquals(stats.getSum(), OpenCVTools.channelSum(mat)[0]);
		assertEquals(stats.getMin(), OpenCVTools.channelMinimum(mat)[0]);
		assertEquals(stats.getMax(), OpenCVTools.channelMaximum(mat)[0]);
		
		// Convert to channels
		mat = mat.reshape(values.length, 1);
		assertEquals(1, mat.cols());
		assertEquals(1, mat.rows());
		assertEquals(values.length, mat.channels());
		
		assertEquals(stats.getMean(), OpenCVTools.mean(mat));
		assertEquals(stdDev, OpenCVTools.stdDev(mat));
		assertEquals(stats.getSum(), OpenCVTools.sum(mat));
		assertEquals(stats.getMin(), OpenCVTools.minimum(mat));
		assertEquals(stats.getMax(), OpenCVTools.maximum(mat));

		double eps = 1e-6;
		assertArrayEquals(values, OpenCVTools.channelMean(mat), eps);
		assertArrayEquals(values, OpenCVTools.channelSum(mat), eps);
		assertArrayEquals(values, OpenCVTools.channelMinimum(mat), eps);
		assertArrayEquals(values, OpenCVTools.channelMaximum(mat), eps);
		
		// Standard deviations should be zero
		assertArrayEquals(new double[mat.channels()], OpenCVTools.channelStdDev(mat), eps);
	}
	
	
	@Test
	public void testNoise() {
		opencv_core.setRNGSeed(100);
		double[] means = new double[] {-100, -0.5, 0, 10, 34, 200, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] stdDevs = new double[] {1, 4, 100, 250, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
		for (int type : new int[] {opencv_core.CV_32F, opencv_core.CV_32FC(5), opencv_core.CV_64F}) {
			for (double mean : means) {
				for (double stdDev : stdDevs) {
					// Due to the randomness, we need to be generous with the error tolerance
					double eps = stdDev / 100.0;
					Mat mat = new Mat(511, 513, type);
					if (stdDev < 0 || !Double.isFinite(stdDev) || !Double.isFinite(mean)) {
						assertThrows(IllegalArgumentException.class, () -> OpenCVTools.addNoise(mat, mean, stdDev));
					} else {
						OpenCVTools.addNoise(mat, mean, stdDev);
						assertEquals(mean, OpenCVTools.mean(mat), eps);
						assertEquals(stdDev, OpenCVTools.stdDev(mat), eps);
						
						var channelMeans = OpenCVTools.channelMean(mat);
						assertEquals(mat.channels(), channelMeans.length);
						
						for (var cm : channelMeans)
							assertEquals(mean, cm, eps);
						
						var channelStdDevs = OpenCVTools.channelStdDev(mat);
						assertEquals(mat.channels(), channelStdDevs.length);
						for (var csd : channelStdDevs) {
							assertEquals(stdDev, csd, eps);
						}
					}
					mat.close();
				}
			}
		}
	}
	
	
	@Test
	public void testContinuous() {
		try (var scope = new PointerScope()) {
			var mat = Mat.eye(9, 7, opencv_core.CV_32F).asMat();
			var mat2 = mat.col(5);
			assertTrue(mat.isContinuous());
			assertFalse(mat2.isContinuous());

			// While we have a non-continuous array, best check that buffers & indexers still behave as expected
			FloatIndexer idx = mat2.createIndexer();
			assertEquals(idx.size(0), 9);
			assertEquals(idx.size(1), 1);
			float[] pixels = OpenCVTools.extractPixels(mat2, (float[])null);
			
			// Warning! Buffer does *not* work here, since it refers to the whole Mat, 
			// with a limit of 63. This is one reason why it's important to use buffers 
			// only with continuous Mats...
//			FloatBuffer buffer = mat2.createBuffer();
			for (int i = 0; i < 9; i++) {
				float val = i == 5 ? 1f : 0f;
				assertEquals(idx.get(i, 0), val);
				assertEquals(pixels[i], val);
//				assertEquals(buffer.get(i), val);
			}
			
			var mat3 = OpenCVTools.ensureContinuous(mat2, false);
			assertTrue(mat3.isContinuous());
			assertFalse(mat2.isContinuous());
			
			OpenCVTools.ensureContinuous(mat2, true);
			assertTrue(mat2.isContinuous());
			assertEquals(mat2.rows(), 9);
			assertEquals(mat2.cols(), 1);
			
			// Extracting a row maintains continuous data
			var mat4 = mat.row(5);
			assertTrue(mat4.isContinuous());
			OpenCVTools.ensureContinuous(mat4, true);
			assertTrue(mat4.isContinuous());
		}
	}
	
	
	
	
	@ParameterizedTest
	@MethodSource("qupath.lib.analysis.algorithms.TestContourTracing#providePathsForTraceContours")
	void testTraceContours(Path path) throws Exception {
		long startTime = System.currentTimeMillis();
		
		var img = ImageIO.read(path.toUri().toURL());
		var mat = OpenCVTools.imageToMat(img);
						
		var labels4 = OpenCVTools.label(mat, 4);
		var objects4 = OpenCVTools.createDetections(labels4, null, 1, -1);
		assertEquals(objects4.size(), OpenCVTools.maximum(labels4));
		assertFalse(objects4.stream().anyMatch(p -> p.getROI().isEmpty()));
		
		long middleTime = System.currentTimeMillis();
		logger.debug("Traced {} contours for {} in {} ms with 4-connectivity", objects4.size(), path.getFileName().toString(), middleTime - startTime);
		
		var labels8 = OpenCVTools.label(mat, 8);
		var objects8 = OpenCVTools.createAnnotations(labels8, null, 1, -1);
		assertEquals(objects8.size(), OpenCVTools.maximum(labels8));
		assertFalse(objects8.stream().anyMatch(p -> p.getROI().isEmpty()));
		
		long endTime = System.currentTimeMillis();
		logger.debug("Traced {} contours for {} in {} ms with 8-connectivity", objects8.size(), path.getFileName().toString(), endTime - middleTime);

	}
	

}