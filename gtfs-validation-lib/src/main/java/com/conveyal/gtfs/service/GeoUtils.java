package com.conveyal.gtfs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.geotools.geometry.GeometryBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.onebusaway.gtfs.model.ShapePoint;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchIdentifierException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.conveyal.gtfs.model.ProjectedCoordinate;

 public class GeoUtils {
	public static double RADIANS = 2 * Math.PI;

	public static MathTransform recentMathTransform = null;
	public static GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(),4326);
	public static GeometryFactory projectedGeometryFactory = new GeometryFactory(new PrecisionModel());
	public static GeometryBuilder builder = new GeometryBuilder(DefaultGeographicCRS.WGS84);

	/**
	 * Converts from a coordinate to The appropriate UTM zone. 
	 * 
	 * @param latlon THE ORDER OF THE COORDINATE MUST BE LAT, LON!
	 * @return
	 */
	public static ProjectedCoordinate convertLatLonToEuclidean(
			Coordinate latlon) {

		Coordinate lonlat = new Coordinate(latlon.y, latlon.x);

		return convertLonLatToEuclidean(lonlat);
	}

	private static ProjectedCoordinate convertLonLatToEuclidean(
			Coordinate lonlat) {

		final MathTransform transform = getTransform(lonlat);
		final Coordinate to = new Coordinate();

		// the transform seems to swap the lat lon pairs
		Coordinate latlon = new Coordinate(lonlat.y, lonlat.x);

		try {
			JTS.transform(latlon, to, transform);
		} catch (final TransformException e) {
			e.printStackTrace();
		}

		return new ProjectedCoordinate(transform, new Coordinate(to.y, to.x), lonlat);
	}


	public static Coordinate convertToLatLon(
			MathTransform transform, Coordinate xy) {

		Coordinate lonlat = convertToLonLat(transform, xy);
		return new Coordinate(lonlat.y, lonlat.x);
	}

	public static Coordinate convertToLonLat(
			MathTransform transform, Coordinate xy) {
		final Coordinate to = new Coordinate();
		final Coordinate yx = new Coordinate(xy.y, xy.x);
		try {
			JTS.transform(yx, to, transform.inverse());
		} catch (final TransformException e) {
			e.printStackTrace();
		}
		return new Coordinate(to.y, to.x);
	}

	public static Coordinate convertToLatLon(ProjectedCoordinate pc) {

		final Coordinate point = new Coordinate(pc.getX(), pc.getY());
		return convertToLatLon(pc.getTransform(), point);
	}



	public static Coordinate convertToLonLat(ProjectedCoordinate pc) {

		final Coordinate point = new Coordinate(pc.getX(), pc.getY());
		return convertToLonLat(pc.getTransform(), point);
	}

	public static Geometry getGeometryFromCoordinate(double lat, double lon) throws IllegalArgumentException{
		Coordinate stopCoord = new Coordinate(lat, lon);
		ProjectedCoordinate projectedStopCoord = null;
		projectedStopCoord = GeoUtils.convertLatLonToEuclidean(stopCoord);
		return geometryFactory.createPoint(projectedStopCoord);
	}

	public static Geometry getGeomFromShapePoints(List<ShapePoint> shapePoints) throws IllegalArgumentException{
		ArrayList<Coordinate> shapeCoords = new ArrayList<Coordinate>();
		// needs to be mutable to sort. TreeSet impl is less verbose than initializing another List. 
		TreeSet<ShapePoint> linkedShapePoints = new TreeSet<ShapePoint>();	
		try {
			linkedShapePoints.addAll(shapePoints);
		} catch (Exception e) {
			e.printStackTrace();
		}

		for(ShapePoint shapePoint : linkedShapePoints) {
			Coordinate coord = new Coordinate(shapePoint.getLat(), shapePoint.getLon());

			ProjectedCoordinate projectedCoord = GeoUtils.convertLatLonToEuclidean(coord);
			if ( projectedCoord.getX() == Coordinate.NULL_ORDINATE || 
					projectedCoord.getY() == Coordinate.NULL_ORDINATE){
				throw new IllegalArgumentException("Something is wrong with " + shapePoint.getId() + 
						" on shape " + shapePoint.getShapeId());
			}
			shapeCoords.add(projectedCoord);
		}

		Geometry geom = geometryFactory.createLineString(
				shapeCoords.toArray(new Coordinate[shapePoints.size()]));

		return geom;
	}

	/**
	 * From
	 * http://gis.stackexchange.com/questions/28986/geotoolkit-conversion-from
	 * -lat-long-to-utm
	 */
	public static int getEPSGCodefromUTS(Coordinate refLonLat) {
		// define base EPSG code value of all UTM zones;
		int epsg_code = 32600;
		// add 100 for all zones in southern hemisphere
		if (refLonLat.y < 0) {
			epsg_code += 100;
		}
		// finally, add zone number to code
		epsg_code += getUTMZoneForLongitude(refLonLat.x);

		return epsg_code;
	}


	public static double getMetersInAngleDegrees(
			double distance) {
		return distance / (Math.PI / 180d) / 6378137d;
	}

	public static MathTransform getTransform(
			Coordinate refLatLon) {
		try {
			final CRSAuthorityFactory crsAuthorityFactory =
					CRS.getAuthorityFactory(false);


			final GeographicCRS geoCRS =
					crsAuthorityFactory.createGeographicCRS("EPSG:4326");

			final CoordinateReferenceSystem dataCRS = 
					crsAuthorityFactory
					.createCoordinateReferenceSystem("EPSG:" 
							+ getEPSGCodefromUTS(refLatLon)); //EPSG:32618

			final MathTransform transform =
					CRS.findMathTransform(geoCRS, dataCRS);

			GeoUtils.recentMathTransform = transform;

			return transform;
		} catch (final NoSuchIdentifierException e) {
			e.printStackTrace();
		} catch (final FactoryException e) {
			e.printStackTrace();
		}

		return null;
	}

	/*
	 * Taken from OneBusAway's UTMLibrary class
	 */
	public static int getUTMZoneForLongitude(double lon) {

		if (lon < -180 || lon > 180)
			throw new IllegalArgumentException(
					"Coordinates not within UTM zone limits");

		int lonZone = (int) ((lon + 180) / 6);

		if (lonZone == 60)
			lonZone--;
		return lonZone + 1;
	}




}