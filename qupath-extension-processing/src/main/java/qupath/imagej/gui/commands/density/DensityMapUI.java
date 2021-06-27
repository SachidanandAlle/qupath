/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.imagej.gui.commands.density;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Side;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.gui.commands.ui.SaveResourcePaneBuilder;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.images.stores.ColorModelRenderer;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectPredicates;
import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;
import qupath.opencv.ml.pixel.PixelClassifierTools.CreateObjectOptions;

/**
 * UI elements associated with density maps.
 * 
 * @author Pete Bankhead
 */
public class DensityMapUI {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMapUI.class);
	
	private final static String title = "Density maps";
	
	/**
	 * Create a pane that can be used to save a {@link DensityMapBuilder}, with standardized display and prompts.
	 * @param project
	 * @param densityMap
	 * @param savedName
	 * @return
	 */
	public static Pane createSaveDensityMapPane(ObjectExpression<Project<BufferedImage>> project, ObjectExpression<DensityMapBuilder> densityMap, StringProperty savedName) {
		logger.trace("Creating 'Save density map' pane");

		var tooltipTextYes = "Save density map in the current project - this is required to use the density map later (e.g. to create objects, measurements)";
		var tooltipTextNo = "Cannot save a density map outside a project. Please create a project to save the classifier.";
		var tooltipText = Bindings
				.when(project.isNull())
				.then(Bindings.createStringBinding(() -> tooltipTextNo, project))
				.otherwise(Bindings.createStringBinding(() -> tooltipTextYes, project));

		return new SaveResourcePaneBuilder<>(DensityMapBuilder.class, densityMap)
				.project(project)
				.labelText("Save map")
				.textFieldPrompt("Enter name")
				.savedName(savedName)
				.tooltip(tooltipText)
				.title("Density maps")
				.build();
	}


	/**
	 * Supported input objects.
	 */
	static enum DensityMapObjects {

		DETECTIONS(PathObjectFilter.DETECTIONS_ALL),
		CELLS(PathObjectFilter.CELLS),
		POINT_ANNOTATIONS(
				PathObjectPredicates.filter(PathObjectFilter.ANNOTATIONS)
				.and(PathObjectPredicates.filter(PathObjectFilter.ROI_POINT)));

		private final PathObjectPredicate predicate;

		private DensityMapObjects(PathObjectFilter filter) {
			this(PathObjectPredicates.filter(filter));
		}

		private DensityMapObjects(PathObjectPredicate predicate) {
			this.predicate = predicate;
		}

		/**
		 * Get predicate to select objects of the desired type.
		 * @return
		 */
		public PathObjectPredicate getPredicate() {
			return predicate;
		}

		@Override
		public String toString() {
			switch(this) {
			case DETECTIONS:
				return "All detections";
			case CELLS:
				return "All cells";
			case POINT_ANNOTATIONS:
				return "Point annotations";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}

	}

	static class MinMax {

		private float minValue = Float.POSITIVE_INFINITY;
		private float maxValue = Float.NEGATIVE_INFINITY;

		private void update(float val) {
			if (Float.isNaN(val))
				return;
			if (val < minValue)
				minValue = val;
			if (val > maxValue)
				maxValue = val;
		}
		
		public double getMinValue() {
			return minValue;
		}
		
		public double getMaxValue() {
			return maxValue;
		}

	}
	
	
	/**
	 * Get the min and max values for an {@link ImageServer}.
	 * Since this involves requesting all tiles at the highest resolution, it should be used with caution.
	 * @param server
	 * @return
	 * @throws IOException
	 */
	static List<MinMax> getMinMax(ImageServer<BufferedImage> server) throws IOException {
		return MinMaxFinder.getMinMax(server, -1, 0);
	}
	
	

	static class MinMaxFinder {

		private static Map<String, List<MinMax>> cache = Collections.synchronizedMap(new HashMap<>());

		/**
		 * Get the minimum and maximum values for all pixels across all channels of an image.
		 * Note that this will use a cached value, therefore it is assumed that the server cannot change.
		 * 
		 * @param server server containing pixels
		 * @param countBand optional band that can be thresholded and used for masking; if -1, then the same band is used for counts
		 * @param minCount minimum value for pixels to be included
		 * @return
		 * @throws IOException 
		 */
		static List<MinMax> getMinMax(ImageServer<BufferedImage> server, int countBand, float minCount) throws IOException {
			String key = server.getPath() + "?count=" + countBand + "&minCount=" + minCount;
			var minMax = cache.get(key);
			if (minMax == null) {
				logger.trace("Calculating min & max for {}", server);
				minMax = calculateMinMax(server, countBand, minCount);
				cache.put(key, minMax);
			} else
				logger.trace("Using cached min & max for {}", server);
			return minMax;
		}

		private static List<MinMax> calculateMinMax(ImageServer<BufferedImage> server, int countBand, float minCount) throws IOException {
			var tiles = getAllTiles(server, 0, false);
			if (tiles == null)
				return null;
			// Sometimes we use the
			boolean countsFromSameBand = countBand < 0;
			int nBands = server.nChannels();
			List<MinMax> results = IntStream.range(0, nBands).mapToObj(i -> new MinMax()).collect(Collectors.toList());
			float[] pixels = null;
			float[] countPixels = null;
			for (var img : tiles.values()) {
				var raster = img.getRaster();
				int w = raster.getWidth();
				int h = raster.getHeight();
				if (pixels == null || pixels.length < w*h) {
					pixels = new float[w*h];
					if (!countsFromSameBand)
						countPixels = new float[w*h];
				}
				countPixels = !countsFromSameBand ? raster.getSamples(0, 0, w, h, countBand, countPixels) : null;
				for (int band = 0; band < nBands; band++) {
					var minMax = results.get(band);
					pixels = raster.getSamples(0, 0, w, h, band, pixels);
					if (countsFromSameBand) {
						for (int i = 0; i < w*h; i++) {
							if (pixels[i] > minCount)
								minMax.update(pixels[i]);
						}					
					} else {
						for (int i = 0; i < w*h; i++) {
							if (countPixels[i] > minCount)
								minMax.update(pixels[i]);
						}
					}
				}
			}
			return Collections.unmodifiableList(results);
		}

		private static Map<RegionRequest, BufferedImage> getAllTiles(ImageServer<BufferedImage> server, int level, boolean ignoreInterrupts) throws IOException {
			Map<RegionRequest, BufferedImage> map = new LinkedHashMap<>();
			var tiles = server.getTileRequestManager().getTileRequestsForLevel(level);
			for (var tile : tiles) {
				if (!ignoreInterrupts && Thread.currentThread().isInterrupted())
					return null;
				var region = tile.getRegionRequest();
				if (server.isEmptyRegion(region))
					continue;
				var imgTile = server.readBufferedImage(region);
				if (imgTile != null)
					map.put(region, imgTile);
			}
			return map;
		}

	}

	/**
	 * Ignore classification (accept all objects).
	 * Generated with a UUID for uniqueness, and because it should not be serialized.
	 */
	public static final PathClass ANY_CLASS = PathClassFactory.getPathClass(UUID.randomUUID().toString());

	/**
	 * Accept any positive classification, including 1+, 2+, 3+.
	 * Generated with a UUID for uniqueness, and because it should not be serialized.
	 */
	public static final PathClass ANY_POSITIVE_CLASS = PathClassFactory.getPathClass(UUID.randomUUID().toString());


	static interface DensityMapButtonCommand {
		
		public void accept(ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName);
		
	}


	static class HotspotFinder implements DensityMapButtonCommand {

		private ParameterList paramsHotspots = new ParameterList()
				.addIntParameter("nHotspots", "Number of hotspots to find", 1, null, "Specify the number of hotspots to identify; hotspots are peaks in the density map")
				.addDoubleParameter("minDensity", "Min object count", 1, null, "Specify the minimum density of objects to accept within a hotspot")
				.addBooleanParameter("allowOverlaps", "Allow overlapping hotspots", false, "Allow hotspots to overlap; if false, peaks are discarded if the hotspot radius overlaps with a 'hotter' hotspot")
				.addBooleanParameter("deletePrevious", "Delete existing hotspots", true, "Delete existing hotspot annotations with the same classification")
				;

		@Override
		public void accept(ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName) {

			if (imageData == null || builder == null) {
				Dialogs.showErrorMessage(title, "No density map found!");
				return;
			}

			if (!Dialogs.showParameterDialog(title, paramsHotspots))
				return;

			double radius = builder.buildParameters().getRadius();

			int n = paramsHotspots.getIntParameterValue("nHotspots");
			double minDensity = paramsHotspots.getDoubleParameterValue("minDensity");
			boolean allowOverlapping = paramsHotspots.getBooleanParameterValue("allowOverlaps");
			boolean deleteExisting = paramsHotspots.getBooleanParameterValue("deletePrevious");

			int channel = 0; // TODO: Allow changing channel (if multiple channels available)

			var hierarchy = imageData.getHierarchy();
			var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
			if (selected.isEmpty())
				selected.add(imageData.getHierarchy().getRootObject());

			try {
				var server = builder.buildServer(imageData);

				// Remove existing hotspots with the same classification
				PathClass hotspotClass = getHotpotClass(server.getChannel(channel).getName());
				if (deleteExisting) {
					var hotspotClass2 = hotspotClass;
					var existing = hierarchy.getAnnotationObjects().stream()
							.filter(a -> a.getPathClass() == hotspotClass2)
							.collect(Collectors.toList());
					hierarchy.removeObjects(existing, true);
				}

				DensityMaps.findHotspots(hierarchy, server, channel, selected, n, radius, minDensity, allowOverlapping, hotspotClass);
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}


	}



	static Action createDensityMapAction(String text, ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<DensityMapBuilder> builder, ObservableStringValue densityMapName,
			ObservableBooleanValue disableButtons, DensityMapButtonCommand consumer, String tooltip) {
		var action = new Action(text, e -> consumer.accept(imageData.get(), builder.get(), densityMapName.get()));
		if (tooltip != null)
			action.setLongText(tooltip);
		if (disableButtons != null)
			action.disabledProperty().bind(disableButtons);
		return action;
	}



	static class ContourTracer implements DensityMapButtonCommand {

		
		private QuPathGUI qupath;
		
		private DoubleProperty threshold = new SimpleDoubleProperty(Double.NaN);
		private DoubleProperty thresholdCounts = new SimpleDoubleProperty(1);
		
		private BooleanProperty deleteExisting = new SimpleBooleanProperty(false);
		private BooleanProperty split = new SimpleBooleanProperty(false);
		private BooleanProperty select = new SimpleBooleanProperty(false);
		
		private int bandThreshold = 0;
		private int bandCounts = -1;
		
		ContourTracer(QuPathGUI qupath) {
			this.qupath = qupath;
		}
		
		boolean showDialog(ImageServer<BufferedImage> densityServer, ColorModelRenderer renderer) throws IOException {
					
			bandThreshold = 0;
			bandCounts = -1;
					
			var channel = densityServer.getChannel(bandThreshold);
			var aboveThreshold = PathClassFactory.getPathClass(channel.getName(), channel.getColor());
			
			var minMaxAll = getMinMax(densityServer);
			
			if (renderer != null) {
				threshold.addListener((v, o, n) -> updateRenderer(renderer, aboveThreshold));
				thresholdCounts.addListener((v, o, n) -> updateRenderer(renderer, aboveThreshold));
			}
			
			var minMax = minMaxAll.get(bandThreshold);
			var slider = new Slider(0, (int)Math.ceil(minMax.getMaxValue()), (int)(minMax.getMaxValue()/2.0));
			slider.setMinorTickCount((int)(slider.getMax() + 1));
			var tfThreshold = new TextField();
			tfThreshold.setPrefColumnCount(6);
			GuiTools.bindSliderAndTextField(slider, tfThreshold, false, 2);
			double t = threshold.get();
			if (!Double.isFinite(t) || t > slider.getMax() || t < slider.getMin())
				threshold.set(slider.getValue());
			slider.valueProperty().bindBidirectional(threshold);
			
			int row = 0;
			var pane = new GridPane();
			
			var labelThreshold = new Label("Density threshold");
			PaneTools.addGridRow(pane, row++, 0, "Threshold to identify high-density regions.", labelThreshold, slider, tfThreshold);

			boolean includeCounts = densityServer.nChannels() > 1;
			if (includeCounts) {
				bandCounts = densityServer.nChannels()-1;
				var minMaxCounts = minMaxAll.get(bandCounts);
				int max = (int)Math.ceil(minMaxCounts.getMaxValue());
				var sliderCounts = new Slider(0, max, (int)(minMaxCounts.getMaxValue()/2.0));
				sliderCounts.setMinorTickCount(max+1);
				sliderCounts.setSnapToTicks(true);
				
				var tfThresholdCounts = new TextField();
				tfThresholdCounts.setPrefColumnCount(6);
				GuiTools.bindSliderAndTextField(sliderCounts, tfThresholdCounts, false, 1);
				double tc = thresholdCounts.get();
				if (tc > sliderCounts.getMax())
					thresholdCounts.set(1);
				sliderCounts.valueProperty().bindBidirectional(thresholdCounts);				
				
				var labelCounts = new Label("Count threshold");
				PaneTools.addGridRow(pane, row++, 0, "The minimum number of objects required.\n"
						+ "Used in combination with the density threshold to remove outliers (i.e. high density based on just 1 or 2 objects).", labelCounts, sliderCounts, tfThresholdCounts);
			}
			
			var cbDeleteExisting = new CheckBox("Delete existing annotations that share the same classification as the new annotations");
			cbDeleteExisting.selectedProperty().bindBidirectional(deleteExisting);
			PaneTools.addGridRow(pane, row++, 0, null, cbDeleteExisting, cbDeleteExisting, cbDeleteExisting);
			
			var cbSplit = new CheckBox("Split new annotations");
			cbSplit.selectedProperty().bindBidirectional(split);
			PaneTools.addGridRow(pane, row++, 0, null, cbSplit, cbSplit, cbSplit);

			var cbSelect = new CheckBox("Select new annotations");
			cbSelect.selectedProperty().bindBidirectional(select);
			PaneTools.addGridRow(pane, row++, 0, null, cbSelect, cbSelect, cbSelect);
			
			PaneTools.setToExpandGridPaneWidth(slider, cbDeleteExisting, cbSplit, cbSelect);
			
			var titledPane = new TitledPane("Threshold parameters", pane);
			titledPane.setExpanded(true);
			titledPane.setCollapsible(false);
			PaneTools.simplifyTitledPane(titledPane, true);

			
			// Opacity slider
			var paneMain = new BorderPane(titledPane);
			if (renderer != null) {
				var paneOverlay = new GridPane();

				int row2 = 0;

				var cbLayer = new CheckBox("Show overlay");
				cbLayer.selectedProperty().bindBidirectional(qupath.getOverlayOptions().showPixelClassificationProperty());
				PaneTools.addGridRow(paneOverlay, row2++, 0, null, cbLayer, cbLayer);	

				var cbDetections = new CheckBox("Show detections");
				cbDetections.selectedProperty().bindBidirectional(qupath.getOverlayOptions().showDetectionsProperty());
				PaneTools.addGridRow(paneOverlay, row2++, 0, null, cbDetections, cbDetections);	

				var sliderOpacity = new Slider(0.0, 1.0, 0.5);
				sliderOpacity.valueProperty().bindBidirectional(qupath.getOverlayOptions().opacityProperty());
				var labelOpacity = new Label("Opacity");
				PaneTools.addGridRow(paneOverlay, row2++, 0, null, labelOpacity, sliderOpacity);	

				
				PaneTools.setToExpandGridPaneWidth(paneOverlay, sliderOpacity, cbLayer, cbDetections);
				paneOverlay.setHgap(5);
				paneOverlay.setVgap(5);
				
				var titledOverlay = new TitledPane("Overlay", paneOverlay);
				titledOverlay.setExpanded(false);
				PaneTools.simplifyTitledPane(titledOverlay, true);
				
				titledOverlay.heightProperty().addListener((v, o, n) -> titledOverlay.getScene().getWindow().sizeToScene());
				
				paneMain.setBottom(titledOverlay);
//				PaneTools.addGridRow(pane, row++, 0, null, titledOverlay, titledOverlay, titledOverlay);
			}

			pane.setVgap(5);
			pane.setHgap(5);
			
			
			if (Dialogs.builder()
				.modality(Modality.WINDOW_MODAL)
				.content(paneMain)
				.title(title)
				.owner(QPEx.getWindow("Density map"))
//				.expandableContent(expandable)
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.build()
				.showAndWait()
				.orElse(ButtonType.CANCEL) != ButtonType.APPLY)
				return false;
//			if (!Dialogs.showConfirmDialog(title, pane))
//				return false;
			
			return true;
		}
		
		private void updateRenderer(ColorModelRenderer renderer, PathClass aboveThreshold) {
			// Create a translucent overlay showing thresholded regions
			ThresholdColorModels.ColorModelThreshold colorModelThreshold;
			if (bandCounts >= 0) {
				colorModelThreshold = ThresholdColorModels.ColorModelThreshold.create(DataBuffer.TYPE_FLOAT, Map.of(bandThreshold, threshold.get(), bandCounts, thresholdCounts.get()));
			} else {
				colorModelThreshold = ThresholdColorModels.ColorModelThreshold.create(DataBuffer.TYPE_FLOAT, bandThreshold, threshold.get());
			}
			var transparent = ColorToolsAwt.getCachedColor(Integer.valueOf(0), true);
			var above = ColorToolsAwt.getCachedColor(aboveThreshold.getColor());
			var colorModel = new ThresholdColorModels.ThresholdColorModel(colorModelThreshold, transparent, transparent, above);
			
			renderer.setColorModel(colorModel);
			qupath.repaintViewers();
		}
		
		@Override
		public void accept(ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName) {

			if (imageData == null) {
				Dialogs.showErrorMessage(title, "No image available!");
				return;
			}

			if (builder == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			
			var densityServer = builder.buildServer(imageData);

			// TODO: Find a better way to pass the renderer rather than trying to find it later
			PixelClassificationOverlay overlay = null;
			if (qupath != null) {
				var temp = qupath.getViewer().getCustomPixelLayerOverlay();
				if (temp instanceof PixelClassificationOverlay)
					overlay = (PixelClassificationOverlay)temp;
			}
			ColorModelRenderer renderer = null;
			if (overlay != null) {
				var temp = overlay.getRenderer();
				if (temp instanceof ColorModelRenderer)
					renderer = (ColorModelRenderer)temp;
				else if (temp == null) {
					renderer = new ColorModelRenderer(null);
					overlay.setRenderer(renderer);
				}
			}
			ColorModel previousColorModel = renderer == null ? null : renderer.getColorModel();
			

			try {
				if (!showDialog(densityServer, renderer))
					return;
//				if (!Dialogs.showParameterDialog("Trace contours from density map", paramsTracing))
//					return;
			} catch (IOException e) {
				logger.error(e.getLocalizedMessage(), e);
				return;
			} finally {
				if (renderer != null) {
					renderer.setColorModel(previousColorModel);
					qupath.repaintViewers();
				}
			}
			
			double countThreshold = this.thresholdCounts.get();
			double threshold = this.threshold.get();
			boolean doDelete = deleteExisting.get();
			boolean doSplit = split.get();
			boolean doSelect = select.get();
			
			List<CreateObjectOptions> options = new ArrayList<>();
			if (doDelete)
				options.add(CreateObjectOptions.DELETE_EXISTING);
			if (doSplit)
				options.add(CreateObjectOptions.SPLIT);
			if (doSelect)
				options.add(CreateObjectOptions.SELECT_NEW);

			Map<Integer, Double> thresholds;
			if (bandCounts > 0)
				thresholds = Map.of(bandThreshold, threshold, bandCounts, countThreshold);
			else
				thresholds = Map.of(bandThreshold, threshold);
			
			var pathClassName = densityServer.getChannel(0).getName();
			
			DensityMaps.threshold(imageData.getHierarchy(), densityServer, thresholds, pathClassName, options.toArray(CreateObjectOptions[]::new));
			
			if (densityMapName != null) {
				String optionsString = "";
				if (!options.isEmpty())
					optionsString = ", " + options.stream().map(o -> "\"" + o.name() + "\"").collect(Collectors.joining(", "));
				
				// Groovy-friendly map
				var thresholdString = "[" + thresholds.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")) + "]";
	
				imageData.getHistoryWorkflow().addStep(
						new DefaultScriptableWorkflowStep("Density map create annotations",
								String.format("createAnnotationsFromDensityMap(\"%s\", %s, \"%s\"%s)",
										densityMapName,
										thresholdString.toString(),
										pathClassName,
										optionsString)
								)
						);
			}
			
		}

	}
	

	
	

	static class DensityMapExporter implements DensityMapButtonCommand {

		@Override
		public void accept(ImageData<BufferedImage> imageData, DensityMapBuilder builder, String densityMapName) {

			if (imageData == null || builder == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}

			var densityMapServer = builder.buildServer(imageData);

			var dialog = new Dialog<ButtonType>();
			dialog.setTitle(title);
			dialog.setHeaderText("How do you want to export the density map?");
			dialog.setContentText("Choose 'Raw values' of 'Send to ImageJ' if you need the original counts, or 'Color overlay' if you want to keep the same visual appearance.");
			var btOrig = new ButtonType("Raw values");
			var btColor = new ButtonType("Color overlay");
			var btImageJ = new ButtonType("Send to ImageJ");
			dialog.getDialogPane().getButtonTypes().setAll(btOrig, btColor, btImageJ, ButtonType.CANCEL);

			var response = dialog.showAndWait().orElse(ButtonType.CANCEL);
			try {
				if (btOrig.equals(response)) {
					promptToSaveRawImage(imageData, densityMapServer, densityMapName);
				} else if (btColor.equals(response)) {
					promptToSaveColorImage(densityMapServer, null); // Counting on color model being set!
				} else if (btImageJ.equals(response)) {
					sendToImageJ(densityMapServer);
				}
			} catch (IOException e) {
				Dialogs.showErrorNotification(title, e);
			}
		}

		private void promptToSaveRawImage(ImageData<BufferedImage> imageData, ImageServer<BufferedImage> densityMap, String densityMapName) throws IOException {
			var file = Dialogs.promptToSaveFile(title, null, densityMapName, "ImageJ tif", ".tif");
			if (file != null) {
				try {
					QP.writeImage(densityMap, file.getAbsolutePath());
					// Log to workflow
					if (densityMapName != null && !densityMapName.isBlank()) {
						var path = file.getAbsolutePath();
						imageData.getHistoryWorkflow().addStep(
								new DefaultScriptableWorkflowStep("Write density map image",
										String.format("writeDensityMapImage(\"%s\", \"%s\")", densityMapName, path)
										)
								);
					}
				} catch (IOException e) {
					Dialogs.showErrorMessage("Save prediction", e);
				}
			}
		}

		private void promptToSaveColorImage(ImageServer<BufferedImage> densityMap, ColorModel colorModel) throws IOException {
			var server = RenderedImageServer.createRenderedServer(densityMap, new ColorModelRenderer(colorModel));
			File file;
			String fmt, ext;
			if (server.nResolutions() == 1 && server.nTimepoints() == 1 && server.nZSlices() == 1) {
				fmt = "PNG";
				ext = ".png";				
			} else {
				fmt = "ImageJ tif";
				ext = ".tif";
			}
			file = Dialogs.promptToSaveFile(title, null, null, fmt, ext);
			if (file != null) {
				QP.writeImage(server, file.getAbsolutePath());
			}
		}

		private void sendToImageJ(ImageServer<BufferedImage> densityMap) throws IOException {
			if (densityMap == null) {
				Dialogs.showErrorMessage(title, "No density map is available!");
				return;
			}
			IJExtension.getImageJInstance();
			var imp = IJTools.extractHyperstack(densityMap, null);
			if (imp instanceof CompositeImage)
				((CompositeImage)imp).resetDisplayRanges();
			imp.show();
		}

	}

	/**
	 * Get a classification to use for hotspots based upon an image channel / classification name.
	 * @param channelName
	 * @return
	 */
	static PathClass getHotpotClass(String channelName) {		
		PathClass baseClass = channelName == null || channelName.isBlank() || DensityMaps.CHANNEL_ALL_OBJECTS.equals(channelName) ? null : PathClassFactory.getPathClass(channelName);
		return DensityMaps.getHotspotClass(baseClass);

	}

	
	/**
	 * Create a pane containing standardized buttons associated with processing a density map (find hotspots, threshold, export map).
	 * 
	 * Note that because density maps need to reflect the current hierarchy, but should be relatively fast to compute (at low resolution), 
	 * the full density map is generated upon request.
	 * 
	 * @param qupath QuPathGUI instance, used to identify viewers
	 * @param imageData expression returning the {@link ImageData} to use
	 * @param builder expression returning the {@link DensityMapBuilder} to use
	 * @param densityMapName name of the density map, if it has been saved (otherwise null). This is used for writing workflow steps.
	 * @return a pane that may be added to a stage
	 */
	public static Pane createButtonPane(QuPathGUI qupath, ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<DensityMapBuilder> builder, StringExpression densityMapName) {
		logger.trace("Creating button pane");
		
		BooleanProperty allowWithoutSaving = new SimpleBooleanProperty(false);
		
		BooleanBinding disableButtons = imageData.isNull()
				.or(builder.isNull())
				.or(densityMapName.isEmpty().and(allowWithoutSaving.not()));
		
		var actionHotspots = createDensityMapAction("Find hotspots", imageData, builder, densityMapName, disableButtons, new HotspotFinder(),
				"Find the hotspots in the density map with highest values");
		var btnHotspots = ActionTools.createButton(actionHotspots, false);

		// TODO: Don't provide QuPath in this way...
		var actionThreshold = createDensityMapAction("Threshold", imageData, builder, densityMapName, disableButtons, new ContourTracer(qupath),
				"Threshold to identify high-density regions");
		var btnThreshold = ActionTools.createButton(actionThreshold, false);

		var actionExport = createDensityMapAction("Export map", imageData, builder, densityMapName, disableButtons, new DensityMapExporter(),
				"Export the density map as an image");
		var btnExport = ActionTools.createButton(actionExport, false);

		var buttonPane = PaneTools.createColumnGrid(btnHotspots, btnThreshold, btnExport);
//		buttonPane.setHgap(hGap);
		PaneTools.setToExpandGridPaneWidth(btnHotspots, btnExport, btnThreshold);
		
		
		// Add some more options
		var menu = new ContextMenu();
		var miWithoutSaving = new CheckMenuItem("Enable buttons for unsaved density maps");
		miWithoutSaving.selectedProperty().bindBidirectional(allowWithoutSaving);
		
		menu.getItems().addAll(
				miWithoutSaving
				);
		
		var btnAdvanced = GuiTools.createMoreButton(menu, Side.RIGHT);
		
		var pane = new BorderPane(buttonPane);
		pane.setRight(btnAdvanced);
		return pane;
	}
	

	// TODO: Generalize these classes for use elsewhere and move to ColorModels or ColorModelFactory
	static class ThresholdColorModels {
	
		static abstract class ColorModelThreshold {
			
			private int transferType;
						
			static ColorModelThreshold create(int transferType, int band, double threshold) {
				return new SingleBandThreshold(transferType, band, threshold);
			}
			
			static ColorModelThreshold create(int transferType, Map<Integer, ? extends Number> thresholds) {
				if (thresholds.size() == 1) {
					var entry = thresholds.entrySet().iterator().next();
					return create(transferType, entry.getKey(), entry.getValue().doubleValue());
				}
				return new MultiBandThreshold(transferType, thresholds);
			}
			
			ColorModelThreshold(int transferType) {
				this.transferType = transferType;
			}
			
			
			protected int getTransferType() {
				return transferType;
			}
			
			protected int getBits() {
				return DataBuffer.getDataTypeSize(transferType);
			}
			
			protected double getValue(Object input, int band) {
					
				if (input instanceof float[])
					return ((float[])input)[band];
				
				if (input instanceof double[])
					return ((double[])input)[band];
				
				if (input instanceof int[])
					return ((int[])input)[band];
	
				if (input instanceof byte[])
					return ((byte[])input)[band] & 0xFF;
	
				if (input instanceof short[]) {
					int val = ((short[])input)[band];
					if (transferType == DataBuffer.TYPE_SHORT)
						return val;
					return val & 0xFFFF;
				}
				
				return Double.NaN;
			}
			
			protected abstract int threshold(Object input);
			
		}
		
		static class SingleBandThreshold extends ColorModelThreshold {
			
			private int band;
			private double threshold;
			
			SingleBandThreshold(int transferType, int band, double threshold) {
				super(transferType);
				this.band = band;
				this.threshold = threshold;
			}
			
			@Override
			protected int threshold(Object input) {
				return Double.compare(getValue(input, band), threshold);
			}			
			
		}
		
		static class MultiBandThreshold  extends ColorModelThreshold {
			
			private int n;
			private int[] bands;
			private double[] thresholds;
			
			MultiBandThreshold(int transferType, Map<Integer, ? extends Number> thresholds) {
				super(transferType);
				this.n = thresholds.size();
				this.bands = new int[n];
				this.thresholds = new double[n];
				int i = 0;
				for (var entry : thresholds.entrySet()) {
					bands[i] = entry.getKey();
					this.thresholds[i] = entry.getValue().doubleValue();
					i++;
				}
			}
			
			@Override
			protected int threshold(Object input) {
				int sum = 0;
				for (int i = 0; i < n; i++) {
					double val = getValue(input, bands[i]);
					int cmp = Double.compare(val, thresholds[i]);
					if (cmp < 0)
						return -1;
					sum += cmp;
				}
				return sum;
			}	
			
		}
		
		
		static class ThresholdColorModel extends ColorModel {
	
			private ColorModelThreshold threshold;
			
			protected Color above;
			protected Color equals;
			protected Color below;
	
			public ThresholdColorModel(ColorModelThreshold threshold, Color below, Color equals, Color above) {
				super(threshold.getBits());
				this.threshold = threshold;
				this.below = below;
				this.equals = equals;
				this.above = above;
			}
	
			@Override
			public int getRed(int pixel) {
				throw new IllegalArgumentException();
			}
	
			@Override
			public int getGreen(int pixel) {
				throw new IllegalArgumentException();
			}
	
			@Override
			public int getBlue(int pixel) {
				throw new IllegalArgumentException();
			}
	
			@Override
			public int getAlpha(int pixel) {
				throw new IllegalArgumentException();
			}
			
			@Override
			public int getRed(Object pixel) {
				return getColor(pixel).getRed();
			}
	
			@Override
			public int getGreen(Object pixel) {
				return getColor(pixel).getGreen();
			}
	
			@Override
			public int getBlue(Object pixel) {
				return getColor(pixel).getBlue();
			}
	
			@Override
			public int getAlpha(Object pixel) {
				return getColor(pixel).getAlpha();
			}
			
			public Color getColor(Object input) {
				int cmp = threshold.threshold(input);
				if (cmp > 0)
					return above;
				else if (cmp < 0)
					return below;
				return equals;
			}
			
			@Override
			public boolean isCompatibleRaster(Raster raster) {
				return raster.getTransferType() == threshold.getTransferType();
			}
			
			
		}
		
		
	}
	

}
