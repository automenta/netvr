package netvr;

import gov.nasa.worldwind.*;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.Message;
import gov.nasa.worldwind.formats.shapefile.Shapefile;
import gov.nasa.worldwind.formats.shapefile.ShapefileRecord;
import gov.nasa.worldwind.formats.shapefile.ShapefileRecordPoint;
import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.layers.Earth.OSMMapnikLayer;
import gov.nasa.worldwind.render.*;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.WWIO;
import gov.nasa.worldwind.util.WWUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class WorldWindOSM {

    public WorldWindOSM() {
    }

    static class OSMModel extends WWObjectImpl implements Model {
        private Globe globe;
        private LayerList layers;
        private boolean showWireframeInterior = false;
        private boolean showWireframeExterior = false;
        private boolean showTessellationBoundingVolumes = false;

        public OSMModel() {
            this.setGlobe((Globe)
                WorldWind.createComponent(
                    Configuration.getStringValue(AVKey.GLOBE_CLASS_NAME)));

            LayerList layers = new LayerList();
            layers.add(new StarsLayer());
            layers.add(new SkyGradientLayer());
            //layers.add(new BMNGOneImage());
            layers.add(new OSMMapnikLayer());
            layers.add(OpenStreetMapShapefileLoader.makeLayerFromOSMPlacesSource(
                    new File("/tmp/shp/waterways.shp")));
            this.setLayers(layers);
        }

        public Globe getGlobe() {
            return this.globe;
        }

        public void setGlobe(Globe globe) {
            // don't raise an exception if globe == null. In that case, we are disassociating the model from any globe

            //remove property change listener "this" from the current globe.
            if (this.globe != null)
                this.globe.removePropertyChangeListener(this);

            // if the new globe is not null, add "this" as a property change listener.
            if (globe != null)
                globe.addPropertyChangeListener(this);

            Globe old = this.globe;
            this.globe = globe;
            this.firePropertyChange(AVKey.GLOBE, old, this.globe);
        }

        /**
         * {@inheritDoc}
         */
        public LayerList getLayers() {
            return this.layers;
        }

        /**
         * {@inheritDoc}
         */
        public void setLayers(LayerList layers) {
            // don't raise an exception if layers == null. In that case, we are disassociating the model from any layer set

            if (this.layers != null)
                this.layers.removePropertyChangeListener(this);
            if (layers != null)
                layers.addPropertyChangeListener(this);

            LayerList old = this.layers;
            this.layers = layers;
            this.firePropertyChange(AVKey.LAYERS, old, this.layers);
        }

        /**
         * {@inheritDoc}
         */
        public boolean isShowWireframeInterior() {
            return this.showWireframeInterior;
        }

        /**
         * {@inheritDoc}
         */
        public void setShowWireframeInterior(boolean show) {
            this.showWireframeInterior = show;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isShowWireframeExterior() {
            return this.showWireframeExterior;
        }

        /**
         * {@inheritDoc}
         */
        public void setShowWireframeExterior(boolean show) {
            this.showWireframeExterior = show;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isShowTessellationBoundingVolumes() {
            return showTessellationBoundingVolumes;
        }

        /**
         * {@inheritDoc}
         */
        public void setShowTessellationBoundingVolumes(boolean showTessellationBoundingVolumes) {
            this.showTessellationBoundingVolumes = showTessellationBoundingVolumes;
        }

        
        public Extent getExtent() {
            // See if the layers have it.
            LayerList layers = OSMModel.this.getLayers();
            if (layers != null) {
                for (Object layer1 : layers) {
                    Layer layer = (Layer) layer1;
                    Extent e = (Extent) layer.getValue(AVKey.EXTENT);
                    if (e != null)
                        return e;
                }
            }

            // See if the Globe has it.
            Globe globe = this.getGlobe();
            if (globe != null) {
                return globe.getExtent();
            }

            return null;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation forwards the message each layer in the model.
         *
         * @param msg The message that was received.
         */
        @Override
        public void onMessage(Message msg) {
            if (this.getLayers() != null) {
                for (Layer layer : this.getLayers()) {
                    try {
                        if (layer != null) {
                            layer.onMessage(msg);
                        }
                    }
                    catch (Exception e) {
                        String message = Logging.getMessage("generic.ExceptionInvokingMessageListener");
                        Logging.logger().log(Level.SEVERE, message, e);
                        // Don't abort; continue on to the next layer.
                    }
                }
            }
        }
    }
    
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            JFrame f = new JFrame();

            WorldWindowGLCanvas wwd = new WorldWindowGLCanvas();
            wwd.setPreferredSize(new Dimension(1000, 800));
            f.getContentPane().add(wwd, BorderLayout.CENTER);

            wwd.setModel(new OSMModel());

            f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);

        });
    }
    
    enum OpenStreetMapShapefileLoader { ;

        public static boolean isOSMPlacesSource(Object source) {
            String message;
            if (source != null && !WWUtil.isEmpty(source)) {
                message = WWIO.getSourcePath(source);
                return message != null && WWIO.getFilename(message).equalsIgnoreCase("places.shp");
            } else {
                message = Logging.getMessage("nullValue.SourceIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }
        }

        public static Layer makeLayerFromOSMPlacesSource(Object source) {
            if (source != null && !WWUtil.isEmpty(source)) {
                Shapefile shp = null;
                Layer layer = null;

                try {
                    shp = new Shapefile(source);
                    layer = makeLayerFromOSMPlacesShapefile(shp);
                } finally {
                    if (shp != null) {
                        shp.close();
                    }

                }

                return layer;
            } else {
                String message = Logging.getMessage("nullValue.SourceIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }
        }

        public static Layer makeLayerFromOSMPlacesShapefile(Shapefile shp) {
            if (shp == null) {
                String message = Logging.getMessage("nullValue.ShapefileIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            } else {
                OpenStreetMapShapefileLoader.OSMShapes[] shapeArray = new OpenStreetMapShapefileLoader.OSMShapes[]{
                        new OpenStreetMapShapefileLoader.OSMShapes(Color.BLACK, 0.5D, 30000.0D), new OpenStreetMapShapefileLoader.OSMShapes(Color.GREEN, 0.5D, 100000.0D), new OpenStreetMapShapefileLoader.OSMShapes(Color.CYAN, 1.0D, 500000.0D), new OpenStreetMapShapefileLoader.OSMShapes(Color.YELLOW, 2.0D, 3000000.0D)};

                OpenStreetMapShapefileLoader.TextAndShapesLayer layer = new OpenStreetMapShapefileLoader.TextAndShapesLayer();

                while(true) {
                    ShapefileRecord record;
                    OpenStreetMapShapefileLoader.OSMShapes shapes;
                    OpenStreetMapShapefileLoader.Label label;
                    do {
                        Object o;
                        do {
                            do {
                                do {
                                    if (!shp.hasNext()) {

                                        for(int var14 = 0; var14 < shapeArray.length; ++var14) {
                                            OpenStreetMapShapefileLoader.OSMShapes ss = shapeArray[var14];



                                            Renderable l =
                                                    surfaceIcons(ss, shp.getPointBuffer().getLocations());

                                            layer.add(l);

                                            for (Label v : ss.labels) {
                                                layer.addLabel(v);
                                            }

//                                            ss.locations.clear();
//                                            ss.labels.clear();
                                        }

                                        return layer;
                                    }

                                    record = shp.nextRecord();
                                    if (record != null) {
                                        if (record.isPolygonRecord()) {

                                            int parts = record.getNumberOfParts();
                                            for (int part = 0; part < parts; part++)
                                                layer.add(new SurfacePolygon(record.asPolygonRecord().getPointBuffer(part).getLocations()));

                                        } else if (record.isPolylineRecord()) {
                                            int parts = record.getNumberOfParts();
                                            for (int part = 0; part < parts; part++) {
                                                final SurfacePolyline line = new SurfacePolyline(record.asPolylineRecord().getPointBuffer(part).getLocations());
                                                line.setValue("lineWidth", 8.0);
                                                layer.add(line);
                                            }

                                        }
                                    }
                                } while(record == null);
                            } while(!record.getShapeType().equals("gov.nasa.worldwind.formats.shapefile.Shapefile.ShapePoint"));

                            o = record.getAttributes().getValue("type");
                        } while(!(o instanceof String));

                        shapes = null;
                        String type = (String)o;
                        if (type.equalsIgnoreCase("hamlet")) {
                            shapes = shapeArray[0];
                        } else if (type.equalsIgnoreCase("village")) {
                            shapes = shapeArray[1];
                        } else if (type.equalsIgnoreCase("town")) {
                            shapes = shapeArray[2];
                        } else if (type.equalsIgnoreCase("city")) {
                            shapes = shapeArray[3];
                        }
                    } while(shapes == null);

                    String name = null;
                    AVList attr = record.getAttributes();
                    if (attr.getEntries() != null) {
                        for (Map.Entry<String, Object> e : attr.getEntries()) {
                            if (e.getKey().equalsIgnoreCase("name")) {
                                name = (String) e.getValue();
                                break;
                            }
                        }
                    }

                    double[] pointCoords = ((ShapefileRecordPoint)record).getPoint();
                    LatLon location = LatLon.fromDegrees(pointCoords[1], pointCoords[0]);
                    if (!WWUtil.isEmpty(name)) {
                        label = new OpenStreetMapShapefileLoader.Label(name, new Position(location, 0.0D));
                        label.setFont(shapes.font);
                        label.setColor(shapes.foreground);
                        label.setBackgroundColor(shapes.background);
                        label.setMaxActiveAltitude(shapes.labelMaxAltitude);
                        label.setPriority(shapes.labelMaxAltitude);
                        shapes.labels.add(label);
                    }

                    shapes.locations.add(location);
                }
            }
        }

        private static SurfaceIcons surfaceIcons(OSMShapes ss, Iterable<? extends LatLon> locations) {
            SurfaceIcons l = new SurfaceIcons(
                    PatternFactory.createPattern("PatternFactory.PatternCircle",
                    0.8F, ss.foreground), locations);
            l.setMaxSize(100.0D * ss.scale);
            l.setMinSize(10.0D);
            l.setScale(ss.scale);
            l.setOpacity(0.8D);
            return l;
        }

        protected static class OSMShapes {
            public final java.util.List<LatLon> locations = new ArrayList();
            public final java.util.List<OpenStreetMapShapefileLoader.Label> labels = new ArrayList();
            public final Color foreground;
            public final Color background;
            public final Font font;
            public final double scale;
            public final double labelMaxAltitude;

            public OSMShapes(Color color, double scale, double labelMaxAltitude) {
                this.foreground = color;
                this.background = WWUtil.computeContrastingColor(color);
                this.font = new Font("Arial", 1, 10 + (int)(3.0D * scale));
                this.scale = scale;
                this.labelMaxAltitude = labelMaxAltitude;
            }
        }

        protected static class Label extends UserFacingText {
            protected double minActiveAltitude = -1.7976931348623157E308D;
            protected double maxActiveAltitude = 1.7976931348623157E308D;

            public Label(CharSequence text, Position position) {
                super(text, position);
            }

            public void setMinActiveAltitude(double altitude) {
                this.minActiveAltitude = altitude;
            }

            public void setMaxActiveAltitude(double altitude) {
                this.maxActiveAltitude = altitude;
            }

            public boolean isActive(DrawContext dc) {
                double eyeElevation = dc.getView().getEyePosition().getElevation();
                return this.minActiveAltitude <= eyeElevation && eyeElevation <= this.maxActiveAltitude;
            }
        }

        protected static class TextAndShapesLayer extends RenderableLayer {
            protected final List<GeographicText> labels = new ArrayList();
            protected final GeographicTextRenderer textRenderer = new GeographicTextRenderer();

            public TextAndShapesLayer() {
                this.textRenderer.setCullTextEnabled(true);
                this.textRenderer.setCullTextMargin(2);
                this.textRenderer.setDistanceMaxScale(2.0D);
                this.textRenderer.setDistanceMinScale(0.5D);
                this.textRenderer.setDistanceMinOpacity(0.5D);
                this.textRenderer.setEffect("gov.nasa.worldwind.avkey.TextEffectOutline");
            }

            public void addLabel(GeographicText label) {
                this.labels.add(label);
            }

            public void doRender(DrawContext dc) {
                super.doRender(dc);
                this.setActiveLabels(dc);
                this.textRenderer.render(dc, this.labels);
            }

            protected void setActiveLabels(DrawContext dc) {

                for (GeographicText text : this.labels) {
                    if (text instanceof Label) {
                        text.setVisible(((Label) text).isActive(dc));
                    }
                }

            }
        }
    }

}
