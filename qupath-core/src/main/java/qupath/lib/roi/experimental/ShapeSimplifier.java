/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.roi.experimental;

import java.util.List;
import java.util.PriorityQueue;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ROIs;

/**
 * Helper methods for simplifying shapes, i.e. removing polygon points while retaining the same overall 
 * shape at a coarser level.
 * 
 * This can help manage storage and performance requirements when working with large numbers of ROIs,
 * especially in terms of repainting speed.
 * 
 * @author Pete Bankhead
 *
 */
public class ShapeSimplifier {
	
	/**
	 * 
	 * Create a simplified polygon (fewer coordinates) using method based on Visvalingam’s Algorithm.
	 * The input is a list of points (the vertices) belonging to a closed polygon.
	 * This list is modified in place.
	 * 
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 * 
	 * @param points
	 * @param altitudeThreshold
	 */
	public static void simplifyPolygonPoints(final List<Point2> points, final double altitudeThreshold) {
		
		if (points.size() <= 3)
			return;
		
		int n = points.size();
		
		// Populate the priority queue
		PriorityQueue<PointWithArea> queue = new PriorityQueue<>();
		
		Point2 pPrevious = points.get(points.size()-1);
		Point2 pCurrent = points.get(0);
		PointWithArea pwaPrevious = null;
		PointWithArea pwaFirst = null;
		for (int i = 0; i < n; i++) {
			Point2 pNext = points.get((i+1) % n);
			PointWithArea pwa = new PointWithArea(pCurrent, calculateArea(pPrevious, pCurrent, pNext));
			pwa.setPrevious(pwaPrevious);
			if (pwaPrevious != null)
				pwaPrevious.setNext(pwa);
			queue.add(pwa);
			pwaPrevious = pwa;
			
			pPrevious = pCurrent;
			pCurrent = pNext;
			// Handle first and last cases for closed polygon
			if (i == n - 1) {
				pwa.setNext(pwaFirst);
				pwaFirst.setPrevious(pwa);
			} else if (i == 0)
				pwaFirst = pwa;
		}
		
		
		double maxArea = 0;
		int minSize = Math.max(n / 100, 3);
		while (queue.size() > minSize) {
			PointWithArea pwa = queue.poll();
//			logger.info("BEFORE: " + pwa + " (counter " + counter + ")");

			// Altitude check (?)
			double altitude = pwa.getArea() * 2 / pwa.getNext().getPoint().distance(pwa.getPrevious().getPoint());
			if (altitude > altitudeThreshold)
				break;

			if (pwa.getArea() < maxArea)
				pwa.setArea(maxArea);
			else
				maxArea = pwa.getArea();
			
			// Remove the point & update accordingly
			points.remove(pwa.getPoint());
			
			pwaPrevious = pwa.getPrevious();
			PointWithArea pwaNext = pwa.getNext();
			pwaPrevious.setNext(pwaNext);
			pwaPrevious.updateArea();
			pwaNext.setPrevious(pwaPrevious);
			pwaNext.updateArea();
			
			// Reinsert into priority queue
			queue.remove(pwaPrevious);
			queue.remove(pwaNext);
			queue.add(pwaPrevious);
			queue.add(pwaNext);
			
//			logger.info(pwa);
		}
	}
	
	
	
	
	/**
	 * 
	 * Create a simplified polygon (fewer coordinates) using method based on Visvalingam’s Algorithm.
	 * 
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 * 
	 * @param polygon
	 * @param altitudeThreshold
	 * @return
	 */
	public static PolygonROI simplifyPolygon(PolygonROI polygon, final double altitudeThreshold) {
		List<Point2> points = polygon.getPolygonPoints();
		simplifyPolygonPoints(points, altitudeThreshold);
		// Construct a new polygon
		return ROIs.createPolyonROI(points, ImagePlane.getPlaneWithChannel(polygon));
	}
	
	
	
	/**
	 * Calculate the area of a triangle from its vertices.
	 * 
	 * @param p1
	 * @param p2
	 * @param p3
	 * @return
	 */
	static double calculateArea(Point2 p1, Point2 p2, Point2 p3) {
		return Math.abs(0.5 * (p1.getX() * (p2.getY() - p3.getY()) + 
						p2.getX() * (p3.getY() - p1.getY()) + 
						p3.getX() * (p1.getY() - p2.getY())));
	}
	
	
	static class PointWithArea implements Comparable<PointWithArea> {
		
		private PointWithArea pPrevious;
		private PointWithArea pNext;
		private Point2 p;
		private double area;
		
		PointWithArea(Point2 p, double area) {
			this.p = p;
			this.area = area;
		}
		
		public void setArea(double area) {
			this.area = area;
		}
		
		public void updateArea() {
			this.area = calculateArea(pPrevious.getPoint(), p, pNext.getPoint());
		}
		
		public double getX() {
			return p.getX();
		}

		public double getY() {
			return p.getY();
		}
		
		public double getArea() {
			return area;
		}
		
		public Point2 getPoint() {
			return p;
		}
		
		public void setPrevious(PointWithArea pPrevious) {
			this.pPrevious = pPrevious;
		}

		public void setNext(PointWithArea pNext) {
			this.pNext = pNext;
		}
		
		public PointWithArea getPrevious() {
			return pPrevious;
		}

		public PointWithArea getNext() {
			return pNext;
		}

		@Override
		public int compareTo(PointWithArea p) {
			return (area < p.area) ? -1 : (area == p.area ? 0 : 1);
		}
		
		
		@Override
		public String toString() {
			return getX() + ", " + getY() + ", Area: " + getArea();
		}
		

	}
	

}
